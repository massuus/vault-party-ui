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
*   Optional Simple Voice Chat integration when the voice chat mod is installed.
*   Invite your voice group to your Vault party, invite your party to voice chat, and see microphone icons for players in your current voice group.

## Why use it?

If you play Vault Hunters with friends, this mod makes party management much less annoying. Instead of constantly typing commands, you get a dedicated UI for the most common party actions, plus favorites, auto-accept and quick actions  make repeated party setup faster.

## Requirements

*   Minecraft 1.18.2
*   Forge 40.x
*   Vault Hunters modpack

## Notes

*   This mod is client-side only.
*   The server still controls party permissions and final command behavior.
*   Simple Voice Chat support is optional. Vault Party UI can still run when the voice chat mod is not installed.

## Changelog

### 1.5.1

*   Added scrollable party and online player lists so large parties no longer overflow the panels.
*   Added an in-memory player name and skin cache so offline party members keep their last seen name/head.
*   Offline party members now render gray and show `[Offline]`.

### 1.5

*   Added a Restore Previous button next to Create Party that recreates the last remembered party and re-invites previous members.
*   Added a hover preview for Restore Previous showing who will be invited.
*   Added a footer update indicator that checks GitHub releases and shows an Update available pill when a newer version exists.
*   Updated footer links so the version opens the CurseForge mod page and Made by Massuus opens the CurseForge creator page.
*   Split more party screen behavior into smaller renderer, controller, footer, snapshot, and update-checker helpers.

### 1.4

*   Added optional Simple Voice Chat integration.
*   Added voice group actions for inviting voice chat members to your Vault party.
*   Added party-to-voice-chat invites, including a create voice group screen when you are not already in a voice group.
*   Added microphone indicators for players in your current voice group.
*   Reworked the party panel so party members in your voice group are shown first.
*   Added per-player actions to invite party members to voice chat or voice group members to the Vault party.
*   Updated the top party controls with grouped Manage Party and Invite sections.

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
