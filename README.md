# Vault Party UI

Client-side Forge mod for Vault Hunters (Minecraft 1.18.2) that provides a party management screen.

## Features

- Open a party UI with a keybind (default: `I`, rebindable in Controls).
- Create/leave/disband party actions.
- Invite nearby/all actions.
- Invite handling (accept/decline) when not already in a party.
- Auto-accept modes: off, favorites only, or all.
- Party member list panel.
- Online player list with per-player `Invite` / `Remove` actions.
- Invite Favorites quick action for every available favorite player.
- Optional keybinds for create/invite-all, create/invite-favorites, and invite-nearby.

## Changelog

### 1.3

- Added persisted auto-accept modes: off, favorites only, and all.
- Added optional keybinds for quick party actions.
- Added a confirmation dialog before disbanding a party.
- Added clickable version and creator credit links in the party screen.
- Added built-in website confirmation flow that closes correctly after open or cancel.
- Swapped the party member HP suffix for a heart glyph.

### 1.2.2

- Removed Runtime.getRuntime() fallback.

### 1.2.1

- Fixed the accept and decline invite buttons so they sit correctly around the Create Party button.

### 1.2.0

- Added favorite players with a clickable star in the online player list.
- Added Invite Favorites to invite every available favorite player at once.
- Reworked the party screen layout to better fit the top controls and both list panels.
- Improved the offline player label shown in the party list.

### 1.1.0

- Added auto-accept invites toggle that works even when the screen is closed.
- Added player heads beside party and online player names.
- Improved row states, invite cooldown handling, and inline UI feedback.
- Refined layout spacing and removed the extra filter button/context text.
- Split party screen logic into helper classes to keep the screen file smaller.

### 1.0.0

- Initial release of the Vault Party UI.
- Added the party management screen, keybind, and core invite/remove actions.

## Requirements

- Minecraft `1.18.2`
- Forge `40.x`
- Vault Hunters modpack (client)

## Development

Build in workspace root:

```powershell
.\gradlew.bat build
```

Compile only:

```powershell
.\gradlew.bat compileJava
```

## Deploy To CurseForge Instance

A helper script is included:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy.ps1
```

Or use VS Code task: `build-and-deploy`.

## Notes

- This mod is client-side UI logic.
- Server still enforces party rules and command permissions.
- For real testing, run it inside the Vault Hunters pack client instance.
