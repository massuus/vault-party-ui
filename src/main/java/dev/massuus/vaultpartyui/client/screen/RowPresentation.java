package dev.massuus.vaultpartyui.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

final class RowPresentation {
    private RowPresentation() {
    }

    static int backgroundColor(RowState state, boolean hovered, boolean selected) {
        if (selected) {
            return 0x66594A2D;
        }
        if (hovered) {
            return 0x663C3122;
        }
        if (state == RowState.OTHER_PARTY) {
            return 0x221A1A1A;
        }
        return 0x00000000;
    }

    static int nameColor(RowState state) {
        if (state == RowState.OTHER_PARTY) return 0xC8B9A3;
        if (state == RowState.NO_ACTION) return 0xBBBBBB;
        return 0xFFFFFF;
    }

    static int actionColor(RowState state) {
        if (state == RowState.INVITEABLE) return 0xA0E0A0;
        if (state == RowState.PARTY_MEMBER) return 0xE0A0A0;
        if (state == RowState.COOLDOWN) return 0xA0A0A0;
        if (state == RowState.OTHER_PARTY) return 0xE3C38C;
        return 0x909090;
    }

    static Component actionLabel(OnlineRow row, boolean isPartyLeader) {
        if (row == null) return null;
        switch (row.state) {
            case INVITEABLE:
                return new TranslatableComponent("screen.vaultpartyui.invite");
            case PARTY_MEMBER:
                return isPartyLeader ? new TranslatableComponent("screen.vaultpartyui.remove") : new TranslatableComponent("screen.vaultpartyui.in_your_party");
            case OTHER_PARTY:
                return new TranslatableComponent("screen.vaultpartyui.in_party");
            case COOLDOWN:
                return new TranslatableComponent("screen.vaultpartyui.invited");
            case SELF:
                return new TranslatableComponent("screen.vaultpartyui.self");
            default:
                return null;
        }
    }

    static Component tooltip(OnlineRow row, boolean isPartyLeader) {
        if (row == null) return null;
        switch (row.state) {
            case INVITEABLE:
                return new TranslatableComponent("screen.vaultpartyui.tip_invite");
            case PARTY_MEMBER:
                return isPartyLeader ? new TranslatableComponent("screen.vaultpartyui.tip_remove") : new TranslatableComponent("screen.vaultpartyui.tip_member");
            case OTHER_PARTY:
                return new TranslatableComponent("screen.vaultpartyui.tip_other_party");
            case COOLDOWN:
                return new TranslatableComponent("screen.vaultpartyui.tip_cooldown");
            case NO_ACTION:
                return new TranslatableComponent("screen.vaultpartyui.tip_no_action");
            default:
                return null;
        }
    }
}
