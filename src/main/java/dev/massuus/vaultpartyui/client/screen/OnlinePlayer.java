package dev.massuus.vaultpartyui.client.screen;

import java.util.UUID;

final class OnlinePlayer {
    final UUID id;
    final String name;

    OnlinePlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
