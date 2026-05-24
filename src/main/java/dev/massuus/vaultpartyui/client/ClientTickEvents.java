package dev.massuus.vaultpartyui.client;

import dev.massuus.vaultpartyui.VaultPartyUiMod;
import iskallia.vault.client.data.ClientPartyData;
import iskallia.vault.client.data.ClientPartyInviteState;
import dev.massuus.vaultpartyui.client.screen.PartyScreen;
import iskallia.vault.network.message.ServerboundPartyInviteResponseMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = VaultPartyUiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTickEvents {
    private ClientTickEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        while (ClientKeyMappings.OPEN_PARTY_UI.consumeClick()) {
            if (minecraft.player != null) {
                minecraft.setScreen(new PartyScreen(minecraft.screen));
            }
        }

        if (minecraft.player == null) {
            return;
        }

        if (ClientPartySettings.isAutoAcceptInvitesEnabled()
                && ClientPartyData.getParty(minecraft.player.getUUID()) == null
                && ClientPartyInviteState.hasPendingInvite()) {
            String inviterName = ClientPartyInviteState.getInviterName();
            UUID inviteId = ClientPartyInviteState.getInviteId();
            if (inviteId != null) {
                ServerboundPartyInviteResponseMessage.send(inviteId, true);
                ClientPartyInviteState.clearInvite();
                if (inviterName != null && !inviterName.isEmpty()) {
                    Component msg = new TranslatableComponent("screen.vaultpartyui.auto_accepted", inviterName);
                    minecraft.player.displayClientMessage(msg, false);
                }
            }
        }
    }
}
