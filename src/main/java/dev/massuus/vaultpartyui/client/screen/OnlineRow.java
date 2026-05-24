package dev.massuus.vaultpartyui.client.screen;

final class OnlineRow {
    final OnlinePlayer player;
    final RowState state;

    OnlineRow(OnlinePlayer player, RowState state) {
        this.player = player;
        this.state = state;
    }
}
