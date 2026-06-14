package dev.massuus.vaultpartyui.client.screen;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

final class PartyScreenGraphics {
    static final int HEAD_SIZE = 8;
    static final int VOICE_ICON_SIZE = 10;

    private static final float VOICE_ICON_SCALE = VOICE_ICON_SIZE / 16.0F;
    @Nonnull
    private static final ResourceLocation VOICE_GROUP_ICON = Objects.requireNonNull(new ResourceLocation("voicechat", "textures/icons/microphone.png"));

    private PartyScreenGraphics() {
    }

    static void drawPanel(@Nonnull PoseStack poseStack, int x, int y, int width, int height) {
        Objects.requireNonNull(poseStack, "poseStack");
        GuiComponent.fill(poseStack, x, y, x + width, y + height, 0xAA111111);
        GuiComponent.fill(poseStack, x, y, x + width, y + 1, 0xFFE3C38C);
    }

    static void drawPlayerHead(@Nonnull PoseStack poseStack, @Nullable UUID playerId, int x, int y) {
        Objects.requireNonNull(poseStack, "poseStack");
        ResourceLocation skin = Objects.requireNonNull(PartyPlayerLookup.getPlayerSkin(playerId));
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, skin);
        GuiComponent.blit(poseStack, x, y, 8.0F, 8.0F, HEAD_SIZE, HEAD_SIZE, 64, 64);
        GuiComponent.blit(poseStack, x, y, 40.0F, 8.0F, HEAD_SIZE, HEAD_SIZE, 64, 64);
    }

    static void drawVoiceGroupIcon(@Nonnull PoseStack poseStack, int x, int y) {
        Objects.requireNonNull(poseStack, "poseStack");
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        ResourceLocation voiceGroupIcon = Objects.requireNonNull(VOICE_GROUP_ICON);
        RenderSystem.setShaderTexture(0, voiceGroupIcon);
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0D);
        poseStack.scale(VOICE_ICON_SCALE, VOICE_ICON_SCALE, 1.0F);
        GuiComponent.blit(poseStack, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
        poseStack.popPose();
    }
}
