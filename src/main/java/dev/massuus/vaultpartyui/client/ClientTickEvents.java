package dev.massuus.vaultpartyui.client;

import dev.massuus.vaultpartyui.VaultPartyUiMod;
import dev.massuus.vaultpartyui.client.screen.PartyScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
    }
}
