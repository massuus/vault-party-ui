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
import net.minecraft.util.Mth;

final class PartyPanelRenderer {
    private static final int PANEL_TOP = 58;
    private static final int PANEL_HEIGHT = 246;
    static final int ROW_HEIGHT = 12;
    static final int ACTION_WIDTH = 96;

    private PartyPanelRenderer() {
    }

    static PartyPanelRenderState render(
            @Nonnull PoseStack poseStack,
            Font font,
            int panelX,
            int panelWidth,
            int mouseX,
            int mouseY,
            @Nullable Party currentParty,
            List<UUID> members,
            List<OnlineRow> voiceRows,
            int scrollOffset,
            boolean partyLeader,
            Predicate<UUID> canInviteToVoice,
            Predicate<UUID> canJoinVoice,
            Consumer<Component> queueTooltip
    ) {
        Objects.requireNonNull(poseStack, "poseStack");
        int textX = panelX + 10;
        int textY = PANEL_TOP + 24;
        int panelBottom = PANEL_TOP + PANEL_HEIGHT - 2;

        if (currentParty == null) {
            String noParty = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.no_party").getString());
            font.draw(poseStack, noParty, textX, textY, 0xE0E0E0);
            return new PartyPanelRenderState(0);
        }

        String membersText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.members").getString()) + ": " + members.size();
        font.draw(poseStack, membersText, textX, textY, 0xE0E0E0);
        textY = listTop();

        int totalRows = totalRows(members, voiceRows);
        int visibleRows = visibleRows();
        int maxOffset = Math.max(0, totalRows - visibleRows);
        int clampedOffset = Mth.clamp(scrollOffset, 0, maxOffset);
        int endRow = Math.min(totalRows, clampedOffset + visibleRows);

        for (int rowIndex = clampedOffset; rowIndex < endRow; rowIndex++) {
            if (textY + ROW_HEIGHT - 2 > panelBottom) {
                break;
            }
            renderVisibleRow(
                    poseStack,
                    font,
                    panelX,
                    panelWidth,
                    textX,
                    textY,
                    mouseX,
                    mouseY,
                    currentParty,
                    members,
                    voiceRows,
                    rowIndex,
                    partyLeader,
                    canInviteToVoice,
                    canJoinVoice,
                    queueTooltip
            );
            textY += ROW_HEIGHT;
        }

        renderScrollHint(poseStack, panelX, panelWidth, listTop(), listHeight(), totalRows, clampedOffset, visibleRows);
        return new PartyPanelRenderState(clampedOffset);
    }

    static int actionX(int panelX, int panelWidth) {
        return panelX + panelWidth - ACTION_WIDTH - 14;
    }

    static int listTop() {
        return PANEL_TOP + 40;
    }

    static int visibleRows() {
        int rowDrawHeight = ROW_HEIGHT - 2;
        return Math.max(1, 1 + Math.max(0, listHeight() - rowDrawHeight) / ROW_HEIGHT);
    }

    static int totalRows(List<UUID> members, List<OnlineRow> voiceRows) {
        return members.size() + (voiceRows.isEmpty() ? 0 : 1 + voiceRows.size());
    }

    static int rowY(int rowIndex, int scrollOffset) {
        return listTop() + (rowIndex - scrollOffset) * ROW_HEIGHT;
    }

    private static void renderVisibleRow(
            @Nonnull PoseStack poseStack,
            Font font,
            int panelX,
            int panelWidth,
            int textX,
            int rowY,
            int mouseX,
            int mouseY,
            Party currentParty,
            List<UUID> members,
            List<OnlineRow> voiceRows,
            int rowIndex,
            boolean partyLeader,
            Predicate<UUID> canInviteToVoice,
            Predicate<UUID> canJoinVoice,
            Consumer<Component> queueTooltip
    ) {
        if (rowIndex < members.size()) {
            renderPartyMemberRow(poseStack, font, panelX, panelWidth, members.get(rowIndex), rowY, mouseX, mouseY, currentParty, canInviteToVoice, canJoinVoice, queueTooltip);
            return;
        }

        if (voiceRows.isEmpty()) {
            return;
        }

        int voiceRowIndex = rowIndex - members.size();
        if (voiceRowIndex == 0) {
            font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.voice_not_party"), textX, rowY, 0xE3C38C);
            return;
        }

        int listIndex = voiceRowIndex - 1;
        if (listIndex >= 0 && listIndex < voiceRows.size()) {
            renderVoiceNotPartyRow(poseStack, font, panelX, panelWidth, voiceRows.get(listIndex), rowY, mouseX, mouseY, partyLeader, queueTooltip);
        }
    }

    private static void renderPartyMemberRow(
            @Nonnull PoseStack poseStack,
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
            @Nonnull PoseStack poseStack,
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
        if (row == null || row.player == null) {
            return;
        }

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

    private static int listHeight() {
        return Math.max(0, PANEL_TOP + PANEL_HEIGHT - 2 - listTop());
    }

    private static void renderScrollHint(
            @Nonnull PoseStack poseStack,
            int panelX,
            int panelWidth,
            int listTop,
            int listHeight,
            int rowCount,
            int scrollOffset,
            int visibleRows
    ) {
        if (rowCount <= visibleRows) {
            return;
        }

        int trackX = panelX + panelWidth - 11;
        int trackTop = listTop;
        int trackBottom = listTop + listHeight;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        GuiComponent.fill(poseStack, trackX, trackTop, trackX + 2, trackBottom, 0x66333333);

        int thumbHeight = Math.max(12, trackHeight * visibleRows / rowCount);
        int maxOffset = Math.max(1, rowCount - visibleRows);
        int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxOffset;
        GuiComponent.fill(poseStack, trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFE3C38C);
    }
}
