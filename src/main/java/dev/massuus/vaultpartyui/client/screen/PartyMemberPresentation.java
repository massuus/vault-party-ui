package dev.massuus.vaultpartyui.client.screen;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import iskallia.vault.client.data.ClientPartyData;
import iskallia.vault.client.data.ClientPartyData.PartyMember;
import iskallia.vault.world.data.VaultPartyData.Party;
import net.minecraft.network.chat.TranslatableComponent;

final class PartyMemberPresentation {
    private PartyMemberPresentation() {
    }

    static String line(@Nullable Party party, @Nullable UUID memberId, @Nullable UUID localPlayerId) {
        String memberName = PartyPlayerLookup.resolvePlayerName(memberId);
        StringBuilder line = new StringBuilder(memberName);
        if (party != null && memberId != null && memberId.equals(party.getLeader())) {
            line.append(" [").append(new TranslatableComponent("screen.vaultpartyui.leader").getString()).append("]");
        }
        if (memberId != null && memberId.equals(localPlayerId)) {
            line.append(" [").append(Objects.requireNonNull(new TranslatableComponent("screen.vaultpartyui.self").getString())).append("]");
        }
        if (memberId != null && !memberId.equals(localPlayerId) && !PartyPlayerLookup.isPlayerOnline(memberId)) {
            line.append(" [").append(new TranslatableComponent("screen.vaultpartyui.offline").getString()).append("]");
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

    static int color(@Nullable UUID memberId) {
        if (memberId != null && !PartyPlayerLookup.isPlayerOnline(memberId)) {
            return 0x808080;
        }

        PartyMember cachedMember = ClientPartyData.getCachedMember(memberId);
        return cachedMember == null ? 0xFFFFFF : statusColor(cachedMember.status);
    }

    private static String formatHealth(float hp) {
        return String.format(Locale.ROOT, "%.1f", hp);
    }

    private static int statusColor(@Nullable PartyMember.Status status) {
        if (status == null) {
            return 0xFFFFFF;
        }

        String statusName = status.name();
        if (statusName.equalsIgnoreCase("DEAD") || statusName.contains("DEAD")) {
            return 0xFF5555;
        }
        if (statusName.equalsIgnoreCase("DOWNED") || statusName.toLowerCase(Locale.ROOT).contains("down")) {
            return 0xFFAA00;
        }
        return 0xFFFFFF;
    }
}
