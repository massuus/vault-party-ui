package dev.massuus.vaultpartyui.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
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
    private static int pendingVoiceGroupInviteTicks;

    private ClientTickEvents() {
    }

    public static void triggerVoiceGroupPartyAction() {
        triggerQuickPartyAction(Minecraft.getInstance(), QuickPartyAction.INVITE_VOICE_GROUP);
    }

    public static void triggerCreateAndInviteAllAction() {
        triggerQuickPartyAction(Minecraft.getInstance(), QuickPartyAction.CREATE_AND_INVITE_ALL);
    }

    public static void triggerCreateAndInviteFavoritesAction() {
        triggerQuickPartyAction(Minecraft.getInstance(), QuickPartyAction.CREATE_AND_INVITE_FAVORITES);
    }

    public static void triggerInviteNearbyAction() {
        triggerQuickPartyAction(Minecraft.getInstance(), QuickPartyAction.INVITE_NEARBY);
    }

    public static void queueInvitePartyToVoiceGroup() {
        pendingVoiceGroupInviteTicks = 100;
    }

    public static void invitePartyToVoiceGroupNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        invitePartyMembersToVoiceGroup(minecraft);
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
        processPendingVoiceGroupInvite(minecraftClient);
    }

    private static void processPendingVoiceGroupInvite(Minecraft minecraft) {
        if (pendingVoiceGroupInviteTicks <= 0) {
            return;
        }

        pendingVoiceGroupInviteTicks--;
        if (!VoiceChatIntegration.hasLocalVoiceGroup()) {
            if (pendingVoiceGroupInviteTicks == 0 && minecraft.player != null) {
                minecraft.player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_voice_group_create_timeout"), false);
            }
            return;
        }

        pendingVoiceGroupInviteTicks = 0;
        invitePartyMembersToVoiceGroup(minecraft);
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
            case INVITE_VOICE_GROUP -> inviteVoiceGroupPlayers(minecraft);
        }
    }

    private static void inviteVoiceGroupPlayers(Minecraft minecraft) {
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

        Set<UUID> voiceGroupMembers = VoiceChatIntegration.getLocalVoiceGroupMemberIds();
        if (voiceGroupMembers.isEmpty()) {
            player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_no_voice_group"), false);
            return;
        }

        int invited = 0;
        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            if (playerInfo == null) {
                continue;
            }

            GameProfile profile = playerInfo.getProfile();
            if (profile == null || profile.getId() == null || profile.getName() == null) {
                continue;
            }

            UUID playerId = profile.getId();
            if (!voiceGroupMembers.contains(playerId) || playerId.equals(player.getUUID())) {
                continue;
            }
            if (isPlayerInCurrentParty(currentParty, playerId) || isPlayerInOtherParty(currentParty, playerId)) {
                continue;
            }

            player.chat("/party invite " + profile.getName());
            invited++;
        }

        Component message = invited > 0
                ? new TranslatableComponent("screen.vaultpartyui.toast_invited_voice_group", invited)
                : new TranslatableComponent("screen.vaultpartyui.toast_no_voice_group_invites");
        player.displayClientMessage(message, false);
    }

    private static void invitePartyMembersToVoiceGroup(Minecraft minecraft) {
        if (minecraft == null) return;
        ClientPacketListener connection = minecraft.getConnection();
        LocalPlayer player = minecraft.player;
        if (connection == null || player == null) {
            return;
        }

        if (!VoiceChatIntegration.isVoiceChatLoaded()) {
            player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_voicechat_missing"), false);
            return;
        }

        if (!VoiceChatIntegration.hasLocalVoiceGroup()) {
            player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_no_voice_group"), false);
            return;
        }

        Party currentParty = ClientPartyData.getParty(player.getUUID());
        if (currentParty == null) {
            player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_no_party_voice_invites"), false);
            return;
        }

        Set<UUID> voiceGroupMembers = VoiceChatIntegration.getLocalVoiceGroupMemberIds();
        if (currentParty.getMembers() == null || currentParty.getMembers().isEmpty()) {
            player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_no_party_voice_invites"), false);
            return;
        }

        int invited = 0;
        for (UUID memberId : currentParty.getMembers()) {
            if (memberId == null || memberId.equals(player.getUUID()) || voiceGroupMembers.contains(memberId)) {
                continue;
            }

            PlayerInfo info = connection.getPlayerInfo(memberId);
            if (info == null || info.getProfile() == null || info.getProfile().getName() == null) {
                continue;
            }

            player.chat("/voicechat invite " + info.getProfile().getName());
            invited++;
        }

        Component message = invited > 0
                ? new TranslatableComponent("screen.vaultpartyui.toast_invited_party_to_voice_group", invited)
                : new TranslatableComponent("screen.vaultpartyui.toast_no_party_voice_invites");
        player.displayClientMessage(message, false);
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
        INVITE_NEARBY(true),
        INVITE_VOICE_GROUP(true);

        private final boolean requiresParty;

        QuickPartyAction(boolean requiresParty) {
            this.requiresParty = requiresParty;
        }

        boolean requiresParty() {
            return requiresParty;
        }
    }
}
