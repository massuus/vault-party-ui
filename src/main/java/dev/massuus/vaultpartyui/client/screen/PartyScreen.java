package dev.massuus.vaultpartyui.client.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import iskallia.vault.client.data.ClientPartyData;
import iskallia.vault.client.data.ClientPartyInviteState;
import iskallia.vault.network.message.ServerboundPartyInviteResponseMessage;
import iskallia.vault.world.data.VaultPartyData.Party;
import iskallia.vault.client.data.ClientPartyData.PartyMember;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PartyScreen extends Screen {
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int PANEL_TOP = 100;
    private static final int PANEL_HEIGHT = 155;
    private static final int PANEL_PADDING = 10;
    private static final int ONLINE_ROW_HEIGHT = 14;
    private static final int VISIBLE_ONLINE_ROWS = 8;

    private final Screen parentScreen;

    private Party currentParty;
    private List<OnlinePlayer> onlinePlayers = Collections.emptyList();
    private EditBox targetBox;
    private Button createPartyButton;
    private Button leavePartyButton;
    private Button disbandPartyButton;
    private Button inviteNearbyButton;
    private Button inviteAllButton;
    private Button acceptInviteButton;
    private Button declineInviteButton;
    private int onlineScrollOffset;

    public PartyScreen(Screen parentScreen) {
        super(new TranslatableComponent("screen.vaultpartyui.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        rebuildState();

        int centerX = this.width / 2;
        int rowWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int rowX = centerX - rowWidth / 2;

        this.createPartyButton = addRenderableWidget(new Button(rowX, 24, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.create"), button -> sendPartyCommand("party create")));
        this.leavePartyButton = addRenderableWidget(new Button(rowX + BUTTON_WIDTH + BUTTON_GAP, 24, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.leave"), button -> sendPartyCommand("party leave")));
        this.disbandPartyButton = addRenderableWidget(new Button(rowX + (BUTTON_WIDTH + BUTTON_GAP) * 2, 24, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.disband"), button -> sendPartyCommand("party disband")));

        this.inviteNearbyButton = addRenderableWidget(new Button(centerX - BUTTON_WIDTH - (BUTTON_GAP / 2), 48, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_nearby"), button -> sendPartyCommand("party invite nearby")));
        this.inviteAllButton = addRenderableWidget(new Button(centerX + (BUTTON_GAP / 2), 48, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_all"), button -> sendPartyCommand("party invite all")));

        int inviteButtonWidth = 140;
        int inviteButtonX = centerX - inviteButtonWidth - 4;
        int declineButtonX = centerX + 4;
        this.acceptInviteButton = addRenderableWidget(new Button(inviteButtonX, 72, inviteButtonWidth, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.accept_invite"), button -> acceptPendingInvite()));
        this.declineInviteButton = addRenderableWidget(new Button(declineButtonX, 72, inviteButtonWidth, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.decline_invite"), button -> declinePendingInvite()));

        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int targetBoxWidth = panelWidth - PANEL_PADDING * 2;
        int rightPanelX = 20 + panelWidth + PANEL_PADDING;
        this.targetBox = new EditBox(this.font, rightPanelX + PANEL_PADDING, PANEL_TOP + 18, targetBoxWidth, 20, new TranslatableComponent("screen.vaultpartyui.target"));
        this.targetBox.setMaxLength(64);
        addRenderableWidget(this.targetBox);

        updateActionVisibility();
    }

    @Override
    public void tick() {
        super.tick();
        rebuildState();
        if (this.targetBox != null) {
            this.targetBox.tick();
        }
        updateActionVisibility();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);

        int leftPanelX = 20;
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;
        int panelBottom = PANEL_TOP + PANEL_HEIGHT;

        drawPanel(poseStack, leftPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);
        drawPanel(poseStack, rightPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);

        this.drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        this.drawCenteredString(poseStack, this.font, new TranslatableComponent("screen.vaultpartyui.party"), leftPanelX + panelWidth / 2, PANEL_TOP + 6, 0xE3C38C);
        this.drawCenteredString(poseStack, this.font, new TranslatableComponent("screen.vaultpartyui.players"), rightPanelX + panelWidth / 2, PANEL_TOP + 6, 0xE3C38C);

        if (ClientPartyInviteState.hasPendingInvite()) {
            String inviterName = ClientPartyInviteState.getInviterName();
            String inviteText = new TranslatableComponent("screen.vaultpartyui.pending_invite", inviterName == null ? "?" : inviterName).getString();
            int noticeWidth = this.font.width(inviteText) + 14;
            int noticeX = this.width / 2 - noticeWidth / 2;
            fill(poseStack, noticeX, panelBottom + 8, noticeX + noticeWidth, panelBottom + 26, 0xCC1D1D1D);
            fill(poseStack, noticeX, panelBottom + 8, noticeX + noticeWidth, panelBottom + 9, 0xFFE3C38C);
            this.font.draw(poseStack, inviteText, noticeX + 7, panelBottom + 13, 0xFFFFFF);
        }

        renderPartyPanel(poseStack, leftPanelX, panelWidth, mouseX, mouseY);
        renderOnlinePanel(poseStack, rightPanelX, panelWidth, mouseX, mouseY);

        super.render(poseStack, mouseX, mouseY, partialTick);

        if (this.targetBox != null) {
            this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.target").getString(), this.targetBox.x, this.targetBox.y - 10, 0xA0A0A0);
        }

        // Credit
        String credit = "Made by Massuus";
        int creditX = this.width - this.font.width(credit) - 8;
        int creditY = this.height - 18;
        this.font.draw(poseStack, credit, creditX, creditY, 0xAAAAAA);


    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check credit link click (bottom-right)
        String credit = "Made by Massuus";
        int creditX = this.width - this.font.width(credit) - 8;
        int creditY = this.height - 18;
        int creditW = this.font.width(credit);
        int creditH = 10;
        if (mouseX >= creditX && mouseX <= creditX + creditW && mouseY >= creditY && mouseY <= creditY + creditH) {
            try {
                openUrl("https://github.com/massuus/vault-hunters-party-ui");
            } catch (Exception ignored) {
            }
            return true;
        }
        if (this.targetBox != null && this.targetBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.targetBox);
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (handleOnlinePlayerClick(mouseX, mouseY)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (!isInsideOnlinePanel(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollDelta);
        }

        List<OnlinePlayer> visiblePlayers = filteredOnlinePlayers();
        int maxOffset = Math.max(0, visiblePlayers.size() - VISIBLE_ONLINE_ROWS);
        if (maxOffset == 0) {
            return true;
        }

        int direction = scrollDelta > 0 ? -1 : 1;
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset + direction, 0, maxOffset);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }

    private void rebuildState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            this.currentParty = null;
            this.onlinePlayers = Collections.emptyList();
            this.onlineScrollOffset = 0;
            return;
        }

        this.currentParty = ClientPartyData.getParty(minecraft.player.getUUID());
        this.onlinePlayers = gatherOnlinePlayers(minecraft.getConnection());

        List<OnlinePlayer> visiblePlayers = filteredOnlinePlayers();
        int maxOffset = Math.max(0, visiblePlayers.size() - VISIBLE_ONLINE_ROWS);
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset, 0, maxOffset);
    }

    private List<OnlinePlayer> gatherOnlinePlayers(ClientPacketListener connection) {
        if (connection == null) {
            return Collections.emptyList();
        }

        List<OnlinePlayer> players = new ArrayList<>();
        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            GameProfile profile = playerInfo.getProfile();
            if (profile != null && profile.getId() != null && profile.getName() != null) {
                players.add(new OnlinePlayer(profile.getId(), profile.getName()));
            }
        }

        players.sort(Comparator.comparing(player -> player.name.toLowerCase(Locale.ROOT)));
        return players;
    }

    private List<OnlinePlayer> filteredOnlinePlayers() {
        if (this.onlinePlayers.isEmpty()) {
            return Collections.emptyList();
        }

        String filter = this.targetBox == null ? "" : this.targetBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (filter.isEmpty()) {
            return this.onlinePlayers;
        }

        List<OnlinePlayer> filtered = new ArrayList<>();
        for (OnlinePlayer player : this.onlinePlayers) {
            if (player.name.toLowerCase(Locale.ROOT).contains(filter)) {
                filtered.add(player);
            }
        }
        return filtered;
    }

    private void sendPartyCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            if (minecraft.player != null) {
                minecraft.player.chat("/" + command);
            }
        }
    }

    private void acceptPendingInvite() {
        if (!ClientPartyInviteState.hasPendingInvite()) {
            return;
        }

        UUID inviteId = ClientPartyInviteState.getInviteId();
        if (inviteId != null) {
            ServerboundPartyInviteResponseMessage.send(inviteId, true);
            ClientPartyInviteState.clearInvite();
        }
    }

    private void declinePendingInvite() {
        if (!ClientPartyInviteState.hasPendingInvite()) {
            return;
        }

        UUID inviteId = ClientPartyInviteState.getInviteId();
        if (inviteId != null) {
            ServerboundPartyInviteResponseMessage.send(inviteId, false);
            ClientPartyInviteState.clearInvite();
        }
    }

    private void updateInviteButtons() {
        boolean hasInvite = ClientPartyInviteState.hasPendingInvite() && this.currentParty == null;
        if (this.acceptInviteButton != null) {
            this.acceptInviteButton.visible = hasInvite;
        }
        if (this.declineInviteButton != null) {
            this.declineInviteButton.visible = hasInvite;
        }
    }

    private void updateActionVisibility() {
        boolean inParty = this.currentParty != null;
        int centerX = this.width / 2;

        if (this.createPartyButton != null) {
            this.createPartyButton.visible = !inParty;
            this.createPartyButton.x = centerX - (BUTTON_WIDTH / 2);
        }
        if (this.leavePartyButton != null) {
            this.leavePartyButton.visible = inParty;
        }
        if (this.disbandPartyButton != null) {
            this.disbandPartyButton.visible = inParty;
        }

        // Keep the first action row centered for the currently visible controls.
        if (inParty && this.leavePartyButton != null && this.disbandPartyButton != null) {
            int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
            int rowX = centerX - rowWidth / 2;
            this.leavePartyButton.x = rowX;
            this.disbandPartyButton.x = rowX + BUTTON_WIDTH + BUTTON_GAP;
        }

        if (this.inviteNearbyButton != null) {
            this.inviteNearbyButton.visible = inParty;
        }
        if (this.inviteAllButton != null) {
            this.inviteAllButton.visible = inParty;
        }

        // Keep the second action row centered as a pair.
        if (this.inviteNearbyButton != null && this.inviteAllButton != null) {
            int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
            int rowX = centerX - rowWidth / 2;
            this.inviteNearbyButton.x = rowX;
            this.inviteAllButton.x = rowX + BUTTON_WIDTH + BUTTON_GAP;
        }

        updateInviteButtons();
    }

    private void renderPartyPanel(PoseStack poseStack, int panelX, int panelWidth, int mouseX, int mouseY) {
        int textX = panelX + 10;
        int textY = PANEL_TOP + 24;

        if (this.currentParty == null) {
            this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.no_party").getString(), textX, textY, 0xE0E0E0);
            return;
        }

        UUID leaderId = this.currentParty.getLeader();
        List<UUID> members = this.currentParty.getMembers();
        this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.members").getString() + ": " + members.size(), textX, textY, 0xE0E0E0);
        textY += 16;

        for (UUID memberId : members) {
            String memberName = resolvePlayerName(memberId);
            PartyMember cachedMember = ClientPartyData.getCachedMember(memberId);
            StringBuilder line = new StringBuilder(memberName);
            if (memberId.equals(leaderId)) {
                line.append(" [").append(new TranslatableComponent("screen.vaultpartyui.leader").getString()).append("]");
            }
            if (memberId.equals(getLocalPlayerId())) {
                line.append(" [").append(new TranslatableComponent("screen.vaultpartyui.self").getString()).append("]");
            }

            int color = 0xFFFFFF;
            if (cachedMember != null) {
                if (cachedMember.status != PartyMember.Status.NORMAL) {
                    line.append(" - ").append(cachedMember.status.name());
                }
                line.append(" - ").append(formatHealth(cachedMember.healthPts)).append(" HP");
                color = statusColor(cachedMember.status);
            }

            this.font.draw(poseStack, line.toString(), textX, textY, color);
            textY += 14;
        }
    }

    private void renderOnlinePanel(PoseStack poseStack, int panelX, int panelWidth, int mouseX, int mouseY) {
        int textX = panelX + 10;
        int boxWidth = panelWidth - 20;
        int listTop = PANEL_TOP + 48;
        int listHeight = VISIBLE_ONLINE_ROWS * ONLINE_ROW_HEIGHT + 6;

        fill(poseStack, panelX + 8, PANEL_TOP + 20, panelX + panelWidth - 8, PANEL_TOP + 42, 0xAA1A1A1A);
        this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.target").getString(), textX, PANEL_TOP + 24, 0xA0A0A0);

        List<OnlinePlayer> visiblePlayers = filteredOnlinePlayers();
        int maxOffset = Math.max(0, visiblePlayers.size() - VISIBLE_ONLINE_ROWS);
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset, 0, maxOffset);

        int startIndex = this.onlineScrollOffset;
        int endIndex = Math.min(visiblePlayers.size(), startIndex + VISIBLE_ONLINE_ROWS);

        fill(poseStack, panelX + 8, listTop, panelX + panelWidth - 8, listTop + listHeight, 0x66111111);
        fill(poseStack, panelX + 8, listTop, panelX + panelWidth - 8, listTop + 1, 0xFFE3C38C);

        if (visiblePlayers.isEmpty()) {
            this.font.draw(poseStack, "No matching players.", textX, listTop + 6, 0xA0A0A0);
            return;
        }

        int rowY = listTop + 4;
        for (int index = startIndex; index < endIndex; index++) {
            OnlinePlayer player = visiblePlayers.get(index);
            boolean hovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 && mouseY >= rowY - 2 && mouseY < rowY + ONLINE_ROW_HEIGHT - 2;
            int background = hovered ? 0x663C3122 : 0x00000000;
            
            // draw player name and per-row action (invite/remove)
            this.fill(poseStack, panelX + 10, rowY - 2, panelX + panelWidth - 10, rowY + ONLINE_ROW_HEIGHT - 2, background);
            this.font.draw(poseStack, player.name, panelX + 12, rowY, 0xFFFFFF);

            // skip drawing actions for self
            if (!player.id.equals(getLocalPlayerId())) {
                int actionX = panelX + panelWidth - 84;
                if (this.currentParty == null) {
                    // Invite button
                    this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.invite").getString(), actionX, rowY, 0xA0E0A0);
                } else if (isPartyLeader()) {
                    // Remove button
                    this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.remove").getString(), actionX, rowY, 0xE0A0A0);
                }
            }

            rowY += ONLINE_ROW_HEIGHT;
        }
    }

    private boolean isInsideOnlinePanel(double mouseX, double mouseY) {
        int leftPanelX = 20;
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;
        int listTop = PANEL_TOP + 48;
        int listHeight = VISIBLE_ONLINE_ROWS * ONLINE_ROW_HEIGHT + 6;
        return mouseX >= rightPanelX + 8 && mouseX <= rightPanelX + panelWidth - 8 && mouseY >= listTop && mouseY <= listTop + listHeight;
    }

    private boolean handleOnlinePlayerClick(double mouseX, double mouseY) {
        if (!isInsideOnlinePanel(mouseX, mouseY)) {
            return false;
        }

        int panelX = 20 + (this.width - 40 - PANEL_PADDING) / 2 + PANEL_PADDING;
        int listTop = PANEL_TOP + 48;
        int relativeY = (int)mouseY - listTop - 4;
        int index = this.onlineScrollOffset + (relativeY / ONLINE_ROW_HEIGHT);
        List<OnlinePlayer> visiblePlayers = filteredOnlinePlayers();
        if (index < 0 || index >= visiblePlayers.size()) {
            return false;
        }

        OnlinePlayer player = visiblePlayers.get(index);
        if (player.id.equals(getLocalPlayerId())) {
            return false;
        }

        int actionX = panelX + ((this.width - 40 - PANEL_PADDING) / 2) - 84;
        int actionY = listTop + (index - this.onlineScrollOffset) * ONLINE_ROW_HEIGHT + 4;
        if (mouseX >= actionX && mouseX <= actionX + 70 && mouseY >= actionY && mouseY <= actionY + ONLINE_ROW_HEIGHT - 2) {
            if (this.currentParty == null) {
                sendPartyCommand("party invite " + player.name);
            } else if (isPartyLeader()) {
                sendPartyCommand("party remove " + player.name);
            }
            return true;
        }

        return false;
    }

    private boolean isPartyLeader() {
        if (this.currentParty == null) return false;
        UUID leader = this.currentParty.getLeader();
        return leader != null && leader.equals(getLocalPlayerId());
    }

    private UUID getLocalPlayerId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        return minecraft.player.getUUID();
    }

    private String resolvePlayerName(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || playerId == null) return "?";
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return "?";
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null && playerId.equals(profile.getId())) {
                return profile.getName();
            }
        }
        return "?";
    }

    private String formatHealth(float hp) {
        return String.format(Locale.ROOT, "%.1f", hp);
    }

    private int statusColor(PartyMember.Status status) {
        if (status == null) return 0xFFFFFF;
        switch (status) {
            case DEAD:
                return 0xFF5555;
            case DOWNED:
                return 0xFFAA00;
            default:
                return 0xFFFFFF;
        }
    }

    private void drawPanel(PoseStack poseStack, int x, int y, int width, int height) {
        fill(poseStack, x, y, x + width, y + height, 0xAA111111);
        fill(poseStack, x, y, x + width, y + 1, 0xFFE3C38C);
    }

    private void openUrl(String url) {
        try {
            java.awt.Desktop desktop = java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(new java.net.URI(url));
                return;
            }
        } catch (Exception ignored) {
        }

        // Fallbacks
        try {
            String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception ignored) {
        }
    }

    private static final class OnlinePlayer {
        final UUID id;
        final String name;

        OnlinePlayer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
