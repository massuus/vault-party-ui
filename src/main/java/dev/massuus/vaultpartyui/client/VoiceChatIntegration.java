package dev.massuus.vaultpartyui.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.fml.ModList;

public final class VoiceChatIntegration {
    private static final String VOICECHAT_MODID = "voicechat";
    private static final String DEFAULT_GROUP_NAME = "Vault Party";

    private VoiceChatIntegration() {
    }

    public static boolean isVoiceChatLoaded() {
        return ModList.get().isLoaded(VOICECHAT_MODID);
    }

    public static boolean hasLocalVoiceGroup() {
        return !getLocalVoiceGroupMemberIds().isEmpty();
    }

    public static boolean isPlayerInLocalVoiceGroup(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return getLocalVoiceGroupMemberIds().contains(playerId);
    }

    public static Set<UUID> getLocalVoiceGroupMemberIds() {
        if (!isVoiceChatLoaded()) {
            return Collections.emptySet();
        }

        try {
            Object stateManager = getPlayerStateManager();
            if (stateManager == null) {
                return Collections.emptySet();
            }

            UUID localGroupId = (UUID) stateManager.getClass().getMethod("getGroupID").invoke(stateManager);
            if (localGroupId == null) {
                return Collections.emptySet();
            }

            Set<UUID> members = new HashSet<>();
            UUID ownId = (UUID) stateManager.getClass().getMethod("getOwnID").invoke(stateManager);
            if (ownId != null) {
                members.add(ownId);
            }

            List<?> states = (List<?>) stateManager.getClass().getMethod("getPlayerStates", boolean.class).invoke(stateManager, true);
            if (states != null) {
                for (Object state : states) {
                    if (state == null) {
                        continue;
                    }
                    UUID playerId = (UUID) state.getClass().getMethod("getUuid").invoke(state);
                    UUID groupId = (UUID) state.getClass().getMethod("getGroup").invoke(state);
                    if (playerId == null || groupId == null) {
                        continue;
                    }
                    if (localGroupId.equals(groupId)) {
                        members.add(playerId);
                    }
                }
            }
            return members;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return Collections.emptySet();
        }
    }

    public static UUID getPlayerVoiceGroupId(UUID playerId) {
        if (!isVoiceChatLoaded() || playerId == null) {
            return null;
        }

        try {
            Object stateManager = getPlayerStateManager();
            if (stateManager == null) {
                return null;
            }

            Object state = stateManager.getClass().getMethod("getState", UUID.class).invoke(stateManager, playerId);
            if (state == null) {
                return null;
            }
            return (UUID) state.getClass().getMethod("getGroup").invoke(state);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    public static boolean joinVoiceGroup(UUID groupId, String password) {
        if (!isVoiceChatLoaded() || groupId == null) {
            return false;
        }

        try {
            Class<?> packetClass = Class.forName("de.maxhenkel.voicechat.net.JoinGroupPacket");
            Object packet = packetClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(groupId, normalizePassword(password));
            Class<?> packetInterface = Class.forName("de.maxhenkel.voicechat.net.Packet");
            Class<?> netManagerClass = Class.forName("de.maxhenkel.voicechat.net.ClientServerNetManager");
            netManagerClass.getMethod("sendToServer", packetInterface).invoke(null, packet);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean createVoiceGroup(String password, VoiceGroupType type) {
        if (!isVoiceChatLoaded()) {
            return false;
        }

        try {
            VoiceGroupType safeType = type == null ? VoiceGroupType.OPEN : type;
            Class<?> groupTypeClass = Class.forName("de.maxhenkel.voicechat.api.Group$Type");
            Object groupType = groupTypeClass.getField(safeType.voicechatName).get(null);
            Class<?> packetClass = Class.forName("de.maxhenkel.voicechat.net.CreateGroupPacket");
            Object packet = packetClass
                    .getConstructor(String.class, String.class, groupTypeClass)
                    .newInstance(DEFAULT_GROUP_NAME, normalizePassword(password), groupType);
            Class<?> packetInterface = Class.forName("de.maxhenkel.voicechat.net.Packet");
            Class<?> netManagerClass = Class.forName("de.maxhenkel.voicechat.net.ClientServerNetManager");
            netManagerClass.getMethod("sendToServer", packetInterface).invoke(null, packet);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static String normalizePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return null;
        }
        return password.trim();
    }

    private static Object getPlayerStateManager() throws ReflectiveOperationException {
        Class<?> clientManagerClass = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
        return clientManagerClass.getMethod("getPlayerStateManager").invoke(null);
    }

    public enum VoiceGroupType {
        OPEN("OPEN", "screen.vaultpartyui.voice_group_type_open"),
        NORMAL("NORMAL", "screen.vaultpartyui.voice_group_type_normal"),
        ISOLATED("ISOLATED", "screen.vaultpartyui.voice_group_type_isolated");

        private final String voicechatName;
        private final String labelKey;

        VoiceGroupType(String voicechatName, String labelKey) {
            this.voicechatName = voicechatName;
            this.labelKey = labelKey;
        }

        public String getLabelKey() {
            return labelKey;
        }

        public VoiceGroupType next() {
            VoiceGroupType[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
