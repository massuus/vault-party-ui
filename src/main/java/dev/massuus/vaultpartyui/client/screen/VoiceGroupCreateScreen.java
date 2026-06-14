package dev.massuus.vaultpartyui.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.massuus.vaultpartyui.client.ClientTickEvents;
import dev.massuus.vaultpartyui.client.VoiceChatIntegration;
import dev.massuus.vaultpartyui.client.VoiceChatIntegration.VoiceGroupType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

public class VoiceGroupCreateScreen extends Screen {
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_HEIGHT = 132;
    private static final int BUTTON_HEIGHT = 20;

    @Nullable
    private final Screen parentScreen;
    private EditBox passwordBox;
    private Button typeButton;
    private VoiceGroupType selectedType = VoiceGroupType.OPEN;

    public VoiceGroupCreateScreen(@Nullable Screen parentScreen) {
        super(new TranslatableComponent("screen.vaultpartyui.voice_group_create_title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = panelX();
        int panelY = panelY();

        this.passwordBox = new EditBox(Objects.requireNonNull(this.font), panelX + 12, panelY + 46, PANEL_WIDTH - 24, 20, new TranslatableComponent("screen.vaultpartyui.voice_group_password"));
        this.passwordBox.setMaxLength(24);
        addRenderableWidget(Objects.requireNonNull(this.passwordBox));

        this.typeButton = addRenderableWidget(new Button(panelX + 12, panelY + 74, PANEL_WIDTH - 24, BUTTON_HEIGHT, Objects.requireNonNull(typeLabel()), button -> {
            this.selectedType = this.selectedType.next();
            updateTypeLabel();
        }));

        int bottomY = panelY + PANEL_HEIGHT - 28;
        addRenderableWidget(new Button(panelX + 12, bottomY, 112, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.voice_group_create_and_invite"), button -> createAndInvite()));
        addRenderableWidget(new Button(panelX + PANEL_WIDTH - 88, bottomY, 76, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.disband_confirm_no"), button -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.passwordBox != null) {
            this.passwordBox.tick();
        }
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(this.font, "font");
        renderBackground(poseStack);
        int panelX = panelX();
        int panelY = panelY();
        fill(poseStack, panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEE111111);
        fill(poseStack, panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, 0xFFE3C38C);
        fill(poseStack, panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFFE3C38C);
        fill(poseStack, panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, 0xFFE3C38C);
        fill(poseStack, panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFFE3C38C);

        GuiComponent.drawCenteredString(poseStack, Objects.requireNonNull(this.font), Objects.requireNonNull(this.title), this.width / 2, panelY + 8, 0xFFFFFF);
        Component name = new TranslatableComponent("screen.vaultpartyui.voice_group_name", "Vault Party");
        this.font.draw(poseStack, name, panelX + 12, panelY + 27, 0xE3C38C);
        super.render(poseStack, mouseX, mouseY, partialTick);
        if (this.passwordBox != null && this.passwordBox.getValue().isEmpty()) {
            this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.voice_group_password"), panelX + 16, panelY + 52, 0x777777);
        }
    }

    @Override
    public void onClose() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(this.parentScreen);
        }
    }

    private void createAndInvite() {
        String password = this.passwordBox == null ? "" : this.passwordBox.getValue();
        boolean sent = VoiceChatIntegration.createVoiceGroup(password, this.selectedType);
        Minecraft mc = this.minecraft;
        LocalPlayer player = mc == null ? null : mc.player;
        if (mc != null && player != null) {
            if (sent) {
                ClientTickEvents.queueInvitePartyToVoiceGroup();
                player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_creating_voice_group"), false);
                mc.setScreen(this.parentScreen);
            } else {
                player.displayClientMessage(new TranslatableComponent("screen.vaultpartyui.toast_voice_group_create_failed"), false);
            }
        }
    }

    private Component typeLabel() {
        return new TranslatableComponent("screen.vaultpartyui.voice_group_type", new TranslatableComponent(Objects.requireNonNull(this.selectedType.getLabelKey())));
    }

    private void updateTypeLabel() {
        if (this.typeButton != null) {
            this.typeButton.setMessage(Objects.requireNonNull(typeLabel()));
        }
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }
}
