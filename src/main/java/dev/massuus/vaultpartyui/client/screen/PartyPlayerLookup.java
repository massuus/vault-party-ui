package dev.massuus.vaultpartyui.client.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

final class PartyPlayerLookup {
    private static final Map<UUID, String> KNOWN_NAMES = new HashMap<>();
    private static final Map<UUID, ResourceLocation> KNOWN_SKINS = new HashMap<>();
    private static final Set<UUID> ONLINE_IDS = new HashSet<>();

    private PartyPlayerLookup() {
    }

    static List<OnlinePlayer> gatherOnlinePlayers(@Nullable ClientPacketListener connection) {
        ONLINE_IDS.clear();
        if (connection == null) {
            return Collections.emptyList();
        }

        List<OnlinePlayer> players = new ArrayList<>();
        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            GameProfile profile = playerInfo.getProfile();
            if (profile != null && profile.getId() != null && profile.getName() != null) {
                UUID playerId = profile.getId();
                String playerName = profile.getName();
                players.add(new OnlinePlayer(playerId, playerName));
                ONLINE_IDS.add(playerId);
                KNOWN_NAMES.put(playerId, playerName);
                ResourceLocation skin = playerInfo.getSkinLocation();
                if (skin != null) {
                    KNOWN_SKINS.put(playerId, skin);
                }
            }
        }

        players.sort(Comparator.comparing(player -> player.name.toLowerCase(Locale.ROOT)));
        return players;
    }

    @Nullable
    static UUID getLocalPlayerId() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }

    static String resolvePlayerName(@Nullable UUID playerId) {
        if (playerId == null) {
            return "offline";
        }

        String debugName = PartyDebugData.resolveName(playerId);
        if (debugName != null) {
            return debugName;
        }

        String knownName = KNOWN_NAMES.get(playerId);
        if (knownName != null && !knownName.isBlank()) {
            return knownName;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return "offline";
        }

        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null && playerId.equals(profile.getId())) {
                return profile.getName();
            }
        }
        return "offline";
    }

    static boolean isPlayerOnline(@Nullable UUID playerId) {
        return playerId != null && ONLINE_IDS.contains(playerId);
    }

    @Nullable
    static OnlinePlayer findOnlinePlayer(List<OnlinePlayer> onlinePlayers, @Nullable UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (OnlinePlayer player : onlinePlayers) {
            if (player != null && playerId.equals(player.id)) {
                return player;
            }
        }
        return null;
    }

    static ResourceLocation getPlayerSkin(@Nullable UUID playerId) {
        UUID safeId = playerId == null ? new UUID(0L, 0L) : playerId;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(safeId);
            if (playerInfo != null) {
                ResourceLocation skin = playerInfo.getSkinLocation();
                if (skin != null) {
                    KNOWN_SKINS.put(safeId, skin);
                    return skin;
                }
            }
        }
        ResourceLocation knownSkin = KNOWN_SKINS.get(safeId);
        if (knownSkin != null) {
            return knownSkin;
        }
        return DefaultPlayerSkin.getDefaultSkin(safeId);
    }
}
