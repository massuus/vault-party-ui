package dev.massuus.vaultpartyui.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TranslatableComponent;

final class PartyScreenFooter {
    private static final String SEPARATOR = " | ";
    private static final String CREDIT = "Made by Massuus";
    private static final int PILL_GAP = 4;
    private static final int PILL_HORIZONTAL_PADDING = 5;
    private static final int PILL_VERTICAL_PADDING = 2;
    private static final int RIGHT_PADDING = 8;

    private PartyScreenFooter() {
    }

    static void render(
            @Nonnull PoseStack poseStack,
            Font font,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            @Nonnull String version,
            boolean updateAvailable
    ) {
        Objects.requireNonNull(poseStack, "poseStack");

        String safeVersion = Objects.requireNonNull(version);
        String updateText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.update_available").getString());
        int versionWidth = font.width(safeVersion);
        int pillWidth = updateAvailable ? font.width(updateText) + PILL_HORIZONTAL_PADDING * 2 : 0;
        int pillGap = updateAvailable ? PILL_GAP : 0;
        int separatorWidth = font.width(SEPARATOR);
        int creditWidth = font.width(CREDIT);
        int totalWidth = versionWidth + pillGap + pillWidth + separatorWidth + creditWidth;
        int versionX = screenWidth - RIGHT_PADDING - totalWidth;
        int versionY = screenHeight - 18;

        boolean versionHovered = contains(mouseX, mouseY, versionX, versionY, versionWidth, font.lineHeight);
        int versionColor = versionHovered ? 0xFFFFFF : 0xAAAAAA;
        font.draw(poseStack, safeVersion, versionX, versionY, versionColor);
        if (versionHovered) {
            underline(poseStack, versionX, versionY + font.lineHeight, versionWidth, versionColor);
        }

        int separatorX = versionX + versionWidth;
        if (updateAvailable) {
            int pillX = separatorX + PILL_GAP;
            int pillY = versionY - PILL_VERTICAL_PADDING;
            boolean pillHovered = contains(mouseX, mouseY, pillX, pillY, pillWidth, font.lineHeight + PILL_VERTICAL_PADDING * 2);
            int pillBackground = pillHovered ? 0xFFE3C38C : 0xCC6E9E4A;
            int pillTextColor = pillHovered ? 0xFF111111 : 0xFFFFFFFF;
            GuiComponent.fill(poseStack, pillX, pillY, pillX + pillWidth, pillY + font.lineHeight + PILL_VERTICAL_PADDING * 2, pillBackground);
            font.draw(poseStack, updateText, pillX + PILL_HORIZONTAL_PADDING, versionY, pillTextColor);
            if (pillHovered) {
                renderUpdateTooltip(poseStack, font, screenWidth, mouseX, versionY);
            }
            separatorX = pillX + pillWidth;
        }

        font.draw(poseStack, SEPARATOR, separatorX, versionY, 0xAAAAAA);

        int creditX = separatorX + separatorWidth;
        boolean creditHovered = contains(mouseX, mouseY, creditX, versionY, creditWidth, font.lineHeight);
        int creditColor = creditHovered ? 0xFFFFFF : 0xAAAAAA;
        font.draw(poseStack, CREDIT, creditX, versionY, creditColor);
        if (creditHovered) {
            underline(poseStack, creditX, versionY + font.lineHeight, creditWidth, creditColor);
            renderCreditTooltip(poseStack, font, screenWidth, mouseX, versionY);
        }
    }

    static boolean handleClick(
            Minecraft minecraft,
            Screen returnScreen,
            Font font,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            @Nonnull String version,
            boolean updateAvailable,
            String versionUrl,
            String creditUrl
    ) {
        String safeVersion = Objects.requireNonNull(version);
        String updateText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.update_available").getString());
        int versionWidth = font.width(safeVersion);
        int pillWidth = updateAvailable ? font.width(updateText) + PILL_HORIZONTAL_PADDING * 2 : 0;
        int pillGap = updateAvailable ? PILL_GAP : 0;
        int separatorWidth = font.width(SEPARATOR);
        int creditWidth = font.width(CREDIT);
        int totalWidth = versionWidth + pillGap + pillWidth + separatorWidth + creditWidth;
        int versionX = screenWidth - RIGHT_PADDING - totalWidth;
        int versionY = screenHeight - 18;

        if (contains(mouseX, mouseY, versionX, versionY, versionWidth, font.lineHeight)) {
            PartyScreenLinks.openUrl(minecraft, returnScreen, versionUrl);
            return true;
        }

        int separatorX = versionX + versionWidth;
        if (updateAvailable) {
            int pillX = separatorX + PILL_GAP;
            int pillY = versionY - PILL_VERTICAL_PADDING;
            if (contains(mouseX, mouseY, pillX, pillY, pillWidth, font.lineHeight + PILL_VERTICAL_PADDING * 2)) {
                PartyScreenLinks.openUrl(minecraft, returnScreen, versionUrl);
                return true;
            }
            separatorX = pillX + pillWidth;
        }

        int creditX = separatorX + separatorWidth;
        if (contains(mouseX, mouseY, creditX, versionY, creditWidth, font.lineHeight)) {
            PartyScreenLinks.openUrl(minecraft, returnScreen, creditUrl);
            return true;
        }
        return false;
    }

    private static void renderCreditTooltip(@Nonnull PoseStack poseStack, Font font, int screenWidth, int mouseX, int creditY) {
        String tip = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.credit_tooltip").getString());
        int tipX = Math.min(screenWidth - 10 - font.width(tip), mouseX + 8);
        int tipY = creditY - font.lineHeight - 6;
        GuiComponent.fill(poseStack, tipX - 4, tipY - 2, tipX + font.width(tip) + 4, tipY + font.lineHeight + 2, 0xCC111111);
        font.drawShadow(poseStack, tip, tipX, tipY, 0xFFFFFF);
    }

    private static void renderUpdateTooltip(@Nonnull PoseStack poseStack, Font font, int screenWidth, int mouseX, int versionY) {
        String tip = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.update_available_tooltip").getString());
        int tipX = Math.min(screenWidth - 10 - font.width(tip), mouseX + 8);
        int tipY = versionY - font.lineHeight - 6;
        GuiComponent.fill(poseStack, tipX - 4, tipY - 2, tipX + font.width(tip) + 4, tipY + font.lineHeight + 2, 0xCC111111);
        font.drawShadow(poseStack, tip, tipX, tipY, 0xFFFFFF);
    }

    private static void underline(@Nonnull PoseStack poseStack, int x, int y, int width, int color) {
        GuiComponent.fill(poseStack, x, y, x + width, y + 1, color);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
