package dev.massuus.vaultpartyui.client;

public final class ClientPartySettings {
    private static boolean autoAcceptInvitesEnabled;

    private ClientPartySettings() {
    }

    public static boolean isAutoAcceptInvitesEnabled() {
        return autoAcceptInvitesEnabled;
    }

    public static void setAutoAcceptInvitesEnabled(boolean enabled) {
        autoAcceptInvitesEnabled = enabled;
    }

    public static void toggleAutoAcceptInvites() {
        autoAcceptInvitesEnabled = !autoAcceptInvitesEnabled;
    }
}
