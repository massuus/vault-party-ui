package dev.massuus.vaultpartyui.client.screen;

import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.HEAD_SIZE;
import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.VOICE_ICON_SIZE;
import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.drawPlayerHead;
import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.drawVoiceGroupIcon;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.massuus.vaultpartyui.client.VoiceChatIntegration;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Mth;

final class PartyOnlinePanelRenderer {
    static final int ROW_HEIGHT = 12;
    static final int STAR_SIZE = 8;

    private static final int PANEL_TOP = 58;
    private static final int PANEL_HEIGHT = 246;

    private PartyOnlinePanelRenderer() {
    }

    static OnlinePanelRenderState render(
            @Nonnull PoseStack poseStack,
            Font font,
            int panelX,
            int panelWidth,
            int mouseX,
            int mouseY,
            List<OnlineRow> visiblePlayers,
            int scrollOffset,
            int selectedIndex,
            boolean partyLeader,
            Consumer<Component> queueTooltip
    ) {
        Objects.requireNonNull(poseStack, "poseStack");
        int textX = panelX + 10;
        int listTop = listTop();
        int listHeight = listHeight();
        int visibleRows = visibleRows();

        GuiComponent.fill(poseStack, panelX + 8, PANEL_TOP + 20, panelX + panelWidth - 8, PANEL_TOP + 42, 0xAA1A1A1A);
        String targetLabel = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.target").getString());
        font.draw(poseStack, targetLabel, textX, PANEL_TOP + 24, 0xA0A0A0);

        int maxOffset = Math.max(0, visiblePlayers.size() - visibleRows);
        int clampedOffset = Mth.clamp(scrollOffset, 0, maxOffset);
        int clampedSelected = visiblePlayers.isEmpty() ? -1 : Mth.clamp(selectedIndex, 0, visiblePlayers.size() - 1);

        int startIndex = clampedOffset;
        int endIndex = Math.min(visiblePlayers.size(), startIndex + visibleRows);

        GuiComponent.fill(poseStack, panelX + 8, listTop, panelX + panelWidth - 8, listTop + listHeight, 0x66111111);
        GuiComponent.fill(poseStack, panelX + 8, listTop, panelX + panelWidth - 8, listTop + 1, 0xFFE3C38C);

        if (visiblePlayers.isEmpty()) {
            String noMatching = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.no_matching").getString());
            font.draw(poseStack, noMatching, textX, listTop + 6, 0xA0A0A0);
            return new OnlinePanelRenderState(clampedOffset, clampedSelected);
        }

        int rowY = listTop + 4;
        for (int index = startIndex; index < endIndex; index++) {
            OnlineRow row = visiblePlayers.get(index);
            renderRow(poseStack, font, panelX, panelWidth, mouseX, mouseY, rowY, index, row, clampedSelected, partyLeader, queueTooltip);
            rowY += ROW_HEIGHT;
        }

        renderScrollHint(poseStack, panelX, panelWidth, listTop, listHeight, visiblePlayers.size(), clampedOffset, visibleRows);

        return new OnlinePanelRenderState(clampedOffset, clampedSelected);
    }

    static boolean isInsideList(double mouseX, double mouseY, int panelX, int panelWidth) {
        return mouseX >= panelX + 8 && mouseX <= panelX + panelWidth - 8 && mouseY >= listTop() && mouseY <= listTop() + listHeight();
    }

    static int listTop() {
        return PANEL_TOP + 48;
    }

    static int rowY(int listIndex, int scrollOffset) {
        return listTop() + (listIndex - scrollOffset) * ROW_HEIGHT + 4;
    }

    static int visibleRows() {
        int rowTopPadding = 4;
        int rowDrawHeight = ROW_HEIGHT - 2;
        return Math.max(1, 1 + Math.max(0, listHeight() - rowTopPadding - rowDrawHeight) / ROW_HEIGHT);
    }

    static boolean isFavoriteToggleHovered(double mouseX, double mouseY, int starX, int rowY) {
        return mouseX >= starX && mouseX <= starX + STAR_SIZE && mouseY >= rowY - 1 && mouseY <= rowY + STAR_SIZE;
    }

    private static void renderRow(
            @Nonnull PoseStack poseStack,
            Font font,
            int panelX,
            int panelWidth,
            int mouseX,
            int mouseY,
            int rowY,
            int index,
            OnlineRow row,
            int selectedIndex,
            boolean partyLeader,
            Consumer<Component> queueTooltip
    ) {
        OnlinePlayer player = row.player;
        boolean hovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 && mouseY >= rowY - 2 && mouseY < rowY + ROW_HEIGHT - 2;
        boolean selected = index == selectedIndex;
        int background = RowPresentation.backgroundColor(row.state, hovered, selected);

        GuiComponent.fill(poseStack, panelX + 10, rowY - 2, panelX + panelWidth - 10, rowY + ROW_HEIGHT - 2, background);
        int starX = panelX + 12;
        int starColor = row.favorite ? 0xFFD76A : 0xB0B0B0;
        boolean starHovered = isFavoriteToggleHovered(mouseX, mouseY, starX, rowY);
        if (starHovered) {
            starColor = 0xFFFFFF;
        }
        font.draw(poseStack, row.favorite ? "\u2605" : "\u2606", starX, rowY, starColor);

        int actionX = actionX(panelX, panelWidth);
        int headX = starX + STAR_SIZE + 4;
        boolean inVoiceGroup = VoiceChatIntegration.isPlayerInLocalVoiceGroup(player.id);
        int voiceIconX = headX;
        int voiceIconY = rowY - 1;
        if (inVoiceGroup) {
            drawVoiceGroupIcon(poseStack, voiceIconX, voiceIconY);
            headX += VOICE_ICON_SIZE + 3;
        }
        drawPlayerHead(poseStack, player.id, headX, rowY);

        int nameX = headX + HEAD_SIZE + 4;
        int nameWidth = Math.max(0, actionX - nameX - 8);
        String safeName = player.name == null ? "" : player.name;
        String displayName = Objects.requireNonNull(font.plainSubstrByWidth(safeName, nameWidth));
        font.draw(poseStack, displayName, nameX, rowY, RowPresentation.nameColor(row.state));

        Component action = RowPresentation.actionLabel(row, partyLeader);
        if (action != null) {
            String actionText = Objects.requireNonNull(action.getString());
            font.draw(poseStack, actionText, actionX, rowY, RowPresentation.actionColor(row.state));
        }

        boolean voiceIconHovered = inVoiceGroup && mouseX >= voiceIconX && mouseX <= voiceIconX + VOICE_ICON_SIZE && mouseY >= voiceIconY && mouseY <= voiceIconY + VOICE_ICON_SIZE;
        if (starHovered) {
            queueTooltip.accept(Objects.requireNonNull(RowPresentation.favoriteTooltip(row.favorite)));
        } else if (voiceIconHovered) {
            queueTooltip.accept(new TranslatableComponent("screen.vaultpartyui.tip_voice_group_member"));
        } else if (hovered) {
            Component hint = RowPresentation.tooltip(row, partyLeader);
            if (hint != null) {
                queueTooltip.accept(Objects.requireNonNull(hint));
            }
        }
    }

    private static int listHeight() {
        int panelBottom = PANEL_TOP + PANEL_HEIGHT;
        int top = listTop();
        return Math.max(0, panelBottom - top - 2);
    }

    static int actionX(int panelX, int panelWidth) {
        return panelX + panelWidth - 110;
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
        int trackTop = listTop + 4;
        int trackBottom = listTop + listHeight - 4;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        GuiComponent.fill(poseStack, trackX, trackTop, trackX + 2, trackBottom, 0x66333333);

        int thumbHeight = Math.max(12, trackHeight * visibleRows / rowCount);
        int maxOffset = Math.max(1, rowCount - visibleRows);
        int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxOffset;
        GuiComponent.fill(poseStack, trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFE3C38C);
    }
}
