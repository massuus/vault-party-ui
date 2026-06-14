package dev.massuus.vaultpartyui.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import iskallia.vault.world.data.VaultPartyData.Party;

final class PartyDebugData {
    static boolean ENABLED = false;

    private static final int ONLINE_PLAYER_COUNT = 34;
    private static final int PARTY_MEMBER_COUNT = 24;
    private static final Map<UUID, String> DEBUG_NAMES = new HashMap<>();

    private PartyDebugData() {
    }

    static Party partyForDisplay(@Nullable Party currentParty, @Nullable UUID localPlayerId) {
        if (!ENABLED) {
            return currentParty;
        }

        Set<UUID> memberIds = new LinkedHashSet<>();
        if (currentParty != null) {
            addIfPresent(memberIds, currentParty.getLeader());
            if (currentParty.getMembers() != null) {
                memberIds.addAll(currentParty.getMembers());
            }
        }
        addIfPresent(memberIds, localPlayerId);

        int debugIndex = 1;
        while (memberIds.size() < PARTY_MEMBER_COUNT) {
            memberIds.add(debugId(debugIndex));
            debugIndex++;
        }

        Party debugParty = new Party();
        for (UUID memberId : memberIds) {
            debugParty.addMember(memberId);
        }
        return debugParty;
    }

    static List<OnlinePlayer> onlinePlayersForDisplay(List<OnlinePlayer> realPlayers) {
        if (!ENABLED) {
            return realPlayers;
        }

        List<OnlinePlayer> players = new ArrayList<>();
        Set<UUID> existingIds = new LinkedHashSet<>();
        if (realPlayers != null) {
            for (OnlinePlayer player : realPlayers) {
                if (player == null || player.id == null) {
                    continue;
                }
                players.add(player);
                existingIds.add(player.id);
            }
        }

        for (int i = 1; players.size() < ONLINE_PLAYER_COUNT; i++) {
            UUID id = debugId(i);
            if (existingIds.add(id)) {
                players.add(new OnlinePlayer(id, debugName(i)));
            }
        }

        players.sort(Comparator.comparing(player -> safeName(player).toLowerCase(Locale.ROOT)));
        return players;
    }

    @Nullable
    static String resolveName(@Nullable UUID playerId) {
        if (!ENABLED || playerId == null) {
            return null;
        }
        return DEBUG_NAMES.get(playerId);
    }

    private static void addIfPresent(Set<UUID> ids, @Nullable UUID id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private static UUID debugId(int index) {
        UUID id = new UUID(0x7650000000000000L, index);
        DEBUG_NAMES.putIfAbsent(id, debugName(index));
        return id;
    }

    private static String debugName(int index) {
        return String.format(Locale.ROOT, "DebugPlayer%02d", index);
    }

    private static String safeName(OnlinePlayer player) {
        return player == null || player.name == null ? "" : player.name;
    }
}
