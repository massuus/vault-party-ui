# Vault Party UI

Vault Party UI is a client-side Forge mod for Vault Hunters 1.18.2 that adds a fast, focused party management screen. It reduces chat command spam and keeps the most common party actions in one place.

By default press `I` to open the UI. It can be changed in the key-bind settings.

## Features

*   Open a party UI with a keybind instead of typing `/party` commands by hand.
*   Create, leave, and disband parties.
*   Invite nearby players, invite everyone nearby, or invite favorites.
*   Handle incoming party invites with accept and decline buttons.
*   Keep track of current party members and online players in one screen.
*   Invite or remove players directly from the player list.
*   Mark favorite players with a star and send favorite-only invites.
*   Use an auto-accept invites toggle for faster party joins.
*   Auto-accept can be set to off, favorites only, or all.
*   Invite Favorites quick action for every available favorite player.
*   Optional keybinds for create/invite-all, create/invite-favorites, and invite-nearby.

## Why use it?

If you play Vault Hunters with friends, this mod makes party management much less annoying. Instead of constantly typing commands, you get a dedicated UI for the most common party actions, plus favorites, auto-accept and quick actions  make repeated party setup faster.

## Requirements

*   Minecraft 1.18.2
*   Forge 40.x
*   Vault Hunters modpack

## Notes

*   This mod is client-side only.
*   The server still controls party permissions and final command behavior.
*   For best results, test it inside the Vault Hunters pack client.

## Changelog

### 1.3

*   Added persisted auto-accept modes: off, favorites only, and all.
*   Added optional keybinds for quick party actions.
*   Added a confirmation dialog before disbanding a party.
*   Added clickable version and creator credit links in the party screen.
*   Added built-in website confirmation flow that closes correctly after open or cancel.
*   Swapped the party member HP suffix for a heart glyph.

### 1.2.2

*   Removed the `Runtime.getRuntime()` fallback from the external link handler.

### 1.2.1

*   Fixed the accept and decline invite buttons so they sit correctly around the Create Party button.

### 1.2.0

*   Added favorite players with a clickable star in the online player list.
*   Added Invite Favorites to invite every available favorite player at once.
*   Reworked the party screen layout to better fit the top controls and both list panels.
*   Improved the offline player label shown in the party list.

### 1.1.0

*   Added auto-accept invites toggle that works even when the screen is closed.
*   Added player heads beside party and online player names.
*   Improved row states, invite cooldown handling, and inline UI feedback.
*   Refined layout spacing and removed the extra filter button/context text.
*   Split party screen logic into helper classes to keep the screen file smaller.

### 1.0.0

*   Initial release of Vault Party UI.
*   Added the party management screen, keybind, and core invite/remove actions.
