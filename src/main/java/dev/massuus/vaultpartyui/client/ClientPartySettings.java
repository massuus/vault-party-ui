package dev.massuus.vaultpartyui.client;

import net.minecraftforge.fml.loading.FMLPaths;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ClientPartySettings {
    private static final String FILE_NAME = "vaultpartyui-party-settings.txt";

    private static AutoAcceptMode autoAcceptMode = AutoAcceptMode.OFF;
    private static boolean loaded;

    private ClientPartySettings() {
    }

    public static AutoAcceptMode getAutoAcceptMode() {
        ensureLoaded();
        return autoAcceptMode;
    }

    public static boolean isAutoAcceptInvitesEnabled() {
        return getAutoAcceptMode() != AutoAcceptMode.OFF;
    }

    public static void setAutoAcceptInvitesEnabled(boolean enabled) {
        setAutoAcceptMode(enabled ? AutoAcceptMode.ALL : AutoAcceptMode.OFF);
    }

    public static void setAutoAcceptMode(AutoAcceptMode mode) {
        ensureLoaded();
        autoAcceptMode = mode == null ? AutoAcceptMode.OFF : mode;
        save();
    }

    public static void toggleAutoAcceptInvites() {
        cycleAutoAcceptMode();
    }

    public static void cycleAutoAcceptMode() {
        setAutoAcceptMode(getAutoAcceptMode().next());
    }

    public static boolean shouldAutoAcceptInvite(Minecraft minecraft, String inviterName) {
        return switch (getAutoAcceptMode()) {
            case OFF -> false;
            case ALL -> true;
            case FAVORITES_ONLY -> isFavoriteInviter(minecraft, inviterName);
            default -> false;
        };
    }

    private static boolean isFavoriteInviter(Minecraft minecraft, String inviterName) {
        if (minecraft == null || inviterName == null || inviterName.isBlank()) {
            return false;
        }

        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return false;
        }

        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            if (playerInfo == null || playerInfo.getProfile() == null) {
                continue;
            }

            String onlineName = playerInfo.getProfile().getName();
            if (onlineName == null || !onlineName.equalsIgnoreCase(inviterName)) {
                continue;
            }

            return ClientFavoritePlayers.isFavorite(playerInfo.getProfile().getId());
        }

        return false;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path file = settingsFile();
        if (file == null || !Files.exists(file)) {
            return;
        }

        try {
            String raw = Files.readString(file).trim();
            if (!raw.isEmpty()) {
                autoAcceptMode = AutoAcceptMode.fromSerialized(raw);
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        Path file = settingsFile();
        if (file == null) {
            return;
        }

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, autoAcceptMode.name());
        } catch (IOException ignored) {
        }
    }

    private static Path settingsFile() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public enum AutoAcceptMode {
        OFF("screen.vaultpartyui.auto_accept_mode_off", "screen.vaultpartyui.toast_auto_accept_mode_off"),
        FAVORITES_ONLY("screen.vaultpartyui.auto_accept_mode_favorites", "screen.vaultpartyui.toast_auto_accept_mode_favorites"),
        ALL("screen.vaultpartyui.auto_accept_mode_all", "screen.vaultpartyui.toast_auto_accept_mode_all");

        private final String labelKey;
        private final String toastKey;

        AutoAcceptMode(String labelKey, String toastKey) {
            this.labelKey = labelKey;
            this.toastKey = toastKey;
        }

        public String getLabelKey() {
            return labelKey;
        }

        public String getToastKey() {
            return toastKey;
        }

        public AutoAcceptMode next() {
            return switch (this) {
                case OFF -> FAVORITES_ONLY;
                case FAVORITES_ONLY -> ALL;
                default -> OFF;
            };
        }

        public static AutoAcceptMode fromSerialized(String value) {
            if (value == null) {
                return OFF;
            }

            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (AutoAcceptMode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }

            return OFF;
        }
    }
}
