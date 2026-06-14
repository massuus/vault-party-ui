package dev.massuus.vaultpartyui.client.screen;

import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.HEAD_SIZE;
import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.VOICE_ICON_SIZE;
import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.drawPlayerHead;
import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.drawVoiceGroupIcon;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.massuus.vaultpartyui.client.VoiceChatIntegration;
import iskallia.vault.world.data.VaultPartyData.Party;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

final class PartyPanelRenderer {
    private static final int PANEL_TOP = 58;
    private static final int PANEL_HEIGHT = 246;
    private static final int ROW_HEIGHT = 14;
    static final int ACTION_WIDTH = 96;

    private PartyPanelRenderer() {
    }

    static void render(
            @Nonnull PoseStack poseStack,
            Font font,
            int panelX,
            int panelWidth,
            int mouseX,
            int mouseY,
            @Nullable Party currentParty,
            List<UUID> members,
            List<OnlineRow> voiceRows,
            boolean partyLeader,
            Predicate<UUID> canInviteToVoice,
            Predicate<UUID> canJoinVoice,
            Consumer<Component> queueTooltip
    ) {
        Objects.requireNonNull(poseStack, "poseStack");
        int textX = panelX + 10;
        int textY = PANEL_TOP + 24;
        int panelBottom = PANEL_TOP + PANEL_HEIGHT - 8;

        if (currentParty == null) {
            String noParty = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.no_party").getString());
            font.draw(poseStack, noParty, textX, textY, 0xE0E0E0);
            return;
        }

        String membersText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.members").getString()) + ": " + members.size();
        font.draw(poseStack, membersText, textX, textY, 0xE0E0E0);
        textY += 16;

        for (UUID memberId : members) {
            if (textY + ROW_HEIGHT > panelBottom) {
                return;
            }
            renderPartyMemberRow(poseStack, font, panelX, panelWidth, memberId, textY, mouseX, mouseY, currentParty, canInviteToVoice, canJoinVoice, queueTooltip);
            textY += ROW_HEIGHT;
        }

        if (!voiceRows.isEmpty() && textY + 24 <= panelBottom) {
            textY += 8;
            font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.voice_not_party"), textX, textY, 0xE3C38C);
            textY += 14;
            for (OnlineRow row : voiceRows) {
                if (textY + ROW_HEIGHT > panelBottom) {
                    return;
                }
                renderVoiceNotPartyRow(poseStack, font, panelX, panelWidth, row, textY, mouseX, mouseY, partyLeader, queueTooltip);
                textY += ROW_HEIGHT;
            }
        }
    }

    static int actionX(int panelX, int panelWidth) {
        return panelX + panelWidth - ACTION_WIDTH - 14;
    }

    private static void renderPartyMemberRow(
            PoseStack poseStack,
            Font font,
            int panelX,
            int panelWidth,
            @Nullable UUID memberId,
            int rowY,
            int mouseX,
            int mouseY,
            Party currentParty,
            Predicate<UUID> canInviteToVoice,
            Predicate<UUID> canJoinVoice,
            Consumer<Component> queueTooltip
    ) {
        int rowLeft = panelX + 10;
        int rowRight = panelX + panelWidth - 10;
        boolean hovered = isRowHovered(mouseX, mouseY, rowLeft, rowRight, rowY);
        if (hovered) {
            GuiComponent.fill(poseStack, rowLeft, rowY - 2, rowRight, rowY + ROW_HEIGHT - 2, 0x663C3122);
        }

        int x = rowLeft + 2;
        boolean inVoiceGroup = VoiceChatIntegration.isPlayerInLocalVoiceGroup(memberId);
        if (inVoiceGroup) {
            drawVoiceGroupIcon(poseStack, x, rowY - 1);
            x += VOICE_ICON_SIZE + 3;
        }
        drawPlayerHead(poseStack, memberId, x, rowY);
        x += HEAD_SIZE + 4;

        String lineText = PartyMemberPresentation.line(currentParty, memberId, PartyPlayerLookup.getLocalPlayerId());
        int actionX = actionX(panelX, panelWidth);
        int nameWidth = Math.max(0, actionX - x - 6);
        font.draw(poseStack, Objects.requireNonNull(font.plainSubstrByWidth(Objects.requireNonNull(lineText), nameWidth)), x, rowY, PartyMemberPresentation.color(memberId));

        if (canInviteToVoice.test(memberId)) {
            font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.invite_to_voice"), actionX, rowY, 0xA0E0A0);
            if (isActionHovered(mouseX, mouseY, actionX, rowY)) {
                queueTooltip.accept(new TranslatableComponent("screen.vaultpartyui.tip_invite_to_voice"));
            }
        } else if (canJoinVoice.test(memberId)) {
            font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.join_voice_group"), actionX, rowY, 0xA0E0A0);
            if (isActionHovered(mouseX, mouseY, actionX, rowY)) {
                queueTooltip.accept(new TranslatableComponent("screen.vaultpartyui.tip_join_voice_group"));
            }
        }
    }

    private static void renderVoiceNotPartyRow(
            PoseStack poseStack,
            Font font,
            int panelX,
            int panelWidth,
            OnlineRow row,
            int rowY,
            int mouseX,
            int mouseY,
            boolean partyLeader,
            Consumer<Component> queueTooltip
    ) {
        int rowLeft = panelX + 10;
        int rowRight = panelX + panelWidth - 10;
        boolean hovered = isRowHovered(mouseX, mouseY, rowLeft, rowRight, rowY);
        if (hovered) {
            GuiComponent.fill(poseStack, rowLeft, rowY - 2, rowRight, rowY + ROW_HEIGHT - 2, 0x663C3122);
        }

        int x = rowLeft + 2;
        drawVoiceGroupIcon(poseStack, x, rowY - 1);
        x += VOICE_ICON_SIZE + 3;
        drawPlayerHead(poseStack, row.player.id, x, rowY);
        x += HEAD_SIZE + 4;

        int actionX = actionX(panelX, panelWidth);
        int nameWidth = Math.max(0, actionX - x - 6);
        font.draw(poseStack, Objects.requireNonNull(font.plainSubstrByWidth(Objects.requireNonNull(row.player.name), nameWidth)), x, rowY, RowPresentation.nameColor(row.state));

        Component action = row.state == RowState.INVITEABLE
                ? new TranslatableComponent("screen.vaultpartyui.invite_to_party")
                : RowPresentation.actionLabel(row, partyLeader);
        if (action != null) {
            font.draw(poseStack, Objects.requireNonNull(action.getString()), actionX, rowY, RowPresentation.actionColor(row.state));
            if (row.state == RowState.INVITEABLE && isActionHovered(mouseX, mouseY, actionX, rowY)) {
                queueTooltip.accept(new TranslatableComponent("screen.vaultpartyui.tip_invite_to_party"));
            }
        }
    }

    private static boolean isRowHovered(int mouseX, int mouseY, int rowLeft, int rowRight, int rowY) {
        return mouseX >= rowLeft && mouseX <= rowRight && mouseY >= rowY - 2 && mouseY < rowY + ROW_HEIGHT - 2;
    }

    private static boolean isActionHovered(int mouseX, int mouseY, int actionX, int rowY) {
        return mouseX >= actionX && mouseX <= actionX + ACTION_WIDTH && mouseY >= rowY - 2 && mouseY <= rowY + ROW_HEIGHT - 2;
    }
}
