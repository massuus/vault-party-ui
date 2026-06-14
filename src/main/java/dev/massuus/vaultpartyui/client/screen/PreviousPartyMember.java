package dev.massuus.vaultpartyui.client.screen;

import java.util.UUID;

final class PreviousPartyMember {
    final UUID id;
    final String name;

    PreviousPartyMember(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
