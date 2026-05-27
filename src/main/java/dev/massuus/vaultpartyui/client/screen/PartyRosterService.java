package dev.massuus.vaultpartyui.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.massuus.vaultpartyui.client.ClientFavoritePlayers;
import iskallia.vault.client.data.ClientPartyData;
import iskallia.vault.world.data.VaultPartyData.Party;

final class PartyRosterService {
    private PartyRosterService() {
    }

    static List<OnlineRow> buildRows(
            List<OnlinePlayer> players,
            FilterMode filterMode,
            Party currentParty,
            UUID localPlayerId,
            Map<UUID, Long> inviteCooldownUntilMs
    ) {
        if (players == null || players.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<OnlineRow> rows = new ArrayList<>();
        for (OnlinePlayer player : players) {
            RowState state = resolveRowState(player, currentParty, localPlayerId, inviteCooldownUntilMs);
            if (filterMode == FilterMode.ACTIONABLE && state != RowState.INVITEABLE && state != RowState.PARTY_MEMBER) {
                continue;
            }
            if (filterMode == FilterMode.OTHER_PARTY && state != RowState.OTHER_PARTY) {
                continue;
            }
            rows.add(new OnlineRow(player, state, ClientFavoritePlayers.isFavorite(player.id))); 
        }

        rows.sort((a, b) -> {
            int p = Integer.compare(rowStatePriority(a.state), rowStatePriority(b.state));
            if (p != 0) return p;
            return a.player.name.compareToIgnoreCase(b.player.name);
        });

        return rows;
    }

    static boolean isPartyLeader(Party currentParty, UUID localPlayerId) {
        if (currentParty == null || localPlayerId == null) return false;
        UUID leader = currentParty.getLeader();
        return leader != null && leader.equals(localPlayerId);
    }

    static boolean isLocalPlayerInParty(Party currentParty, UUID localPlayerId) {
        if (currentParty == null || localPlayerId == null) return false;
        UUID leader = currentParty.getLeader();
        if (leader != null && leader.equals(localPlayerId)) return true;
        List<UUID> members = currentParty.getMembers();
        if (members != null) {
            for (UUID m : members) {
                if (localPlayerId.equals(m)) return true;
            }
        }
        return false;
    }

    static boolean isPlayerInCurrentParty(Party currentParty, UUID playerId) {
        if (currentParty == null || playerId == null) return false;
        UUID leader = currentParty.getLeader();
        if (leader != null && leader.equals(playerId)) return true;
        List<UUID> members = currentParty.getMembers();
        if (members != null) {
            for (UUID memberId : members) {
                if (playerId.equals(memberId)) return true;
            }
        }
        return false;
    }

    static boolean isPlayerInOtherParty(Party currentParty, UUID playerId) {
        if (playerId == null) return false;
        Party party = ClientPartyData.getParty(playerId);
        if (party == null) return false;
        return !isPlayerInCurrentParty(currentParty, playerId);
    }

    static void pruneCooldowns(Map<UUID, Long> cooldownMap, long nowMs) {
        if (cooldownMap == null || cooldownMap.isEmpty()) {
            return;
        }
        java.util.Iterator<Map.Entry<UUID, Long>> it = cooldownMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() == null || e.getValue() <= nowMs) {
                it.remove();
            }
        }
    }

    private static RowState resolveRowState(
            OnlinePlayer player,
            Party currentParty,
            UUID localPlayerId,
            Map<UUID, Long> inviteCooldownUntilMs
    ) {
        if (player == null || player.id == null) return RowState.NO_ACTION;
        if (localPlayerId != null && localPlayerId.equals(player.id)) return RowState.SELF;
        if (isPlayerInOtherParty(currentParty, player.id)) return RowState.OTHER_PARTY;
        if (!isLocalPlayerInParty(currentParty, localPlayerId)) return RowState.NO_ACTION;
        if (isPlayerInCurrentParty(currentParty, player.id)) return RowState.PARTY_MEMBER;
        Long until = inviteCooldownUntilMs == null ? null : inviteCooldownUntilMs.get(player.id);
        if (until != null && until > System.currentTimeMillis()) return RowState.COOLDOWN;
        return RowState.INVITEABLE;
    }

    private static int rowStatePriority(RowState state) {
        if (state == null) return 99;
        return switch (state) {
            case INVITEABLE -> 0;
            case COOLDOWN -> 1;
            case PARTY_MEMBER -> 2;
            case OTHER_PARTY -> 3;
            case SELF -> 4;
            default -> 5;
        };
    }
}
