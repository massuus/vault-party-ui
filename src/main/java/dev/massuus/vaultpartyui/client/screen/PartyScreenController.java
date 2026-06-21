package dev.massuus.vaultpartyui.client.screen;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.massuus.vaultpartyui.client.ClientFavoritePlayers;
import dev.massuus.vaultpartyui.client.ClientTickEvents;
import dev.massuus.vaultpartyui.client.VoiceChatIntegration;
import iskallia.vault.client.data.ClientPartyInviteState;
import iskallia.vault.network.message.ServerboundPartyInviteResponseMessage;
import iskallia.vault.world.data.VaultPartyData.Party;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

final class PartyScreenController {
    private final Screen owner;
    private final PartySupplier currentParty;
    private final OnlinePlayersSupplier onlinePlayers;
    private final Map<UUID, Long> inviteCooldownUntilMs;
    private final long inviteCooldownMs;
    private final ToastSink pushToast;
    private final Consumer<Component> showClientMessage;

    PartyScreenController(
            Screen owner,
            PartySupplier currentParty,
            OnlinePlayersSupplier onlinePlayers,
            Map<UUID, Long> inviteCooldownUntilMs,
            long inviteCooldownMs,
            ToastSink pushToast,
            Consumer<Component> showClientMessage
    ) {
        this.owner = owner;
        this.currentParty = currentParty;
        this.onlinePlayers = onlinePlayers;
        this.inviteCooldownUntilMs = inviteCooldownUntilMs;
        this.inviteCooldownMs = inviteCooldownMs;
        this.pushToast = pushToast;
        this.showClientMessage = showClientMessage;
    }

    void sendPartyCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        LocalPlayer player = client.player;
        if (connection != null && player != null) {
            player.chat("/" + command);
        }
    }

    void restorePreviousParty(List<PreviousPartyMember> members) {
        if (members == null || members.isEmpty()) {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_no_previous_party"), 0xB0B0B0);
            return;
        }

        sendPartyCommand("party create");
        int invited = 0;
        for (PreviousPartyMember member : members) {
            if (member == null || member.id == null || member.name == null || member.name.isBlank()) {
                continue;
            }
            sendPartyCommand("party invite " + member.name);
            invited++;
        }

        if (invited > 0) {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_restored_previous_party", invited), 0xA0E0A0);
        } else {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_no_previous_party"), 0xB0B0B0);
        }
    }

    void acceptPendingInvite() {
        respondToPendingInvite(true);
    }

    void declinePendingInvite() {
        respondToPendingInvite(false);
    }

    void confirmDisbandParty() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                sendPartyCommand("party disband");
            }
            mc.setScreen(this.owner);
        },
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_title"),
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_message"),
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_yes"),
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_no")));
    }

    boolean isPartyLeader() {
        return PartyRosterService.isPartyLeader(this.currentParty.get(), PartyPlayerLookup.getLocalPlayerId());
    }

    boolean isLocalPlayerInParty() {
        return PartyRosterService.isLocalPlayerInParty(this.currentParty.get(), PartyPlayerLookup.getLocalPlayerId());
    }

    boolean canInvitePartyMemberToVoice(@Nullable UUID memberId) {
        if (memberId == null || memberId.equals(PartyPlayerLookup.getLocalPlayerId())) {
            return false;
        }
        return VoiceChatIntegration.isVoiceChatLoaded()
                && VoiceChatIntegration.hasLocalVoiceGroup()
                && !VoiceChatIntegration.isPlayerInLocalVoiceGroup(memberId)
                && PartyPlayerLookup.findOnlinePlayer(this.onlinePlayers.get(), memberId) != null;
    }

    boolean canJoinPartyMemberVoiceGroup(@Nullable UUID memberId) {
        if (memberId == null || memberId.equals(PartyPlayerLookup.getLocalPlayerId())) {
            return false;
        }
        return VoiceChatIntegration.isVoiceChatLoaded()
                && !VoiceChatIntegration.hasLocalVoiceGroup()
                && VoiceChatIntegration.getPlayerVoiceGroupId(memberId) != null
                && PartyPlayerLookup.findOnlinePlayer(this.onlinePlayers.get(), memberId) != null;
    }

    void invitePlayerToVoiceGroup(@Nullable UUID playerId) {
        OnlinePlayer player = PartyPlayerLookup.findOnlinePlayer(this.onlinePlayers.get(), playerId);
        if (player == null) {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_no_party_voice_invites"), 0xB0B0B0);
            return;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            localPlayer.chat("/voicechat invite " + player.name);
            toast(new TranslatableComponent("screen.vaultpartyui.toast_invited_to_voice", player.name), 0xA0E0A0);
        }
    }

    void joinPartyMemberVoiceGroup(@Nullable UUID playerId) {
        OnlinePlayer player = PartyPlayerLookup.findOnlinePlayer(this.onlinePlayers.get(), playerId);
        UUID groupId = VoiceChatIntegration.getPlayerVoiceGroupId(playerId);
        if (player == null || groupId == null) {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_no_joinable_voice_group"), 0xB0B0B0);
            return;
        }

        boolean sent = VoiceChatIntegration.joinVoiceGroup(groupId, null);
        if (sent) {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_joining_voice_group", player.name), 0xA0E0A0);
        } else {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_join_voice_group_failed"), 0xB0B0B0);
        }
    }

    boolean hasInviteableFavorites() {
        List<OnlineRow> rows = PartyRosterService.buildRows(
                this.onlinePlayers.get(),
                FilterMode.ALL,
                this.currentParty.get(),
                PartyPlayerLookup.getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );

        for (OnlineRow row : rows) {
            if (row.favorite && row.state == RowState.INVITEABLE) {
                return true;
            }
        }
        return false;
    }

    boolean hasOnlineFavorites() {
        UUID localPlayerId = PartyPlayerLookup.getLocalPlayerId();
        Party party = this.currentParty.get();
        for (OnlinePlayer player : this.onlinePlayers.get()) {
            if (player == null || player.id == null || player.id.equals(localPlayerId)) {
                continue;
            }
            if (ClientFavoritePlayers.isFavorite(player.id) && !PartyRosterService.isPlayerInOtherParty(party, player.id)) {
                return true;
            }
        }
        return false;
    }

    boolean hasInviteableVoiceGroupPlayersForParty(List<OnlineRow> voiceRows) {
        for (OnlineRow row : voiceRows) {
            if (row.state == RowState.INVITEABLE) {
                return true;
            }
        }
        return false;
    }

    boolean hasPartyMembersAvailableForVoiceInvite() {
        Party party = this.currentParty.get();
        if (!VoiceChatIntegration.isVoiceChatLoaded() || party == null || party.getMembers() == null) {
            return false;
        }

        if (!VoiceChatIntegration.hasLocalVoiceGroup()) {
            for (UUID memberId : party.getMembers()) {
                if (memberId != null
                        && !memberId.equals(PartyPlayerLookup.getLocalPlayerId())
                        && PartyPlayerLookup.findOnlinePlayer(this.onlinePlayers.get(), memberId) != null) {
                    return true;
                }
            }
            return false;
        }

        for (UUID memberId : party.getMembers()) {
            if (canInvitePartyMemberToVoice(memberId)) {
                return true;
            }
        }
        return false;
    }

    void inviteFavoritePlayers() {
        List<OnlineRow> rows = PartyRosterService.buildRows(
                this.onlinePlayers.get(),
                FilterMode.ALL,
                this.currentParty.get(),
                PartyPlayerLookup.getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );

        int invited = 0;
        for (OnlineRow row : rows) {
            if (!row.favorite || row.state != RowState.INVITEABLE) {
                continue;
            }
            sendPartyCommand("party invite " + row.player.name);
            this.inviteCooldownUntilMs.put(row.player.id, System.currentTimeMillis() + this.inviteCooldownMs);
            invited++;
        }

        if (invited > 0) {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_invited_favorites"), 0xA0E0A0);
        } else {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_no_favorite_invites"), 0xB0B0B0);
        }
    }

    void invitePartyToVoiceGroup() {
        if (!VoiceChatIntegration.isVoiceChatLoaded()) {
            toast(new TranslatableComponent("screen.vaultpartyui.toast_voicechat_missing"), 0xB0B0B0);
            return;
        }

        if (VoiceChatIntegration.hasLocalVoiceGroup()) {
            ClientTickEvents.invitePartyToVoiceGroupNow();
            return;
        }

        Minecraft.getInstance().setScreen(new VoiceGroupCreateScreen(this.owner));
    }

    void leaveVoiceGroup() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.chat("/voicechat leave");
            toast(new TranslatableComponent("screen.vaultpartyui.toast_left_voice_group"), 0xE3C38C);
        }
    }

    boolean performPrimaryRowAction(@Nullable OnlineRow row) {
        if (row == null || row.player == null) {
            return false;
        }

        OnlinePlayer player = row.player;
        return switch (row.state) {
            case INVITEABLE -> {
                sendPartyCommand("party invite " + player.name);
                this.inviteCooldownUntilMs.put(player.id, System.currentTimeMillis() + this.inviteCooldownMs);
                toast(new TranslatableComponent("screen.vaultpartyui.toast_invited", player.name), 0xA0E0A0);
                yield true;
            }
            case PARTY_MEMBER -> {
                if (isPartyLeader()) {
                    sendPartyCommand("party remove " + player.name);
                    toast(new TranslatableComponent("screen.vaultpartyui.toast_removed", player.name), 0xE0A0A0);
                    yield true;
                }
                toast(new TranslatableComponent("screen.vaultpartyui.tip_member"), 0xE3C38C);
                yield false;
            }
            case OTHER_PARTY -> {
                Component message = new TranslatableComponent("screen.vaultpartyui.already_in_party_local", player.name);
                this.showClientMessage.accept(message);
                toast(message, 0xE3C38C);
                yield false;
            }
            case COOLDOWN -> {
                toast(new TranslatableComponent("screen.vaultpartyui.tip_cooldown"), 0xB0B0B0);
                yield false;
            }
            case NO_ACTION -> {
                toast(new TranslatableComponent("screen.vaultpartyui.tip_no_action"), 0xB0B0B0);
                yield false;
            }
            default -> false;
        };
    }

    private void respondToPendingInvite(boolean accepted) {
        if (!ClientPartyInviteState.hasPendingInvite()) {
            return;
        }

        UUID inviteId = ClientPartyInviteState.getInviteId();
        if (inviteId != null) {
            ServerboundPartyInviteResponseMessage.send(inviteId, accepted);
            ClientPartyInviteState.clearInvite();
        }
    }

    private void toast(Component message, int color) {
        this.pushToast.accept(message, color);
    }

    @FunctionalInterface
    interface PartySupplier {
        @Nullable
        Party get();
    }

    @FunctionalInterface
    interface OnlinePlayersSupplier {
        List<OnlinePlayer> get();
    }

    @FunctionalInterface
    interface ToastSink {
        void accept(@Nullable Component message, int color);
    }
}
