package dev.massuus.vaultpartyui.client.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;

final class PartyScreenLinks {
    private PartyScreenLinks() {
    }

    static void openUrl(Minecraft mc, Screen returnScreen, String url) {
        if (mc == null || url == null || url.isBlank()) {
            return;
        }

        mc.setScreen(new ConfirmLinkScreen(accepted -> {
            if (accepted) {
                try {
                    Util.getPlatform().openUri(url);
                } catch (Exception ignored) {
                }
            }

            mc.setScreen(returnScreen);
        }, url, false));
    }
}
