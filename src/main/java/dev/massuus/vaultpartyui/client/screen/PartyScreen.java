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
import dev.massuus.vaultpartyui.client.ClientTickEvents;
import dev.massuus.vaultpartyui.client.VoiceChatIntegration;
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
    private static final int MANAGE_BUTTON_WIDTH = 82;
    private static final int LEAVE_VOICE_BUTTON_WIDTH = 76;
    private static final int BULK_VOICE_BUTTON_WIDTH = 190;
    private static final int INVITE_BUTTON_WIDTH = 68;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int ACTION_GROUP_TOP = 18;
    private static final int ACTION_BUTTON_Y = 30;
    private static final int ACTION_GROUP_PADDING = 3;
    private static final int PANEL_TOP = 58;
    private static final int PANEL_HEIGHT = 246;
    private static final int PANEL_PADDING = 10;
    private static final int ONLINE_ROW_HEIGHT = 14;
    private static final int VISIBLE_ONLINE_ROWS = 15;
    private static final int HEAD_SIZE = 8;
    private static final int VOICE_ICON_SIZE = 10;
    private static final float VOICE_ICON_SCALE = VOICE_ICON_SIZE / 16.0F;
    private static final long INVITE_COOLDOWN_MS = 8000L;
    private static final int STATE_REFRESH_INTERVAL_TICKS = 4;
    private static final int STAR_SIZE = 8;
    private static final int PARTY_ACTION_WIDTH = 96;
    private static final String MOD_VERSION = "VPUI v1.4";
    private static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods/vault-party-ui";
    private static final String GITHUB_URL = "https://github.com/massuus/vault-party-ui";
    private static final ResourceLocation VOICE_GROUP_ICON = new ResourceLocation("voicechat", "textures/icons/microphone.png");

    private final Screen parentScreen;

    private Party currentParty;
    private List<OnlinePlayer> onlinePlayers = Collections.emptyList();
    private EditBox targetBox;
    private Button createPartyButton;
    private Button leavePartyButton;
    private Button disbandPartyButton;
    private Button leaveVoiceGroupButton;
    private Button inviteNearbyButton;
    private Button inviteAllButton;
    private Button inviteFavoritesButton;
    private Button inviteVoiceGroupButton;
    private Button invitePartyVoiceGroupButton;
    private Button acceptInviteButton;
    private Button declineInviteButton;
    private Button autoAcceptToggleButton;
    private int onlineScrollOffset;
    private int selectedOnlineIndex = -1;
    private int stateRefreshTicks;
    private Component queuedTooltip;
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
        int leftRowWidth = MANAGE_BUTTON_WIDTH * 2 + LEAVE_VOICE_BUTTON_WIDTH + BUTTON_GAP * 2;
        int rightRowWidth = INVITE_BUTTON_WIDTH * 4 + BUTTON_GAP * 3;
        int leftRowX = leftPanelX + panelWidth / 2 - leftRowWidth / 2;
        int rightRowX = rightPanelX + panelWidth / 2 - rightRowWidth / 2;

        int createX = this.width / 2 - BUTTON_WIDTH / 2;
        this.createPartyButton = addRenderableWidget(new Button(createX, ACTION_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.create"), button -> sendPartyCommand("party create")));
        this.leavePartyButton = addRenderableWidget(new Button(leftRowX, ACTION_BUTTON_Y, MANAGE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.leave_short"), button -> sendPartyCommand("party leave")));
        this.disbandPartyButton = addRenderableWidget(new Button(leftRowX + MANAGE_BUTTON_WIDTH + BUTTON_GAP, ACTION_BUTTON_Y, MANAGE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.disband_short"), button -> confirmDisbandParty()));
        this.leaveVoiceGroupButton = addRenderableWidget(new Button(leftRowX + (MANAGE_BUTTON_WIDTH + BUTTON_GAP) * 2, ACTION_BUTTON_Y, LEAVE_VOICE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.leave_voice_short"), button -> leaveVoiceGroup()));

        this.inviteNearbyButton = addRenderableWidget(new Button(rightRowX, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_nearby_short"), button -> sendPartyCommand("party invite nearby")));
        this.inviteAllButton = addRenderableWidget(new Button(rightRowX + INVITE_BUTTON_WIDTH + BUTTON_GAP, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_all_short"), button -> sendPartyCommand("party invite all")));
        this.inviteFavoritesButton = addRenderableWidget(new Button(rightRowX + (INVITE_BUTTON_WIDTH + BUTTON_GAP) * 2, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_favorites_short"), button -> inviteFavoritePlayers()));
        this.inviteVoiceGroupButton = addRenderableWidget(new Button(rightRowX + (INVITE_BUTTON_WIDTH + BUTTON_GAP) * 3, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_voice_group_short"), button -> ClientTickEvents.triggerVoiceGroupPartyAction()));
        this.invitePartyVoiceGroupButton = addRenderableWidget(new Button(20, this.height - BUTTON_HEIGHT * 2 - 12, BULK_VOICE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_party_voice_group"), button -> invitePartyToVoiceGroup()));

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
        this.queuedTooltip = null;
        this.renderBackground(poseStack);

        int leftPanelX = 20;
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;
        int panelBottom = PANEL_TOP + PANEL_HEIGHT;

        drawPanel(poseStack, leftPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);
        drawPanel(poseStack, rightPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);
        renderActionGroupOutlines(poseStack);

        GuiComponent.drawCenteredString(poseStack, font, Objects.requireNonNull(this.title), this.width / 2, 8, 0xFFFFFF);
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
        renderQueuedTooltip(poseStack, mouseX, mouseY);


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

        if (handlePartyPanelClick(mouseX, mouseY)) {
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
        if (this.leaveVoiceGroupButton != null) {
            this.leaveVoiceGroupButton.visible = inParty && VoiceChatIntegration.hasLocalVoiceGroup();
            this.leaveVoiceGroupButton.active = this.leaveVoiceGroupButton.visible;
        }

        if (inParty && this.leavePartyButton != null && this.disbandPartyButton != null && this.leaveVoiceGroupButton != null) {
            boolean showLeaveVoice = this.leaveVoiceGroupButton.visible;
            int rowWidth = MANAGE_BUTTON_WIDTH * 2 + BUTTON_GAP + (showLeaveVoice ? LEAVE_VOICE_BUTTON_WIDTH + BUTTON_GAP : 0);
            int rowX = leftPanelX + panelWidth / 2 - rowWidth / 2;
            this.leavePartyButton.x = rowX;
            this.leavePartyButton.y = ACTION_BUTTON_Y;
            this.disbandPartyButton.x = rowX + MANAGE_BUTTON_WIDTH + BUTTON_GAP;
            this.disbandPartyButton.y = ACTION_BUTTON_Y;
            if (showLeaveVoice) {
                this.leaveVoiceGroupButton.x = this.disbandPartyButton.x + MANAGE_BUTTON_WIDTH + BUTTON_GAP;
                this.leaveVoiceGroupButton.y = ACTION_BUTTON_Y;
            }
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
        if (this.inviteVoiceGroupButton != null) {
            this.inviteVoiceGroupButton.visible = inParty && VoiceChatIntegration.hasLocalVoiceGroup();
            this.inviteVoiceGroupButton.active = this.inviteVoiceGroupButton.visible && hasInviteableVoiceGroupPlayersForParty();
        }
        if (this.invitePartyVoiceGroupButton != null) {
            this.invitePartyVoiceGroupButton.visible = inParty && VoiceChatIntegration.isVoiceChatLoaded();
            this.invitePartyVoiceGroupButton.active = this.invitePartyVoiceGroupButton.visible && hasPartyMembersAvailableForVoiceInvite();
        }
        if (this.autoAcceptToggleButton != null) {
            this.autoAcceptToggleButton.visible = true;
            this.autoAcceptToggleButton.active = true;
            this.autoAcceptToggleButton.x = 20;
            this.autoAcceptToggleButton.y = this.height - BUTTON_HEIGHT - 8;
        }
        if (this.invitePartyVoiceGroupButton != null) {
            this.invitePartyVoiceGroupButton.x = 20;
            this.invitePartyVoiceGroupButton.y = this.height - BUTTON_HEIGHT * 2 - 12;
            this.invitePartyVoiceGroupButton.setWidth(BULK_VOICE_BUTTON_WIDTH);
        }
        if (this.inviteNearbyButton != null && this.inviteAllButton != null && this.inviteFavoritesButton != null && this.inviteVoiceGroupButton != null) {
            boolean showVoiceGroupButton = this.inviteVoiceGroupButton.visible;
            int buttonCount = 3 + (showVoiceGroupButton ? 1 : 0);
            int rowWidth = INVITE_BUTTON_WIDTH * buttonCount + BUTTON_GAP * (buttonCount - 1);
            int rowX = rightPanelX + panelWidth / 2 - rowWidth / 2;
            this.inviteNearbyButton.x = rowX;
            this.inviteNearbyButton.y = ACTION_BUTTON_Y;
            this.inviteAllButton.x = rowX + INVITE_BUTTON_WIDTH + BUTTON_GAP;
            this.inviteAllButton.y = ACTION_BUTTON_Y;
            this.inviteFavoritesButton.x = rowX + (INVITE_BUTTON_WIDTH + BUTTON_GAP) * 2;
            this.inviteFavoritesButton.y = ACTION_BUTTON_Y;
            int nextX = rowX + (INVITE_BUTTON_WIDTH + BUTTON_GAP) * 3;
            if (showVoiceGroupButton) {
                this.inviteVoiceGroupButton.x = nextX;
                this.inviteVoiceGroupButton.y = ACTION_BUTTON_Y;
            }
        }

        updateInviteButtons();
        updateAutoAcceptToggleLabel();
    }

    private void renderActionGroupOutlines(PoseStack poseStack) {
        Objects.requireNonNull(poseStack, "poseStack");
        if (!isLocalPlayerInParty()) {
            return;
        }

        Button lastManageButton = this.leaveVoiceGroupButton != null && this.leaveVoiceGroupButton.visible
                ? this.leaveVoiceGroupButton
                : this.disbandPartyButton;
        renderButtonGroupOutline(poseStack, this.leavePartyButton, lastManageButton, new TranslatableComponent("screen.vaultpartyui.manage_party"));

        Button lastInviteButton = this.inviteVoiceGroupButton != null && this.inviteVoiceGroupButton.visible
                ? this.inviteVoiceGroupButton
                : this.inviteFavoritesButton;
        renderButtonGroupOutline(poseStack, this.inviteNearbyButton, lastInviteButton, new TranslatableComponent("screen.vaultpartyui.invite_group"));
    }

    private void renderButtonGroupOutline(PoseStack poseStack, Button firstButton, Button lastButton, Component label) {
        if (firstButton == null || lastButton == null || !firstButton.visible || !lastButton.visible) {
            return;
        }

        int x1 = firstButton.x - ACTION_GROUP_PADDING;
        int y1 = ACTION_GROUP_TOP;
        int x2 = lastButton.x + lastButton.getWidth() + ACTION_GROUP_PADDING;
        int y2 = ACTION_BUTTON_Y + BUTTON_HEIGHT + ACTION_GROUP_PADDING;
        fill(poseStack, x1, y1, x2, y1 + 1, 0xFFE3C38C);
        fill(poseStack, x1, y2, x2, y2 + 1, 0xFFE3C38C);
        fill(poseStack, x1, y1, x1 + 1, y2 + 1, 0xFFE3C38C);
        fill(poseStack, x2 - 1, y1, x2, y2 + 1, 0xFFE3C38C);
        if (label != null) {
            String text = Objects.requireNonNull(label.getString());
            int labelX = x1 + (x2 - x1) / 2 - this.font.width(text) / 2;
            fill(poseStack, labelX - 3, y1 - 4, labelX + this.font.width(text) + 3, y1 + 7, 0xFF111111);
            this.font.draw(poseStack, text, labelX, y1 - 3, 0xE3C38C);
        }
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
        int panelBottom = PANEL_TOP + PANEL_HEIGHT - 8;

        if (this.currentParty == null) {
            String noParty = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.no_party").getString());
            font.draw(poseStack, noParty, textX, textY, 0xE0E0E0);
            return;
        }

        List<UUID> members = sortedPartyMembers();
        String membersText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.members").getString()) + ": " + members.size();
        font.draw(poseStack, membersText, textX, textY, 0xE0E0E0);
        textY += 16;

        for (UUID memberId : members) {
            if (textY + ONLINE_ROW_HEIGHT > panelBottom) {
                return;
            }
            renderPartyMemberRow(poseStack, panelX, panelWidth, memberId, textY, mouseX, mouseY);
            textY += ONLINE_ROW_HEIGHT;
        }

        List<OnlineRow> voiceRows = voiceGroupRowsNotInParty();
        if (!voiceRows.isEmpty() && textY + 24 <= panelBottom) {
            textY += 8;
            font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.voice_not_party"), textX, textY, 0xE3C38C);
            textY += 14;
            for (OnlineRow row : voiceRows) {
                if (textY + ONLINE_ROW_HEIGHT > panelBottom) {
                    return;
                }
                renderVoiceNotPartyRow(poseStack, panelX, panelWidth, row, textY, mouseX, mouseY);
                textY += ONLINE_ROW_HEIGHT;
            }
        }
    }

    private void renderPartyMemberRow(PoseStack poseStack, int panelX, int panelWidth, UUID memberId, int rowY, int mouseX, int mouseY) {
        int rowLeft = panelX + 10;
        int rowRight = panelX + panelWidth - 10;
        boolean hovered = mouseX >= rowLeft && mouseX <= rowRight && mouseY >= rowY - 2 && mouseY < rowY + ONLINE_ROW_HEIGHT - 2;
        if (hovered) {
            fill(poseStack, rowLeft, rowY - 2, rowRight, rowY + ONLINE_ROW_HEIGHT - 2, 0x663C3122);
        }

        int x = rowLeft + 2;
        boolean inVoiceGroup = VoiceChatIntegration.isPlayerInLocalVoiceGroup(memberId);
        if (inVoiceGroup) {
            drawVoiceGroupIcon(poseStack, x, rowY - 1);
            x += VOICE_ICON_SIZE + 3;
        }
        drawPlayerHead(poseStack, memberId, x, rowY);
        x += HEAD_SIZE + 4;

        String lineText = partyMemberLine(memberId);
        int actionX = partyPanelActionX(panelX, panelWidth);
        int nameWidth = Math.max(0, actionX - x - 6);
        this.font.draw(poseStack, this.font.plainSubstrByWidth(lineText, nameWidth), x, rowY, partyMemberColor(memberId));

        if (canInvitePartyMemberToVoice(memberId)) {
            this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.invite_to_voice"), actionX, rowY, 0xA0E0A0);
            if (isPartyActionHovered(mouseX, mouseY, actionX, rowY)) {
                queueTooltip(new TranslatableComponent("screen.vaultpartyui.tip_invite_to_voice"));
            }
        } else if (canJoinPartyMemberVoiceGroup(memberId)) {
            this.font.draw(poseStack, new TranslatableComponent("screen.vaultpartyui.join_voice_group"), actionX, rowY, 0xA0E0A0);
            if (isPartyActionHovered(mouseX, mouseY, actionX, rowY)) {
                queueTooltip(new TranslatableComponent("screen.vaultpartyui.tip_join_voice_group"));
            }
        }
    }

    private void renderVoiceNotPartyRow(PoseStack poseStack, int panelX, int panelWidth, OnlineRow row, int rowY, int mouseX, int mouseY) {
        int rowLeft = panelX + 10;
        int rowRight = panelX + panelWidth - 10;
        boolean hovered = mouseX >= rowLeft && mouseX <= rowRight && mouseY >= rowY - 2 && mouseY < rowY + ONLINE_ROW_HEIGHT - 2;
        if (hovered) {
            fill(poseStack, rowLeft, rowY - 2, rowRight, rowY + ONLINE_ROW_HEIGHT - 2, 0x663C3122);
        }

        int x = rowLeft + 2;
        drawVoiceGroupIcon(poseStack, x, rowY - 1);
        x += VOICE_ICON_SIZE + 3;
        drawPlayerHead(poseStack, row.player.id, x, rowY);
        x += HEAD_SIZE + 4;

        int actionX = partyPanelActionX(panelX, panelWidth);
        int nameWidth = Math.max(0, actionX - x - 6);
        this.font.draw(poseStack, this.font.plainSubstrByWidth(row.player.name, nameWidth), x, rowY, RowPresentation.nameColor(row.state));

        Component action = row.state == RowState.INVITEABLE
                ? new TranslatableComponent("screen.vaultpartyui.invite_to_party")
                : RowPresentation.actionLabel(row, isPartyLeader());
        if (action != null) {
            this.font.draw(poseStack, action.getString(), actionX, rowY, RowPresentation.actionColor(row.state));
            if (row.state == RowState.INVITEABLE && isPartyActionHovered(mouseX, mouseY, actionX, rowY)) {
                queueTooltip(new TranslatableComponent("screen.vaultpartyui.tip_invite_to_party"));
            }
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

            int actionX = panelX + panelWidth - 110;
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
            String displayName = font.plainSubstrByWidth(safeName, nameWidth);
            font.draw(poseStack, Objects.requireNonNull(String.valueOf(displayName)), nameX, rowY, RowPresentation.nameColor(row.state));

            Component action = RowPresentation.actionLabel(row, isPartyLeader());
            if (action != null) {
                String actionText = Objects.requireNonNull(action.getString());
                font.draw(poseStack, actionText, actionX, rowY, RowPresentation.actionColor(row.state));
            }

            boolean voiceIconHovered = inVoiceGroup && mouseX >= voiceIconX && mouseX <= voiceIconX + VOICE_ICON_SIZE && mouseY >= voiceIconY && mouseY <= voiceIconY + VOICE_ICON_SIZE;
            if (starHovered) {
                queueTooltip(Objects.requireNonNull(RowPresentation.favoriteTooltip(row.favorite)));
            } else if (voiceIconHovered) {
                queueTooltip(new TranslatableComponent("screen.vaultpartyui.tip_voice_group_member"));
            } else if (hovered) {
                Component hint = RowPresentation.tooltip(row, isPartyLeader());
                if (hint != null) {
                    queueTooltip(Objects.requireNonNull(hint));
                }
            }

            rowY += ONLINE_ROW_HEIGHT;
        }
    }

    private void queueTooltip(Component tooltip) {
        this.queuedTooltip = tooltip;
    }

    private void renderQueuedTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        if (this.queuedTooltip != null) {
            renderTooltip(poseStack, this.queuedTooltip, mouseX, mouseY);
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

    private boolean handlePartyPanelClick(double mouseX, double mouseY) {
        if (this.currentParty == null || !isInsidePartyPanel(mouseX, mouseY)) {
            return false;
        }

        int leftPanelX = 20;
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int actionX = partyPanelActionX(leftPanelX, panelWidth);
        if (mouseX < actionX || mouseX > actionX + PARTY_ACTION_WIDTH) {
            return true;
        }

        int rowY = PANEL_TOP + 40;
        for (UUID memberId : sortedPartyMembers()) {
            if (mouseY >= rowY - 2 && mouseY <= rowY + ONLINE_ROW_HEIGHT - 2) {
                if (canInvitePartyMemberToVoice(memberId)) {
                    invitePlayerToVoiceGroup(memberId);
                } else if (canJoinPartyMemberVoiceGroup(memberId)) {
                    joinPartyMemberVoiceGroup(memberId);
                }
                return true;
            }
            rowY += ONLINE_ROW_HEIGHT;
        }

        List<OnlineRow> voiceRows = voiceGroupRowsNotInParty();
        if (!voiceRows.isEmpty()) {
            rowY += 22;
            for (OnlineRow row : voiceRows) {
                if (mouseY >= rowY - 2 && mouseY <= rowY + ONLINE_ROW_HEIGHT - 2) {
                    if (row.state == RowState.INVITEABLE) {
                        performPrimaryRowAction(row);
                    }
                    return true;
                }
                rowY += ONLINE_ROW_HEIGHT;
            }
        }

        return true;
    }

    private boolean isInsidePartyPanel(double mouseX, double mouseY) {
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        return mouseX >= 20 + 8 && mouseX <= 20 + panelWidth - 8 && mouseY >= PANEL_TOP && mouseY <= PANEL_TOP + PANEL_HEIGHT;
    }

    private int partyPanelActionX(int panelX, int panelWidth) {
        return panelX + panelWidth - PARTY_ACTION_WIDTH - 14;
    }

    private boolean isPartyActionHovered(int mouseX, int mouseY, int actionX, int rowY) {
        return mouseX >= actionX && mouseX <= actionX + PARTY_ACTION_WIDTH && mouseY >= rowY - 2 && mouseY <= rowY + ONLINE_ROW_HEIGHT - 2;
    }

    private boolean isPartyLeader() {
        return PartyRosterService.isPartyLeader(this.currentParty, getLocalPlayerId());
    }

    private List<UUID> sortedPartyMembers() {
        if (this.currentParty == null || this.currentParty.getMembers() == null) {
            return Collections.emptyList();
        }

        List<UUID> members = new ArrayList<>(this.currentParty.getMembers());
        members.sort((a, b) -> {
            int voiceCompare = Boolean.compare(VoiceChatIntegration.isPlayerInLocalVoiceGroup(b), VoiceChatIntegration.isPlayerInLocalVoiceGroup(a));
            if (voiceCompare != 0) {
                return voiceCompare;
            }
            int anyVoiceCompare = Boolean.compare(VoiceChatIntegration.getPlayerVoiceGroupId(b) != null, VoiceChatIntegration.getPlayerVoiceGroupId(a) != null);
            if (anyVoiceCompare != 0) {
                return anyVoiceCompare;
            }
            return resolvePlayerName(a).compareToIgnoreCase(resolvePlayerName(b));
        });
        return members;
    }

    private List<OnlineRow> voiceGroupRowsNotInParty() {
        if (this.currentParty == null || this.onlinePlayers.isEmpty() || !VoiceChatIntegration.hasLocalVoiceGroup()) {
            return Collections.emptyList();
        }

        List<OnlinePlayer> players = new ArrayList<>();
        for (OnlinePlayer player : this.onlinePlayers) {
            if (player == null || player.id == null) {
                continue;
            }
            if (VoiceChatIntegration.isPlayerInLocalVoiceGroup(player.id) && !PartyRosterService.isPlayerInCurrentParty(this.currentParty, player.id)) {
                players.add(player);
            }
        }

        return PartyRosterService.buildRows(
                players,
                FilterMode.ALL,
                this.currentParty,
                getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );
    }

    private String partyMemberLine(UUID memberId) {
        String memberName = resolvePlayerName(memberId);
        StringBuilder line = new StringBuilder(memberName);
        if (memberId != null && memberId.equals(this.currentParty.getLeader())) {
            line.append(" [").append(new TranslatableComponent("screen.vaultpartyui.leader").getString()).append("]");
        }
        if (memberId != null && memberId.equals(getLocalPlayerId())) {
            line.append(" [").append(Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.self").getString())).append("]");
        }

        PartyMember cachedMember = ClientPartyData.getCachedMember(memberId);
        if (cachedMember != null) {
            if (cachedMember.status != PartyMember.Status.NORMAL) {
                line.append(" - ").append(cachedMember.status.name());
            }
            line.append(" - ").append(formatHealth(cachedMember.healthPts)).append(" \u2764");
        }
        return line.toString();
    }

    private int partyMemberColor(UUID memberId) {
        PartyMember cachedMember = ClientPartyData.getCachedMember(memberId);
        return cachedMember == null ? 0xFFFFFF : statusColor(cachedMember.status);
    }

    private boolean canInvitePartyMemberToVoice(UUID memberId) {
        if (memberId == null || memberId.equals(getLocalPlayerId())) {
            return false;
        }
        return VoiceChatIntegration.isVoiceChatLoaded()
                && VoiceChatIntegration.hasLocalVoiceGroup()
                && !VoiceChatIntegration.isPlayerInLocalVoiceGroup(memberId)
                && findOnlinePlayer(memberId) != null;
    }

    private boolean canJoinPartyMemberVoiceGroup(UUID memberId) {
        if (memberId == null || memberId.equals(getLocalPlayerId())) {
            return false;
        }
        return VoiceChatIntegration.isVoiceChatLoaded()
                && !VoiceChatIntegration.hasLocalVoiceGroup()
                && VoiceChatIntegration.getPlayerVoiceGroupId(memberId) != null
                && findOnlinePlayer(memberId) != null;
    }

    private void invitePlayerToVoiceGroup(UUID playerId) {
        OnlinePlayer player = findOnlinePlayer(playerId);
        if (player == null) {
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_no_party_voice_invites"), 0xB0B0B0);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.chat("/voicechat invite " + player.name);
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_invited_to_voice", player.name), 0xA0E0A0);
        }
    }

    private void joinPartyMemberVoiceGroup(UUID playerId) {
        OnlinePlayer player = findOnlinePlayer(playerId);
        UUID groupId = VoiceChatIntegration.getPlayerVoiceGroupId(playerId);
        if (player == null || groupId == null) {
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_no_joinable_voice_group"), 0xB0B0B0);
            return;
        }

        boolean sent = VoiceChatIntegration.joinVoiceGroup(groupId, null);
        if (sent) {
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_joining_voice_group", player.name), 0xA0E0A0);
        } else {
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_join_voice_group_failed"), 0xB0B0B0);
        }
    }

    private OnlinePlayer findOnlinePlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (OnlinePlayer player : this.onlinePlayers) {
            if (player != null && playerId.equals(player.id)) {
                return player;
            }
        }
        return null;
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

    private boolean hasInviteableVoiceGroupPlayersForParty() {
        for (OnlineRow row : voiceGroupRowsNotInParty()) {
            if (row.state == RowState.INVITEABLE) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPartyMembersAvailableForVoiceInvite() {
        if (!VoiceChatIntegration.isVoiceChatLoaded() || this.currentParty == null || this.currentParty.getMembers() == null) {
            return false;
        }

        if (!VoiceChatIntegration.hasLocalVoiceGroup()) {
            for (UUID memberId : this.currentParty.getMembers()) {
                if (memberId != null && !memberId.equals(getLocalPlayerId()) && findOnlinePlayer(memberId) != null) {
                    return true;
                }
            }
            return false;
        }

        for (UUID memberId : this.currentParty.getMembers()) {
            if (canInvitePartyMemberToVoice(memberId)) {
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

    private void invitePartyToVoiceGroup() {
        if (!VoiceChatIntegration.isVoiceChatLoaded()) {
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_voicechat_missing"), 0xB0B0B0);
            return;
        }

        if (VoiceChatIntegration.hasLocalVoiceGroup()) {
            ClientTickEvents.invitePartyToVoiceGroupNow();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new VoiceGroupCreateScreen(this));
        }
    }

    private void leaveVoiceGroup() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.chat("/voicechat leave");
            pushToast(new TranslatableComponent("screen.vaultpartyui.toast_left_voice_group"), 0xE3C38C);
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

    private void drawVoiceGroupIcon(PoseStack poseStack, int x, int y) {
        Objects.requireNonNull(poseStack, "poseStack");
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, VOICE_GROUP_ICON);
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0D);
        poseStack.scale(VOICE_ICON_SCALE, VOICE_ICON_SCALE, 1.0F);
        blit(poseStack, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
        poseStack.popPose();
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
