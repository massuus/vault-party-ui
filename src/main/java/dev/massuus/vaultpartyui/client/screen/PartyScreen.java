package dev.massuus.vaultpartyui.client.screen;

import static dev.massuus.vaultpartyui.client.screen.PartyScreenGraphics.drawPanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.massuus.vaultpartyui.client.ClientFavoritePlayers;
import dev.massuus.vaultpartyui.client.ClientPartySettings;
import dev.massuus.vaultpartyui.client.ClientTickEvents;
import dev.massuus.vaultpartyui.client.VoiceChatIntegration;
import iskallia.vault.client.data.ClientPartyData;
import iskallia.vault.client.data.ClientPartyInviteState;
import iskallia.vault.world.data.VaultPartyData.Party;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Mth;

public class PartyScreen extends Screen {
    private static final int BUTTON_WIDTH = 90;
    private static final int RESTORE_BUTTON_WIDTH = 116;
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
    private static final int ONLINE_ROW_HEIGHT = PartyOnlinePanelRenderer.ROW_HEIGHT;
    private static final long INVITE_COOLDOWN_MS = 8000L;
    private static final int STATE_REFRESH_INTERVAL_TICKS = 4;
    private static final String MOD_VERSION = "VPUI v1.5.2";
    private static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods/vault-party-ui";
    private static final String CURSEFORGE_AUTHOR_URL = "https://www.curseforge.com/members/massuus/projects";
    private static final String GITHUB_RELEASES_API_URL = "https://api.github.com/repos/massuus/vault-party-ui/releases/latest";

    @Nullable
    private final Screen parentScreen;

    private Party currentParty;
    private List<OnlinePlayer> onlinePlayers = Collections.emptyList();
    private EditBox targetBox;
    private Button createPartyButton;
    private Button restorePreviousPartyButton;
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
    private int partyScrollOffset;
    private int onlineScrollOffset;
    private int selectedOnlineIndex = -1;
    private int stateRefreshTicks;
    @Nullable
    private Component queuedTooltip;
    private final Map<UUID, Long> inviteCooldownUntilMs = new HashMap<>();
    private final List<UiToast> toasts = new ArrayList<>();
    private final PartyScreenController controller;

    public PartyScreen(@Nullable Screen parentScreen) {
        super(new TranslatableComponent("screen.vaultpartyui.title"));
        this.parentScreen = parentScreen;
        this.controller = new PartyScreenController(
                this,
                () -> this.currentParty,
                () -> this.onlinePlayers,
                this.inviteCooldownUntilMs,
                INVITE_COOLDOWN_MS,
                this::pushToast,
                this::showClientMessage
        );
    }

    @Override
    protected void init() {
        super.init();
        Objects.requireNonNull(this.font, "font");
        PartyUpdateChecker.ensureStarted(MOD_VERSION, GITHUB_RELEASES_API_URL);
        rebuildState();

        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int leftPanelX = 20;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;
        int leftRowWidth = MANAGE_BUTTON_WIDTH * 2 + LEAVE_VOICE_BUTTON_WIDTH + BUTTON_GAP * 2;
        int rightRowWidth = INVITE_BUTTON_WIDTH * 4 + BUTTON_GAP * 3;
        int leftRowX = leftPanelX + panelWidth / 2 - leftRowWidth / 2;
        int rightRowX = rightPanelX + panelWidth / 2 - rightRowWidth / 2;

        int createX = this.width / 2 - BUTTON_WIDTH / 2;
        this.createPartyButton = addRenderableWidget(new Button(createX, ACTION_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.create"), button -> this.controller.sendPartyCommand("party create")));
        this.restorePreviousPartyButton = addRenderableWidget(new Button(createX + BUTTON_WIDTH + BUTTON_GAP, ACTION_BUTTON_Y, RESTORE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.restore_previous"), button -> this.controller.restorePreviousParty(PreviousPartySnapshot.members())));
        this.leavePartyButton = addRenderableWidget(new Button(leftRowX, ACTION_BUTTON_Y, MANAGE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.leave_short"), button -> this.controller.sendPartyCommand("party leave")));
        this.disbandPartyButton = addRenderableWidget(new Button(leftRowX + MANAGE_BUTTON_WIDTH + BUTTON_GAP, ACTION_BUTTON_Y, MANAGE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.disband_short"), button -> this.controller.confirmDisbandParty()));
        this.leaveVoiceGroupButton = addRenderableWidget(new Button(leftRowX + (MANAGE_BUTTON_WIDTH + BUTTON_GAP) * 2, ACTION_BUTTON_Y, LEAVE_VOICE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.leave_voice_short"), button -> this.controller.leaveVoiceGroup()));

        this.inviteNearbyButton = addRenderableWidget(new Button(rightRowX, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_nearby_short"), button -> ClientTickEvents.triggerInviteNearbyAction()));
        this.inviteAllButton = addRenderableWidget(new Button(rightRowX + INVITE_BUTTON_WIDTH + BUTTON_GAP, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_all_short"), button -> ClientTickEvents.triggerCreateAndInviteAllAction()));
        this.inviteFavoritesButton = addRenderableWidget(new Button(rightRowX + (INVITE_BUTTON_WIDTH + BUTTON_GAP) * 2, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_favorites_short"), button -> ClientTickEvents.triggerCreateAndInviteFavoritesAction()));
        this.inviteVoiceGroupButton = addRenderableWidget(new Button(rightRowX + (INVITE_BUTTON_WIDTH + BUTTON_GAP) * 3, ACTION_BUTTON_Y, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_voice_group_short"), button -> ClientTickEvents.triggerVoiceGroupPartyAction()));
        this.invitePartyVoiceGroupButton = addRenderableWidget(new Button(20, this.height - BUTTON_HEIGHT * 2 - 12, BULK_VOICE_BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.invite_party_voice_group"), button -> this.controller.invitePartyToVoiceGroup()));

        int inviteButtonWidth = 140;
        // Position invite accept/decline to the left/right of the centered Create Party button, same vertical level
        this.acceptInviteButton = addRenderableWidget(new Button(createX - inviteButtonWidth - BUTTON_GAP, 34, inviteButtonWidth, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.accept_invite"), button -> this.controller.acceptPendingInvite()));
        this.declineInviteButton = addRenderableWidget(new Button(createX + BUTTON_WIDTH + BUTTON_GAP, 34, inviteButtonWidth, BUTTON_HEIGHT, new TranslatableComponent("screen.vaultpartyui.decline_invite"), button -> this.controller.declinePendingInvite()));
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
        final Font screenFont = Objects.requireNonNull(this.font, "font");
        this.queuedTooltip = null;
        this.renderBackground(poseStack);

        int leftPanelX = 20;
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;
        int panelBottom = PANEL_TOP + PANEL_HEIGHT;

        drawPanel(poseStack, leftPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);
        drawPanel(poseStack, rightPanelX, PANEL_TOP, panelWidth, PANEL_HEIGHT);
        renderActionGroupOutlines(poseStack);

        GuiComponent.drawCenteredString(poseStack, screenFont, Objects.requireNonNull(this.title), this.width / 2, 8, 0xFFFFFF);
        GuiComponent.drawCenteredString(poseStack, screenFont, new TranslatableComponent("screen.vaultpartyui.party"), leftPanelX + panelWidth / 2, PANEL_TOP + 6, 0xE3C38C);
        GuiComponent.drawCenteredString(poseStack, screenFont, new TranslatableComponent("screen.vaultpartyui.players"), rightPanelX + panelWidth / 2, PANEL_TOP + 6, 0xE3C38C);

        if (ClientPartyInviteState.hasPendingInvite()) {
            String inviterName = ClientPartyInviteState.getInviterName();
            String inviteText = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.pending_invite", inviterName == null ? "?" : inviterName).getString());
            int noticeWidth = screenFont.width(inviteText) + 14;
            int noticeX = this.width / 2 - noticeWidth / 2;
            fill(poseStack, noticeX, panelBottom + 8, noticeX + noticeWidth, panelBottom + 26, 0xCC1D1D1D);
            fill(poseStack, noticeX, panelBottom + 8, noticeX + noticeWidth, panelBottom + 9, 0xFFE3C38C);
            screenFont.draw(poseStack, inviteText, noticeX + 7, panelBottom + 13, 0xFFFFFF);
        }

        renderPartyPanel(poseStack, leftPanelX, panelWidth, mouseX, mouseY);
        renderOnlinePanel(poseStack, rightPanelX, panelWidth, mouseX, mouseY);

        super.render(poseStack, mouseX, mouseY, partialTick);

        if (this.targetBox != null) {
            String targetLabel = Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.target").getString());
            screenFont.draw(poseStack, targetLabel, this.targetBox.x, this.targetBox.y - 10, 0xA0A0A0);
        }

        PartyScreenFooter.render(poseStack, screenFont, this.width, this.height, mouseX, mouseY, MOD_VERSION, PartyUpdateChecker.isUpdateAvailable());
        PartyToastService.render(poseStack, screenFont, this.width, this.toasts);
        renderRestorePreviousTooltip(poseStack, mouseX, mouseY);
        renderQueuedTooltip(poseStack, mouseX, mouseY);


    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (PartyScreenFooter.handleClick(this.minecraft, this, this.font, this.width, this.height, mouseX, mouseY, MOD_VERSION, PartyUpdateChecker.isUpdateAvailable(), CURSEFORGE_URL, CURSEFORGE_AUTHOR_URL)) {
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

        return handleOnlinePlayerClick(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        int direction = scrollDelta > 0 ? -1 : 1;
        if (isInsidePartyPanel(mouseX, mouseY)) {
            int maxOffset = maxPartyScrollOffset();
            if (maxOffset == 0) {
                return true;
            }
            this.partyScrollOffset = Mth.clamp(this.partyScrollOffset + direction, 0, maxOffset);
            return true;
        }

        if (isInsideOnlinePanel(mouseX, mouseY)) {
            List<OnlineRow> visiblePlayers = filteredOnlineRows();
            int maxOffset = Math.max(0, visiblePlayers.size() - PartyOnlinePanelRenderer.visibleRows());
            if (maxOffset == 0) {
                return true;
            }
            this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset + direction, 0, maxOffset);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public void onClose() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(this.parentScreen);
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
                this.controller.performPrimaryRowAction(rows.get(this.selectedOnlineIndex));
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void rebuildState() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            this.currentParty = null;
            this.onlinePlayers = Collections.emptyList();
            this.partyScrollOffset = 0;
            this.onlineScrollOffset = 0;
            this.selectedOnlineIndex = -1;
            return;
        }
        LocalPlayer player = client.player;
        if (player == null) {
            this.currentParty = null;
            this.onlinePlayers = Collections.emptyList();
            this.partyScrollOffset = 0;
            this.onlineScrollOffset = 0;
            this.selectedOnlineIndex = -1;
            return;
        }

        this.currentParty = PartyDebugData.partyForDisplay(ClientPartyData.getParty(player.getUUID()), player.getUUID());
        this.onlinePlayers = PartyDebugData.onlinePlayersForDisplay(PartyPlayerLookup.gatherOnlinePlayers(client.getConnection()));
        PreviousPartySnapshot.remember(this.currentParty, this.onlinePlayers, player.getUUID());

        List<OnlineRow> visiblePlayers = filteredOnlineRows();
        int maxOffset = Math.max(0, visiblePlayers.size() - PartyOnlinePanelRenderer.visibleRows());
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset, 0, maxOffset);
        this.partyScrollOffset = Mth.clamp(this.partyScrollOffset, 0, maxPartyScrollOffset());
        if (visiblePlayers.isEmpty()) {
            this.selectedOnlineIndex = -1;
        } else {
            this.selectedOnlineIndex = Mth.clamp(this.selectedOnlineIndex, 0, visiblePlayers.size() - 1);
        }
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
            String playerName = player == null || player.name == null ? "" : player.name;
            if (playerName.toLowerCase(Locale.ROOT).contains(filter)) {
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
                PartyPlayerLookup.getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );
    }

    private void updateInviteButtons() {
        boolean hasInvite = shouldShowPendingInviteActions();
        if (this.acceptInviteButton != null) {
            this.acceptInviteButton.visible = hasInvite;
        }
        if (this.declineInviteButton != null) {
            this.declineInviteButton.visible = hasInvite;
        }
    }

    private void updateActionVisibility() {
        boolean inParty = this.controller.isLocalPlayerInParty();
        boolean hasInvite = shouldShowPendingInviteActions();
        boolean showSoloInviteActions = !inParty && !hasInvite;
        boolean showInviteActions = inParty || showSoloInviteActions;
        boolean hasPreviousParty = !PreviousPartySnapshot.members().isEmpty();
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int leftPanelX = 20;
        int rightPanelX = leftPanelX + panelWidth + PANEL_PADDING;

        if (this.createPartyButton != null) {
            this.createPartyButton.visible = !inParty;
            boolean showRestorePrevious = !inParty && !hasInvite && hasPreviousParty && this.restorePreviousPartyButton != null;
            int rowWidth = showRestorePrevious ? BUTTON_WIDTH + BUTTON_GAP + RESTORE_BUTTON_WIDTH : BUTTON_WIDTH;
            int createX = leftPanelX + panelWidth / 2 - (rowWidth / 2);
            this.createPartyButton.x = createX;
            if (this.restorePreviousPartyButton != null) {
                this.restorePreviousPartyButton.visible = showRestorePrevious;
                this.restorePreviousPartyButton.active = showRestorePrevious;
                this.restorePreviousPartyButton.x = createX + BUTTON_WIDTH + BUTTON_GAP;
                this.restorePreviousPartyButton.y = this.createPartyButton.y;
            }
            // keep accept/decline positioned relative to create button
            if (this.acceptInviteButton != null) {
                this.acceptInviteButton.x = createX - (this.acceptInviteButton.getWidth() + BUTTON_GAP);
                this.acceptInviteButton.y = this.createPartyButton.y;
            }
            if (this.declineInviteButton != null) {
                this.declineInviteButton.x = createX + BUTTON_WIDTH + BUTTON_GAP;
                this.declineInviteButton.y = this.createPartyButton.y;
            }
        } else if (this.restorePreviousPartyButton != null) {
            this.restorePreviousPartyButton.visible = false;
            this.restorePreviousPartyButton.active = false;
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
            this.inviteNearbyButton.visible = showInviteActions;
            this.inviteNearbyButton.active = showInviteActions;
        }
        if (this.inviteAllButton != null) {
            this.inviteAllButton.visible = showInviteActions;
            this.inviteAllButton.active = showInviteActions;
        }
        if (this.inviteFavoritesButton != null) {
            this.inviteFavoritesButton.visible = showInviteActions;
            this.inviteFavoritesButton.active = showInviteActions && (inParty ? this.controller.hasInviteableFavorites() : this.controller.hasOnlineFavorites());
        }
        if (this.inviteVoiceGroupButton != null) {
            this.inviteVoiceGroupButton.visible = showInviteActions && VoiceChatIntegration.hasLocalVoiceGroup();
            this.inviteVoiceGroupButton.active = this.inviteVoiceGroupButton.visible && (inParty ? this.controller.hasInviteableVoiceGroupPlayersForParty(voiceGroupRowsNotInParty()) : !VoiceChatIntegration.getLocalVoiceGroupMemberIds().isEmpty());
        }
        if (this.invitePartyVoiceGroupButton != null) {
            this.invitePartyVoiceGroupButton.visible = inParty && VoiceChatIntegration.isVoiceChatLoaded();
            this.invitePartyVoiceGroupButton.active = this.invitePartyVoiceGroupButton.visible && this.controller.hasPartyMembersAvailableForVoiceInvite();
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

    private boolean shouldShowPendingInviteActions() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        return ClientPartyInviteState.hasPendingInvite()
                && this.currentParty == null
                && !ClientPartySettings.shouldAutoAcceptInvite(client, ClientPartyInviteState.getInviterName());
    }

    private void renderActionGroupOutlines(@Nonnull PoseStack poseStack) {
        Objects.requireNonNull(poseStack, "poseStack");
        if (this.controller.isLocalPlayerInParty()) {
            Button lastManageButton = this.leaveVoiceGroupButton != null && this.leaveVoiceGroupButton.visible
                    ? this.leaveVoiceGroupButton
                    : this.disbandPartyButton;
            renderButtonGroupOutline(poseStack, this.leavePartyButton, lastManageButton, new TranslatableComponent("screen.vaultpartyui.manage_party"));
        }

        Button lastInviteButton = this.inviteVoiceGroupButton != null && this.inviteVoiceGroupButton.visible
                ? this.inviteVoiceGroupButton
                : this.inviteFavoritesButton;
        renderButtonGroupOutline(poseStack, this.inviteNearbyButton, lastInviteButton, new TranslatableComponent("screen.vaultpartyui.invite_group"));
    }

    private void renderButtonGroupOutline(@Nonnull PoseStack poseStack, @Nullable Button firstButton, @Nullable Button lastButton, @Nonnull Component label) {
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
        String text = Objects.requireNonNull(label.getString());
        Font screenFont = Objects.requireNonNull(this.font, "font");
        int labelWidth = screenFont.width(text);
        int labelX = x1 + (x2 - x1) / 2 - labelWidth / 2;
        fill(poseStack, labelX - 3, y1 - 4, labelX + labelWidth + 3, y1 + 7, 0xFF111111);
        screenFont.draw(poseStack, text, labelX, y1 - 3, 0xE3C38C);
    }

    private Component autoAcceptToggleLabel() {
        return Objects.requireNonNull(new TranslatableComponent(Objects.requireNonNull(ClientPartySettings.getAutoAcceptMode().getLabelKey())));
    }

    private void updateAutoAcceptToggleLabel() {
        if (this.autoAcceptToggleButton != null) {
            this.autoAcceptToggleButton.setMessage(Objects.requireNonNull(autoAcceptToggleLabel()));
        }
    }

    private void renderPartyPanel(@Nonnull PoseStack poseStack, int panelX, int panelWidth, int mouseX, int mouseY) {
        PartyPanelRenderState state = PartyPanelRenderer.render(
                poseStack,
                Objects.requireNonNull(this.font),
                panelX,
                panelWidth,
                mouseX,
                mouseY,
                this.currentParty,
                sortedPartyMembers(),
                voiceGroupRowsNotInParty(),
                this.partyScrollOffset,
                this.controller.isPartyLeader(),
                this.controller::canInvitePartyMemberToVoice,
                this.controller::canJoinPartyMemberVoiceGroup,
                this::queueTooltip
        );
        this.partyScrollOffset = state.scrollOffset;
    }

    private void renderOnlinePanel(@Nonnull PoseStack poseStack, int panelX, int panelWidth, int mouseX, int mouseY) {
        OnlinePanelRenderState state = PartyOnlinePanelRenderer.render(
                poseStack,
                Objects.requireNonNull(this.font),
                panelX,
                panelWidth,
                mouseX,
                mouseY,
                filteredOnlineRows(),
                this.onlineScrollOffset,
                this.selectedOnlineIndex,
                this.controller.isPartyLeader(),
                this::queueTooltip
        );
        this.onlineScrollOffset = state.scrollOffset;
        this.selectedOnlineIndex = state.selectedIndex;
    }

    private void queueTooltip(@Nonnull Component tooltip) {
        this.queuedTooltip = tooltip;
    }

    private void renderQueuedTooltip(@Nonnull PoseStack poseStack, int mouseX, int mouseY) {
        Component tooltip = this.queuedTooltip;
        if (tooltip != null) {
            renderTooltip(poseStack, tooltip, mouseX, mouseY);
        }
    }

    private void renderRestorePreviousTooltip(@Nonnull PoseStack poseStack, int mouseX, int mouseY) {
        if (!isButtonHovered(this.restorePreviousPartyButton, mouseX, mouseY)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(new TranslatableComponent("screen.vaultpartyui.restore_previous_tooltip"));
        for (PreviousPartyMember member : PreviousPartySnapshot.members()) {
            tooltip.add(new TextComponent("- " + member.name));
        }
        renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
    }

    private static boolean isButtonHovered(@Nullable Button button, int mouseX, int mouseY) {
        return button != null
                && button.visible
                && mouseX >= button.x
                && mouseX <= button.x + button.getWidth()
                && mouseY >= button.y
                && mouseY <= button.y + BUTTON_HEIGHT;
    }

    private boolean isInsideOnlinePanel(double mouseX, double mouseY) {
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int rightPanelX = 20 + panelWidth + PANEL_PADDING;
        return PartyOnlinePanelRenderer.isInsideList(mouseX, mouseY, rightPanelX, panelWidth);
    }

    private boolean handleOnlinePlayerClick(double mouseX, double mouseY) {
        if (!isInsideOnlinePanel(mouseX, mouseY)) {
            return false;
        }

        int panelX = 20 + (this.width - 40 - PANEL_PADDING) / 2 + PANEL_PADDING;
        int listTop = PartyOnlinePanelRenderer.listTop();
        int relativeY = (int)mouseY - listTop - 4;
        int index = this.onlineScrollOffset + (relativeY / ONLINE_ROW_HEIGHT);
        List<OnlineRow> visiblePlayers = filteredOnlineRows();
        if (index < 0 || index >= visiblePlayers.size()) {
            return false;
        }

        this.selectedOnlineIndex = index;

        OnlineRow row = visiblePlayers.get(index);
        OnlinePlayer player = row.player;
        if (player == null) {
            return true;
        }
        int actionY = PartyOnlinePanelRenderer.rowY(index, this.onlineScrollOffset);
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        int actionX = PartyOnlinePanelRenderer.actionX(panelX, panelWidth);
        int starX = panelX + 12;
        if (PartyOnlinePanelRenderer.isFavoriteToggleHovered(mouseX, mouseY, starX, actionY)) {
            ClientFavoritePlayers.toggleFavorite(player.id);
            pushToast(new TranslatableComponent(row.favorite ? "screen.vaultpartyui.toast_favorite_removed" : "screen.vaultpartyui.toast_favorite_added", player.name), 0xE3C38C);
            return true;
        }
        if (mouseX >= actionX && mouseX <= actionX + 104 && mouseY >= actionY && mouseY <= actionY + ONLINE_ROW_HEIGHT - 2) {
            this.controller.performPrimaryRowAction(row);
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
        int actionX = PartyPanelRenderer.actionX(leftPanelX, panelWidth);
        if (mouseX < actionX || mouseX > actionX + PartyPanelRenderer.ACTION_WIDTH) {
            return true;
        }

        int relativeY = (int)mouseY - PartyPanelRenderer.listTop();
        int rowIndex = this.partyScrollOffset + (relativeY / PartyPanelRenderer.ROW_HEIGHT);
        List<UUID> members = sortedPartyMembers();
        List<OnlineRow> voiceRows = voiceGroupRowsNotInParty();
        if (rowIndex < 0 || rowIndex >= PartyPanelRenderer.totalRows(members, voiceRows)) {
            return true;
        }

        if (rowIndex < members.size()) {
            UUID memberId = members.get(rowIndex);
            if (this.controller.canInvitePartyMemberToVoice(memberId)) {
                this.controller.invitePlayerToVoiceGroup(memberId);
            } else if (this.controller.canJoinPartyMemberVoiceGroup(memberId)) {
                this.controller.joinPartyMemberVoiceGroup(memberId);
            }
            return true;
        }

        if (!voiceRows.isEmpty()) {
            int voiceRowIndex = rowIndex - members.size();
            if (voiceRowIndex > 0) {
                int listIndex = voiceRowIndex - 1;
                if (listIndex >= 0 && listIndex < voiceRows.size() && voiceRows.get(listIndex).state == RowState.INVITEABLE) {
                    this.controller.performPrimaryRowAction(voiceRows.get(listIndex));
                }
            }
        }

        return true;
    }

    private boolean isInsidePartyPanel(double mouseX, double mouseY) {
        int panelWidth = (this.width - 40 - PANEL_PADDING) / 2;
        return mouseX >= 20 + 8 && mouseX <= 20 + panelWidth - 8 && mouseY >= PANEL_TOP && mouseY <= PANEL_TOP + PANEL_HEIGHT;
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
            return PartyPlayerLookup.resolvePlayerName(a).compareToIgnoreCase(PartyPlayerLookup.resolvePlayerName(b));
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
                PartyPlayerLookup.getLocalPlayerId(),
                this.inviteCooldownUntilMs
        );
    }

    private void ensureSelectedRowVisible(int rowCount) {
        if (rowCount <= 0 || this.selectedOnlineIndex < 0) return;
        if (this.selectedOnlineIndex < this.onlineScrollOffset) {
            this.onlineScrollOffset = this.selectedOnlineIndex;
        }
        int visibleRows = PartyOnlinePanelRenderer.visibleRows();
        int maxVisible = this.onlineScrollOffset + visibleRows - 1;
        if (this.selectedOnlineIndex > maxVisible) {
            this.onlineScrollOffset = this.selectedOnlineIndex - visibleRows + 1;
        }
        int maxOffset = Math.max(0, rowCount - visibleRows);
        this.onlineScrollOffset = Mth.clamp(this.onlineScrollOffset, 0, maxOffset);
    }

    private int maxPartyScrollOffset() {
        return Math.max(0, PartyPanelRenderer.totalRows(sortedPartyMembers(), voiceGroupRowsNotInParty()) - PartyPanelRenderer.visibleRows());
    }

    private void pruneTransientState() {
        long now = System.currentTimeMillis();
        PartyRosterService.pruneCooldowns(this.inviteCooldownUntilMs, now);

        PartyToastService.prune(this.toasts, now);
    }

    private void pushToast(@Nullable Component message, int color) {
        PartyToastService.push(this.toasts, message, color);
    }

    private void showClientMessage(@Nullable Component message) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player != null && message != null) {
            player.displayClientMessage(message, false);
        }
    }

}
