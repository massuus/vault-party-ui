package dev.massuus.vaultpartyui.client.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private PartyPlayerLookup() {
    }

    static List<OnlinePlayer> gatherOnlinePlayers(@Nullable ClientPacketListener connection) {
        if (connection == null) {
            return Collections.emptyList();
        }

        List<OnlinePlayer> players = new ArrayList<>();
        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            GameProfile profile = playerInfo.getProfile();
            if (profile != null && profile.getId() != null && profile.getName() != null) {
                players.add(new OnlinePlayer(profile.getId(), profile.getName()));
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
                return playerInfo.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(safeId);
    }
}
