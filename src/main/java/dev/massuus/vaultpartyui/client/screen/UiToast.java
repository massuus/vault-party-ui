package dev.massuus.vaultpartyui.client.screen;

import net.minecraft.network.chat.Component;

final class UiToast {
    final Component message;
    final int color;
    final long expiresAt;

    UiToast(Component message, int color, long expiresAt) {
        this.message = message;
        this.color = color;
        this.expiresAt = expiresAt;
    }
}
