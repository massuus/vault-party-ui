# Vault Party UI

Client-side Forge mod for Vault Hunters (Minecraft 1.18.2) that provides a party management screen.

## Features

- Open a party UI with a keybind (default: `I`, rebindable in Controls).
- Create/leave/disband party actions.
- Invite nearby/all actions.
- Invite handling (accept/decline) when not already in a party.
- Party member list panel.
- Online player list with per-player `Invite` / `Remove` actions.

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
