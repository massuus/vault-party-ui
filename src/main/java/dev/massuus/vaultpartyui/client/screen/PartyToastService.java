package dev.massuus.vaultpartyui.client.screen;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

final class PartyToastService {
    private static final int MAX_TOASTS = 3;
    private static final long TOAST_DURATION_MS = 2600L;

    private PartyToastService() {
    }

    static void prune(List<UiToast> toasts, long now) {
        toasts.removeIf(toast -> toast.expiresAt <= now);
    }

    static void push(List<UiToast> toasts, @Nullable Component message, int color) {
        if (message == null) {
            return;
        }
        toasts.add(new UiToast(message, color, System.currentTimeMillis() + TOAST_DURATION_MS));
        if (toasts.size() > MAX_TOASTS) {
            toasts.remove(0);
        }
    }

    static void render(@Nonnull PoseStack poseStack, Font font, int screenWidth, List<UiToast> toasts) {
        Objects.requireNonNull(poseStack, "poseStack");
        if (toasts.isEmpty()) {
            return;
        }

        int y = 34;
        for (UiToast toast : toasts) {
            String text = Objects.requireNonNull(toast.message.getString());
            int width = font.width(text) + 10;
            int x = screenWidth - width - 10;
            GuiComponent.fill(poseStack, x, y - 1, x + width, y + font.lineHeight + 3, 0xCC111111);
            font.drawShadow(poseStack, text, x + 5, y + 1, toast.color);
            y += font.lineHeight + 6;
        }
    }
}
