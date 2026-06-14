package dev.massuus.vaultpartyui.client.screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iskallia.vault.world.data.VaultPartyData.Party;
import net.minecraftforge.fml.loading.FMLPaths;

final class PreviousPartySnapshot {
    private static final String FILE_NAME = "vaultpartyui-previous-party.txt";
    private static final Map<UUID, String> MEMBERS = new LinkedHashMap<>();

    private static boolean loaded;

    private PreviousPartySnapshot() {
    }

    static void remember(@javax.annotation.Nullable Party party, List<OnlinePlayer> onlinePlayers, @javax.annotation.Nullable UUID localPlayerId) {
        if (party == null || localPlayerId == null) {
            return;
        }

        ensureLoaded();
        LinkedHashMap<UUID, String> remembered = new LinkedHashMap<>();
        addRememberedMember(remembered, party.getLeader(), localPlayerId, onlinePlayers);
        if (party.getMembers() != null) {
            for (UUID memberId : party.getMembers()) {
                addRememberedMember(remembered, memberId, localPlayerId, onlinePlayers);
            }
        }

        if (!remembered.isEmpty()) {
            MEMBERS.clear();
            MEMBERS.putAll(remembered);
            save();
        }
    }

    static List<PreviousPartyMember> members() {
        ensureLoaded();
        List<PreviousPartyMember> result = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : MEMBERS.entrySet()) {
            result.add(new PreviousPartyMember(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static void addRememberedMember(
            Map<UUID, String> remembered,
            UUID playerId,
            UUID localPlayerId,
            List<OnlinePlayer> onlinePlayers
    ) {
        if (playerId == null || playerId.equals(localPlayerId) || remembered.containsKey(playerId)) {
            return;
        }

        String name = resolveName(playerId, onlinePlayers);
        if (name == null || name.isBlank() || "offline".equals(name)) {
            return;
        }
        remembered.put(playerId, name);
    }

    private static String resolveName(UUID playerId, List<OnlinePlayer> onlinePlayers) {
        OnlinePlayer onlinePlayer = PartyPlayerLookup.findOnlinePlayer(onlinePlayers, playerId);
        if (onlinePlayer != null) {
            return onlinePlayer.name;
        }
        return PartyPlayerLookup.resolvePlayerName(playerId);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path file = snapshotFile();
        if (!Files.exists(file)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.indexOf('|');
                if (separator <= 0 || separator >= trimmed.length() - 1) {
                    continue;
                }
                try {
                    UUID id = UUID.fromString(trimmed.substring(0, separator));
                    String name = trimmed.substring(separator + 1).trim();
                    if (!name.isEmpty()) {
                        MEMBERS.put(id, name);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        Path file = snapshotFile();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = MEMBERS.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "|" + entry.getValue())
                    .toList();
            Files.write(file, lines);
        } catch (IOException ignored) {
        }
    }

    private static Path snapshotFile() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
