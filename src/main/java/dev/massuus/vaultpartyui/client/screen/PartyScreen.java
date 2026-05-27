package dev.massuus.vaultpartyui.client.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.lwjgl.glfw.GLFW;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.massuus.vaultpartyui.client.ClientFavoritePlayers;
import dev.massuus.vaultpartyui.client.ClientPartySettings;
import iskallia.vault.client.data.ClientPartyData;
import iskallia.vault.client.data.ClientPartyData.PartyMember;
import iskallia.vault.client.data.ClientPartyInviteState;
import iskallia.vault.network.message.ServerboundPartyInviteResponseMessage;
import iskallia.vault.world.data.VaultPartyData.Party;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PartyScreen extends Screen {
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int PANEL_TOP = 58;
    private static final int PANEL_HEIGHT = 246;
    private static final int PANEL_PADDING = 10;
    private static final int ONLINE_ROW_HEIGHT = 14;
    private static final int VISIBLE_ONLINE_ROWS = 15;
    private static final int HEAD_SIZE = 8;
    private static final long INVITE_COOLDOWN_MS = 8000L;
    private static final int STATE_REFRESH_INTERVAL_TICKS = 4;
    private static final int STAR_SIZE = 8;
    private static final String MOD_VERSION = "VPUI v1.3";
    private static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods/vault-party-ui";
    private static final String GITHUB_URL = "https://github.com/massuus/vault-party-ui";

    private final Screen parentScreen;

    private Party currentParty;
    private List<OnlinePlayer> onlinePlayers = Collections.emptyList();
    private EditBox targetBox;
    private Button createPartyButton;
    private Button leavePartyButton;
    private Button disbandPartyButton;
    private Button inviteNearbyButton;
    private Button inviteAllButton;
    private Button inviteFavoritesButton;
    private Button acceptInviteButton;
    private Button declineInviteButton;
    private Button autoAcceptToggleButton;
    private int onlineScrollOffset;
    private int selectedOnlineIndex = -1;
    private int stateRefreshTicks;
    private final Map<UUID, Long> inviteCooldownUntilMs = new HashMap<>();
    private final List<UiToast> toasts = new ArrayList<>();

    public PartyScreen(Screen parentScreen) {
        super(new TranslatableComponent("screen.vaultpartyui.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        Objects.requireNonNull(this.font, "font");
        rebuildState();

        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int leftPanelX = 20;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;
        int leftRowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int rightRowWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int leftRowX = leftPanelX + panelWidth / 2 - leftRowWidth / 2;
        int rightRowX = rightPanelX + panelWidth / 2 - rightRowWidth / 2;

        int createX = this.width / 2 - BUTTON_WIDTH / 2;
        this.createPartyButton = addRenderableWidget(new Button(createX, 34, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.create"), button -> sendPartyCommand("party create")));
        this.leavePartyButton = addRenderableWidget(new Button(leftRowX, 34, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.leave"), button -> sendPartyCommand("party leave")));
        this.disbandPartyButton = addRenderableWidget(new Button(leftRowX + BUTTON_WIDTH + BUTTON_GAP, 34, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.disband"), button -> confirmDisbandParty()));

        this.inviteNearbyButton = addRenderableWidget(new Button(rightRowX, 34, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_nearby"), button -> sendPartyCommand("party invite nearby")));
        this.inviteAllButton = addRenderableWidget(new Button(rightRowX + BUTTON_WIDTH + BUTTON_GAP, 34, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_all"), button -> sendPartyCommand("party invite all")));
        this.inviteFavoritesButton = addRenderableWidget(new Button(rightRowX + (BUTTON_WIDTH + BUTTON_GAP) * 2, 34, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_favorites"), button -> inviteFavoritePlayers()));

        int inviteButtonWidth = 140;
        // Position invite accept/decline to the left/right of the centered Create Party button, same vertical level
        this.acceptInviteButton = addRenderableWidget(new Button(createX - inviteButtonWidth - BUTTON_GAP, 34, inviteButtonWidth, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.accept_invite"), button -> acceptPendingInvite()));
        this.declineInviteButton = addRenderableWidget(new Button(createX + BUTTON_WIDTH + BUTTON_GAP, 34, inviteButtonWidth, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.decline_invite"), button -> declinePendingInvite()));
        this.autoAcceptToggleButton = addRenderableWidget(new Button(20, this.height - BUTTON_HEIGHT - 8, 190, BUTTON_HEIGHT, Objects.requireNonNull(autoAcceptToggleLabel()), button -> {
            ClientPartySettings.cycleAutoAcceptMode();
            updateAutoAcceptToggleLabel();
            pushToast(new TranslatableComponent(Objects.requireNonNull(ClientPartySettings.getAutoAcceptMode().getToastKey())), 0xE3C38C);
        }));

        int targetBoxWidth = panelWidth - PANEL_PADDING * 2;
        this.targetBox = new EditBox(Objects.requireNonNull(this.font), rightPanelX + PANEL_PADDING, PANEL_TOP + 18, targetBoxWidth, 20, new TranslatableComponent("screen.vaultpartyui.target"));
        this.targetBox.setMaxLength(64);
        addRenderableWidget(Objects.requireNonNull(this.targetBox));

        updateActionVisibility();
    }

    @Override
    public void tick() {
        super.tick();
        this.stateRefreshTicks++;
        if (this.stateRefreshTicks >= STATE_REFRESH_INTERVAL_TICKS) {
            this.stateRefreshTicks = 0;
            rebuildState();
        }

        pruneTransientState();

        if (this.targetBox != null) {
            this.targetBox.tick();
        }
        updateActionVisibility();
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Objects.requireNonNull(poseStack, "poseStack");
        final Font font = Objects.requireNonNull(this.font, "font");
        this.renderBackground(poseStack);

        int leftPanelX = 20;
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;
        int panelBottom = PANEL_TOP + PANEL_HEIGHT;

        drawPanel(poseStack, leftPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);
        drawPanel(poseStack, rightPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);

        GuiComponent.drawCenteredString(poseStack, font, Objects.requireNonNull(this.title), this.width / 2, 8, 0xFFFFFF);
        GuiComponent.drawCenteredString(poseStack, font, new TranslatableComponent("screen.vaultpartyui.party"), leftPanelX + panelWidth / 2, PANEL_TOP + 6, 0xE3C38C);
        GuiComponent.drawCenteredString(poseStack, font, new TranslatableComponent("screen.vaultpartyui.players"), rightPanelX + panelWidth / 2, PANEL_TOP + 6, 0xE3C38C);

        if (ClientPartyInviteState.hasPendingInvite()) {
            String inviterName = ClientPartyInviteState.getInviterName();
            String inviteText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.pending_invite", inviterName == null ? "?" : inviterName).getString());
            int noticeWidth = font.width(inviteText) + 14;
            int noticeX = this.width / 2 - noticeWidth / 2;
            fill(poseStack, noticeX, panelBottom + 8, noticeX + noticeWidth, panelBottom + 26, 0xCC1D1D1D);
            fill(poseStack, noticeX, panelBottom + 8, noticeX + noticeWidth, panelBottom + 9, 0xFFE3C38C);
            font.draw(poseStack, inviteText, noticeX + 7, panelBottom + 13, 0xFFFFFF);
        }

        renderPartyPanel(poseStack, leftPanelX, panelWidth, mouseX, mouseY);
        renderOnlinePanel(poseStack, rightPanelX, panelWidth, mouseX, mouseY);

        super.render(poseStack, mouseX, mouseY, partialTick);

        if (this.targetBox != null) {
            String targetLabel = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.target").getString());
            font.draw(poseStack, targetLabel, this.targetBox.x, this.targetBox.y - 10, 0xA0A0A0);
        }

        // Version and credit (clickable) - right aligned
        String version = Objects.requireNonNull(MOD_VERSION);
        String sep = " | ";
        String credit = "Made by Massuus";
        int versionW = font.width(version);
        int sepW = font.width(sep);
        int creditW = font.width(credit);
        int totalW = versionW + sepW + creditW;
        int rightPadding = 8;
        int versionX = this.width - rightPadding - totalW;
        int versionY = this.height - 18;
        boolean versionHovered = mouseX >= versionX && mouseX <= versionX + versionW && mouseY >= versionY && mouseY <= versionY + font.lineHeight;
        int versionColor = versionHovered ? 0xFFFFFF : 0xAAAAAA;
        font.draw(poseStack, version, versionX, versionY, versionColor);
        if (versionHovered) {
            int underlineY = versionY + font.lineHeight;
            fill(poseStack, versionX, underlineY, versionX + versionW, underlineY + 1, versionColor);
        }

        int sepX = versionX + versionW;
        font.draw(poseStack, Objects.requireNonNull(sep), sepX, versionY, 0xAAAAAA);

        int creditX = sepX + sepW;
        int creditY = versionY;
        boolean creditHovered = mouseX >= creditX && mouseX <= creditX + creditW && mouseY >= creditY && mouseY <= creditY + font.lineHeight;
        int creditColor = creditHovered ? 0xFFFFFF : 0xAAAAAA;
        font.draw(poseStack, Objects.requireNonNull(credit), creditX, creditY, creditColor);
        if (creditHovered) {
            int underlineY = creditY + font.lineHeight;
            fill(poseStack, creditX, underlineY, creditX + creditW, underlineY + 1, creditColor);
            // tooltip
            String tip = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.credit_tooltip").getString());
            int tipX = Math.min(this.width - 10 - font.width(tip), (int)mouseX + 8);
            int tipY = creditY - font.lineHeight - 6;
            fill(poseStack, tipX - 4, tipY - 2, tipX + font.width(tip) + 4, tipY + font.lineHeight + 2, 0xCC111111);
            font.drawShadow(poseStack, tip, tipX, tipY, 0xFFFFFF);
        }

        renderToasts(poseStack);


    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check version and credit clicks (bottom-right)
        String version = MOD_VERSION;
        String sep = " | ";
        String credit = "Made by Massuus";
        int versionW = this.font.width(version);
        int sepW = this.font.width(sep);
        int creditW = this.font.width(credit);
        int totalW = versionW + sepW + creditW;
        int rightPadding = 8;
        int versionX = this.width - rightPadding - totalW;
        int versionY = this.height - 18;
        int versionH = this.font.lineHeight;
        if (mouseX >= versionX && mouseX <= versionX + versionW && mouseY >= versionY && mouseY <= versionY + versionH) {
            try {
                openUrl(CURSEFORGE_URL);
            } catch (Exception ignored) {
            }
            return true;
        }

        int sepX = versionX + versionW;
        int creditX = sepX + sepW;
        int creditY = versionY;
        int creditH = this.font.lineHeight;
        if (mouseX >= creditX && mouseX <= creditX + creditW && mouseY >= creditY && mouseY <= creditY + creditH) {
            try {
                openUrl(GITHUB_URL);
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

        List<OnlineRow> visiblePlayers = filteredOnlineRows();
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
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.targetBox != null && this.targetBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        List<OnlineRow> rows = filteredOnlineRows();
        if (rows.isEmpty()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (this.selectedOnlineIndex < 0) {
                this.selectedOnlineIndex = 0;
            } else {
                this.selectedOnlineIndex = Math.min(rows.size() - 1, this.selectedOnlineIndex + 1);
            }
            ensureSelectedRowVisible(rows.size());
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (this.selectedOnlineIndex < 0) {
                this.selectedOnlineIndex = rows.size() - 1;
            } else {
                this.selectedOnlineIndex = Math.max(0, this.selectedOnlineIndex - 1);
            }
            ensureSelectedRowVisible(rows.size());
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.selectedOnlineIndex >= 0 && this.selectedOnlineIndex < rows.size()) {
                performPrimaryRowAction(rows.get(this.selectedOnlineIndex));
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void rebuildState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            this.currentParty = null;
            this.onlinePlayers = Collections.emptyList();
            this.onlineScrollOffset = 0;
            this.selectedOnlineIndex = -1;
            return;
        }

        this.currentParty = ClientPartyData.getParty(minecraft.player.getUUID());
        this.onlinePlayers = gatherOnlinePlayers(minecraft.getConnection());

        List<OnlineRow> visiblePlayers = filteredOnlineRows();
        int maxOffset = Math.max(0, visiblePlayers.size() - VISIBLE_ONLINE_ROWS);
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset, 0, maxOffset);
        if (visiblePlayers.isEmpty()) {
            this.selectedOnlineIndex = -1;
        } else {
            this.selectedOnlineIndex = Mth.clamp(this.selectedOnlineIndex, 0, visiblePlayers.size() - 1);
        }
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

    private List<OnlineRow> filteredOnlineRows() {
        return PartyRosterService.buildRows(
                filteredOnlinePlayers(),
            FilterMode.ALL,
                this.currentParty,
                getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );
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

    private void confirmDisbandParty() {
        Minecraft mc = this.minecraft;
        if (mc == null) {
            return;
        }

        mc.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                sendPartyCommand("party disband");
            }
            if (mc != null) {
                mc.setScreen(this);
            }
        },
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_title"),
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_message"),
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_yes"),
        new TranslatableComponent("screen.vaultpartyui.disband_confirm_no")));
    }

    private void updateInviteButtons() {
        boolean hasInvite = ClientPartyInviteState.hasPendingInvite()
                && this.currentParty == null
                && !ClientPartySettings.shouldAutoAcceptInvite(Minecraft.getInstance(), ClientPartyInviteState.getInviterName());
        if (this.acceptInviteButton != null) {
            this.acceptInviteButton.visible = hasInvite;
        }
        if (this.declineInviteButton != null) {
            this.declineInviteButton.visible = hasInvite;
        }
    }

    private void updateActionVisibility() {
        boolean inParty = isLocalPlayerInParty();
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int leftPanelX = 20;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;

        if (this.createPartyButton != null) {
            this.createPartyButton.visible = !inParty;
            int createX = this.width / 2 - (BUTTON_WIDTH / 2);
            this.createPartyButton.x = createX;
            // keep accept/decline positioned relative to create button
            if (this.acceptInviteButton != null) {
                this.acceptInviteButton.x = createX - (this.acceptInviteButton.getWidth() + BUTTON_GAP);
                this.acceptInviteButton.y = this.createPartyButton.y;
            }
            if (this.declineInviteButton != null) {
                this.declineInviteButton.x = createX + BUTTON_WIDTH + BUTTON_GAP;
                this.declineInviteButton.y = this.createPartyButton.y;
            }
        }
        if (this.leavePartyButton != null) {
            this.leavePartyButton.visible = inParty;
        }
        if (this.disbandPartyButton != null) {
            this.disbandPartyButton.visible = inParty;
        }

        if (inParty && this.leavePartyButton != null && this.disbandPartyButton != null) {
            int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
            int rowX = leftPanelX + panelWidth / 2 - rowWidth / 2;
            this.leavePartyButton.x = rowX;
            this.disbandPartyButton.x = rowX + BUTTON_WIDTH + BUTTON_GAP;
        }

        if (this.inviteNearbyButton != null) {
            this.inviteNearbyButton.visible = inParty;
        }
        if (this.inviteAllButton != null) {
            this.inviteAllButton.visible = inParty;
        }
        if (this.inviteFavoritesButton != null) {
            this.inviteFavoritesButton.visible = inParty;
            this.inviteFavoritesButton.active = inParty && hasInviteableFavorites();
        }
        if (this.autoAcceptToggleButton != null) {
            this.autoAcceptToggleButton.visible = true;
            this.autoAcceptToggleButton.active = true;
            this.autoAcceptToggleButton.x = 20;
            this.autoAcceptToggleButton.y = this.height - BUTTON_HEIGHT - 8;
        }
        if (this.inviteNearbyButton != null && this.inviteAllButton != null && this.inviteFavoritesButton != null) {
            int rowWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
            int rowX = rightPanelX + panelWidth / 2 - rowWidth / 2;
            this.inviteNearbyButton.x = rowX;
            this.inviteAllButton.x = rowX + BUTTON_WIDTH + BUTTON_GAP;
            this.inviteFavoritesButton.x = rowX + (BUTTON_WIDTH + BUTTON_GAP) * 2;
        }

        updateInviteButtons();
        updateAutoAcceptToggleLabel();
    }

    private Component autoAcceptToggleLabel() {
        return Objects.requireNonNull(new TranslatableComponent(Objects.requireNonNull(ClientPartySettings.getAutoAcceptMode().getLabelKey())));
    }

    private void updateAutoAcceptToggleLabel() {
        if (this.autoAcceptToggleButton != null) {
            this.autoAcceptToggleButton.setMessage(Objects.requireNonNull(autoAcceptToggleLabel()));
        }
    }

    private void renderPartyPanel(PoseStack poseStack, int panelX, int panelWidth, int mouseX, int mouseY) {
        Objects.requireNonNull(poseStack, "poseStack");
        final Font font = Objects.requireNonNull(this.font);
        int textX = panelX + 10;
        int textY = PANEL_TOP + 24;

        if (this.currentParty == null) {
            String noParty = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.no_party").getString());
            font.draw(poseStack, noParty, textX, textY, 0xE0E0E0);
            return;
        }

        UUID leaderId = this.currentParty.getLeader();
        List<UUID> members = this.currentParty.getMembers();
        String membersText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.members").getString()) + ": " + members.size();
        font.draw(poseStack, membersText, textX, textY, 0xE0E0E0);
        textY += 16;

        for (UUID memberId : members) {
            String memberName = resolvePlayerName(memberId);
            PartyMember cachedMember = ClientPartyData.getCachedMember(memberId);
            StringBuilder line = new StringBuilder(memberName);
            if (memberId.equals(leaderId)) {
                line.append(" [").append(new TranslatableComponent("screen.vaultpartyui.leader").getString()).append("]");
            }
                if (memberId.equals(getLocalPlayerId())) {
                line.append(" [").append(Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.self").getString())).append("]");
            }

            int color = 0xFFFFFF;
            if (cachedMember != null) {
                if (cachedMember.status != PartyMember.Status.NORMAL) {
                    line.append(" - ").append(cachedMember.status.name());
                }
                line.append(" - ").append(formatHealth(cachedMember.healthPts)).append(" \u2764");
                color = statusColor(cachedMember.status);
            }

            drawPlayerHead(poseStack, memberId, textX, textY);
            String lineText = Objects.requireNonNull(String.valueOf(line.toString()));
            font.draw(poseStack, lineText, textX + HEAD_SIZE + 4, textY, color);
            textY += 14;
        }
    }

    private void renderOnlinePanel(PoseStack poseStack, int panelX, int panelWidth, int mouseX, int mouseY) {
        Objects.requireNonNull(poseStack, "poseStack");
        final Font font = Objects.requireNonNull(this.font);
        int textX = panelX + 10;
        int listTop = PANEL_TOP + 48;
        int panelBottom = PANEL_TOP + PANEL_HEIGHT;
        int listHeight = Math.min(VISIBLE_ONLINE_ROWS * ONLINE_ROW_HEIGHT + 6, Math.max(0, panelBottom - listTop - 8));

        fill(poseStack, panelX + 8, PANEL_TOP + 20, panelX + panelWidth - 8, PANEL_TOP + 42, 0xAA1A1A1A);
        String targetLabel = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.target").getString());
        font.draw(poseStack, targetLabel, textX, PANEL_TOP + 24, 0xA0A0A0);

        List<OnlineRow> visiblePlayers = filteredOnlineRows();
        int maxOffset = Math.max(0, visiblePlayers.size() - VISIBLE_ONLINE_ROWS);
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset, 0, maxOffset);
        if (visiblePlayers.isEmpty()) {
            this.selectedOnlineIndex = -1;
        } else {
            this.selectedOnlineIndex = Mth.clamp(this.selectedOnlineIndex, 0, visiblePlayers.size() - 1);
        }

        int startIndex = this.onlineScrollOffset;
        int endIndex = Math.min(visiblePlayers.size(), startIndex + VISIBLE_ONLINE_ROWS);

        fill(poseStack, panelX + 8, listTop, panelX + panelWidth - 8, listTop + listHeight, 0x66111111);
        fill(poseStack, panelX + 8, listTop, panelX + panelWidth - 8, listTop + 1, 0xFFE3C38C);

        if (visiblePlayers.isEmpty()) {
            String noMatching = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.no_matching").getString());
            font.draw(poseStack, noMatching, textX, listTop + 6, 0xA0A0A0);
            return;
        }

        int rowY = listTop + 4;
        for (int index = startIndex; index < endIndex; index++) {
            OnlineRow row = visiblePlayers.get(index);
            OnlinePlayer player = row.player;
            boolean hovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 && mouseY >= rowY - 2 && mouseY < rowY + ONLINE_ROW_HEIGHT - 2;
            boolean selected = index == this.selectedOnlineIndex;
            int background = RowPresentation.backgroundColor(row.state, hovered, selected);
            
            // draw player name and per-row action (invite/remove)
            GuiComponent.fill(poseStack, panelX + 10, rowY - 2, panelX + panelWidth - 10, rowY + ONLINE_ROW_HEIGHT - 2, background);
            int starX = panelX + 12;
            int starColor = row.favorite ? 0xFFD76A : 0xB0B0B0;
            boolean starHovered = isFavoriteToggleHovered(mouseX, mouseY, starX, rowY);
            if (starHovered) {
                starColor = 0xFFFFFF;
            }
            font.draw(poseStack, row.favorite ? "\u2605" : "\u2606", starX, rowY, starColor);

            drawPlayerHead(poseStack, player.id, starX + STAR_SIZE + 4, rowY);

            int actionX = panelX + panelWidth - 110;
            int nameX = starX + STAR_SIZE + 4 + HEAD_SIZE + 4;
            int nameWidth = Math.max(0, actionX - nameX - 8);
            String safeName = player.name == null ? "" : player.name;
            String displayName = font.plainSubstrByWidth(safeName, nameWidth);
            font.draw(poseStack, Objects.requireNonNull(String.valueOf(displayName)), nameX, rowY, RowPresentation.nameColor(row.state));

            Component action = RowPresentation.actionLabel(row, isPartyLeader());
            if (action != null) {
                String actionText = Objects.requireNonNull(action.getString());
                font.draw(poseStack, actionText, actionX, rowY, RowPresentation.actionColor(row.state));
            }

            if (starHovered) {
                renderTooltip(poseStack, Objects.requireNonNull(RowPresentation.favoriteTooltip(row.favorite)), mouseX, mouseY);
            } else if (hovered) {
                Component hint = RowPresentation.tooltip(row, isPartyLeader());
                if (hint != null) {
                    renderTooltip(poseStack, Objects.requireNonNull(hint), mouseX, mouseY);
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
        int panelBottom = PANEL_TOP + PANEL_HEIGHT;
        int listHeight = Math.min(VISIBLE_ONLINE_ROWS * ONLINE_ROW_HEIGHT + 6, Math.max(0, panelBottom - listTop - 8));
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
        List<OnlineRow> visiblePlayers = filteredOnlineRows();
        if (index < 0 || index >= visiblePlayers.size()) {
            return false;
        }

        this.selectedOnlineIndex = index;

        OnlineRow row = visiblePlayers.get(index);
        OnlinePlayer player = row.player;
        int actionY = listTop + (index - this.onlineScrollOffset) * ONLINE_ROW_HEIGHT + 4;
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int actionX = panelX + panelWidth - 110;
        int starX = panelX + 12;
        if (isFavoriteToggleHovered(mouseX, mouseY, starX, actionY)) {
            ClientFavoritePlayers.toggleFavorite(player.id);
            pushToast(new TranslatableComponent(row.favorite ? "screen.vaultpartyui.toast_favorite_removed" : "screen.vaultpartyui.toast_favorite_added", player.name), 0xE3C38C);
            return true;
        }
        if (mouseX >= actionX && mouseX <= actionX + 104 && mouseY >= actionY && mouseY <= actionY + ONLINE_ROW_HEIGHT - 2) {
            performPrimaryRowAction(row);
            return true;
        }

        // Clicking anywhere on row selects it for keyboard action.
        return true;
    }

    private boolean isPartyLeader() {
        return PartyRosterService.isPartyLeader(this.currentParty, getLocalPlayerId());
    }

    private boolean hasInviteableFavorites() {
        List<OnlineRow> rows = PartyRosterService.buildRows(
                this.onlinePlayers,
                FilterMode.ALL,
                this.currentParty,
                getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );

        for (OnlineRow row : rows) {
            if (row.favorite && row.state == RowState.INVITEABLE) {
                return true;
            }
        }
        return false;
    }

    private void inviteFavoritePlayers() {
        List<OnlineRow> rows = PartyRosterService.buildRows(
                this.onlinePlayers,
                FilterMode.ALL,
                this.currentParty,
                getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );

        int invited = 0;
        for (OnlineRow row : rows) {
            if (!row.favorite || row.state != RowState.INVITEABLE) {
                continue;
            }
            sendPartyCommand("party invite " + row.player.name);
            this.inviteCooldownUntilMs.put(row.player.id, System.currentTimeMillis() + INVITE_COOLDOWN_MS);
            invited++;
        }

        if (invited > 0) {
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_invited_favorites"), 0xA0E0A0);
        } else {
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_no_favorite_invites"), 0xB0B0B0);
        }
    }

    private boolean isLocalPlayerInParty() {
        return PartyRosterService.isLocalPlayerInParty(this.currentParty, getLocalPlayerId());
    }

    private boolean performPrimaryRowAction(OnlineRow row) {
        if (row == null || row.player == null) return false;
        OnlinePlayer player = row.player;
        switch (row.state) {
            case INVITEABLE:
                sendPartyCommand("party invite " + player.name);
                this.inviteCooldownUntilMs.put(player.id, System.currentTimeMillis() + INVITE_COOLDOWN_MS);
                pushToast(new TranslatableComponent("screen.vaultpartyui.toast_invited", player.name), 0xA0E0A0);
                return true;
            case PARTY_MEMBER:
                if (isPartyLeader()) {
                    sendPartyCommand("party remove " + player.name);
                    pushToast(new TranslatableComponent("screen.vaultpartyui.toast_removed", player.name), 0xE0A0A0);
                    return true;
                }
                pushToast(new TranslatableComponent("screen.vaultpartyui.tip_member"), 0xE3C38C);
                return false;
            case OTHER_PARTY:
                Component msg = new TranslatableComponent("screen.vaultpartyui.already_in_party_local", player.name);
                showClientMessage(msg);
                pushToast(msg, 0xE3C38C);
                return false;
            case COOLDOWN:
                pushToast(new TranslatableComponent("screen.vaultpartyui.tip_cooldown"), 0xB0B0B0);
                return false;
            case NO_ACTION:
                pushToast(new TranslatableComponent("screen.vaultpartyui.tip_no_action"), 0xB0B0B0);
                return false;
            default:
                return false;
        }
    }

    private void ensureSelectedRowVisible(int rowCount) {
        if (rowCount <= 0 || this.selectedOnlineIndex < 0) return;
        if (this.selectedOnlineIndex < this.onlineScrollOffset) {
            this.onlineScrollOffset = this.selectedOnlineIndex;
        }
        int maxVisible = this.onlineScrollOffset + VISIBLE_ONLINE_ROWS - 1;
        if (this.selectedOnlineIndex > maxVisible) {
            this.onlineScrollOffset = this.selectedOnlineIndex - VISIBLE_ONLINE_ROWS + 1;
        }
        int maxOffset = Math.max(0, rowCount - VISIBLE_ONLINE_ROWS);
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset, 0, maxOffset);
    }

    private void pruneTransientState() {
        long now = System.currentTimeMillis();
        PartyRosterService.pruneCooldowns(this.inviteCooldownUntilMs, now);

        this.toasts.removeIf(t -> t.expiresAt <= now);
    }

    private void pushToast(Component message, int color) {
        if (message == null) return;
        this.toasts.add(new UiToast(message, color, System.currentTimeMillis() + 2600L));
        if (this.toasts.size() > 3) {
            this.toasts.remove(0);
        }
    }

    private void renderToasts(PoseStack poseStack) {
        Objects.requireNonNull(poseStack, "poseStack");
        if (this.toasts.isEmpty()) return;
        int y = 34;
        for (UiToast toast : this.toasts) {
            String text = Objects.requireNonNull(toast.message.getString());
            int w = this.font.width(text) + 10;
            int x = this.width - w - 10;
            fill(poseStack, x, y - 1, x + w, y + this.font.lineHeight + 3, 0xCC111111);
            this.font.drawShadow(poseStack, text, x + 5, y + 1, toast.color);
            y += this.font.lineHeight + 6;
        }
    }

    private void showClientMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && message != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    private UUID getLocalPlayerId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        return minecraft.player.getUUID();
    }

    private String resolvePlayerName(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || playerId == null) return "offline";
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return "offline";
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null && playerId.equals(profile.getId())) {
                return profile.getName();
            }
        }
        return "offline";
    }

    private String formatHealth(float hp) {
        return String.format(Locale.ROOT, "%.1f", hp);
    }

    private void drawPlayerHead(PoseStack poseStack, UUID playerId, int x, int y) {
        Objects.requireNonNull(poseStack, "poseStack");
        ResourceLocation skin = Objects.requireNonNull(getPlayerSkin(playerId));
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, skin);
        // Base face + hat layer from the standard 64x64 skin texture.
        blit(poseStack, x, y, 8.0F, 8.0F, HEAD_SIZE, HEAD_SIZE, 64, 64);
        blit(poseStack, x, y, 40.0F, 8.0F, HEAD_SIZE, HEAD_SIZE, 64, 64);
    }

    private ResourceLocation getPlayerSkin(UUID playerId) {
        UUID safeId = playerId == null ? new UUID(0L, 0L) : playerId;
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(safeId);
            if (playerInfo != null) {
                return playerInfo.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(safeId);
    }

    private boolean isFavoriteToggleHovered(double mouseX, double mouseY, int starX, int rowY) {
        return mouseX >= starX && mouseX <= starX + STAR_SIZE && mouseY >= rowY - 1 && mouseY <= rowY + STAR_SIZE;
    }

    private int statusColor(PartyMember.Status status) {
        if (status == null) return 0xFFFFFF;
        String s = status.name();
        if (s.equalsIgnoreCase("DEAD") || s.contains("DEAD")) {
            return 0xFF5555;
        } else if (s.equalsIgnoreCase("DOWNED") || s.toLowerCase(Locale.ROOT).contains("down")) {
            return 0xFFAA00;
        } else {
            return 0xFFFFFF;
        }
    }

    private void drawPanel(PoseStack poseStack, int x, int y, int width, int height) {
        Objects.requireNonNull(poseStack, "poseStack");
        fill(poseStack, x, y, x + width, y + height, 0xAA111111);
        fill(poseStack, x, y, x + width, y + 1, 0xFFE3C38C);
    }

    private void openUrl(String url) {
        Minecraft mc = this.minecraft;
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

            if (mc != null) {
                mc.setScreen(this);
            }
        }, url, false));
    }

}
