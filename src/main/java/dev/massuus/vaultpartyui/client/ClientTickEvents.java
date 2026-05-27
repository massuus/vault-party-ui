package dev.massuus.vaultpartyui.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.massuus.vaultpartyui.VaultPartyUiMod;
import dev.massuus.vaultpartyui.client.screen.PartyScreen;
import iskallia.vault.client.data.ClientPartyData;
import iskallia.vault.client.data.ClientPartyInviteState;
import iskallia.vault.network.message.ServerboundPartyInviteResponseMessage;
import iskallia.vault.world.data.VaultPartyData.Party;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VaultPartyUiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTickEvents {
    private static final Deque<QuickPartyAction> PENDING_QUICK_ACTIONS = new ArrayDeque<>();

    private ClientTickEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        final Minecraft minecraftClient = Objects.requireNonNull(minecraft);
        final LocalPlayer player = minecraftClient.player;
        while (ClientKeyMappings.OPEN_PARTY_UI.consumeClick()) {
            if (player != null) {
                minecraftClient.setScreen(new PartyScreen(minecraftClient.screen));
            }
        }

        while (ClientKeyMappings.CREATE_AND_INVITE_ALL.consumeClick()) {
            triggerQuickPartyAction(minecraftClient, QuickPartyAction.CREATE_AND_INVITE_ALL);
        }

        while (ClientKeyMappings.CREATE_AND_INVITE_FAVORITES.consumeClick()) {
            triggerQuickPartyAction(minecraftClient, QuickPartyAction.CREATE_AND_INVITE_FAVORITES);
        }

        while (ClientKeyMappings.INVITE_NEARBY.consumeClick()) {
            triggerQuickPartyAction(minecraftClient, QuickPartyAction.INVITE_NEARBY);
        }

        if (player == null) {
            return;
        }

        if (ClientPartyInviteState.hasPendingInvite()
                && ClientPartyData.getParty(player.getUUID()) == null
                && ClientPartySettings.shouldAutoAcceptInvite(minecraftClient, ClientPartyInviteState.getInviterName())) {
            String inviterName = ClientPartyInviteState.getInviterName();
            UUID inviteId = ClientPartyInviteState.getInviteId();
            if (inviteId != null) {
                ServerboundPartyInviteResponseMessage.send(inviteId, true);
                ClientPartyInviteState.clearInvite();
                if (inviterName != null && !inviterName.isEmpty()) {
                    Component msg = new TranslatableComponent("screen.vaultpartyui.auto_accepted", inviterName);
                    player.displayClientMessage(msg, false);
                }
            }
        }

        processPendingQuickActions(minecraftClient);
    }

    private static void triggerQuickPartyAction(Minecraft minecraft, QuickPartyAction action) {
        if (minecraft == null || action == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        UUID localPlayerId = player.getUUID();
        boolean inParty = ClientPartyData.getParty(localPlayerId) != null;
        if (!inParty && action.requiresParty()) {
            player.chat("/party create");
            if (!PENDING_QUICK_ACTIONS.contains(action)) {
                PENDING_QUICK_ACTIONS.addLast(action);
            }
            return;
        }

        executeQuickPartyAction(minecraft, action);
    }

    private static void processPendingQuickActions(Minecraft minecraft) {
        if (minecraft == null || PENDING_QUICK_ACTIONS.isEmpty()) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        if (ClientPartyData.getParty(player.getUUID()) == null) {
            return;
        }

        while (!PENDING_QUICK_ACTIONS.isEmpty()) {
            QuickPartyAction action = PENDING_QUICK_ACTIONS.pollFirst();
            if (action == null) {
                continue;
            }
            executeQuickPartyAction(minecraft, action);
        }
    }

    private static void executeQuickPartyAction(Minecraft minecraft, QuickPartyAction action) {
        if (minecraft == null || action == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        switch (action) {
            case CREATE_AND_INVITE_ALL -> player.chat("/party invite all");
            case CREATE_AND_INVITE_FAVORITES -> inviteFavoritePlayers(minecraft);
            case INVITE_NEARBY -> player.chat("/party invite nearby");
        }
    }

    private static void inviteFavoritePlayers(Minecraft minecraft) {
        if (minecraft == null) return;
        ClientPacketListener connection = minecraft.getConnection();
        LocalPlayer player = minecraft.player;
        if (connection == null || player == null) {
            return;
        }

        Party currentParty = ClientPartyData.getParty(player.getUUID());
        if (currentParty == null) {
            return;
        }

        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            if (playerInfo == null) {
                continue;
            }

            GameProfile profile = playerInfo.getProfile();
            if (profile == null || profile.getId() == null || profile.getName() == null) {
                continue;
            }

            UUID playerId = profile.getId();
            if (playerId.equals(player.getUUID())) {
                continue;
            }
            if (isPlayerInCurrentParty(currentParty, playerId) || isPlayerInOtherParty(currentParty, playerId)) {
                continue;
            }
            if (ClientFavoritePlayers.isFavorite(playerId)) {
                player.chat("/party invite " + profile.getName());
            }
        }
    }

    private static boolean isPlayerInCurrentParty(Party currentParty, UUID playerId) {
        if (currentParty == null || playerId == null) {
            return false;
        }

        UUID leader = currentParty.getLeader();
        if (leader != null && leader.equals(playerId)) {
            return true;
        }

        if (currentParty.getMembers() != null) {
            for (UUID memberId : currentParty.getMembers()) {
                if (playerId.equals(memberId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isPlayerInOtherParty(Party currentParty, UUID playerId) {
        if (playerId == null) {
            return false;
        }

        Party playerParty = ClientPartyData.getParty(playerId);
        return playerParty != null && !isPlayerInCurrentParty(currentParty, playerId);
    }

    private enum QuickPartyAction {
        CREATE_AND_INVITE_ALL(true),
        CREATE_AND_INVITE_FAVORITES(true),
        INVITE_NEARBY(true);

        private final boolean requiresParty;

        QuickPartyAction(boolean requiresParty) {
            this.requiresParty = requiresParty;
        }

        boolean requiresParty() {
            return requiresParty;
        }
    }
}
