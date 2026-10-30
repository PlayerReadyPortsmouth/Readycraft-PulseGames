# PulseGames admin menu and AnvilPrompt (30 Oct 2026)

Branch `feat/pulsegames-admin-menu` from `origin/main` b3f9c28 (0.9.6). Draft; no version bump, nothing deployed.

## What
- `core/ui/AnvilPrompt` (+ pure `AnvilPromptState`): text input. Java: anvil with a paper named after the prompt; rename it and click the result. Bedrock: Cumulus custom-form input (`Forms.input`, guarded like the other forms). Bad answer keeps it open with a message (Bedrock re-sends the form with the reason first); closing cancels; the anvil is emptied before it closes so no paper is returned. Max 50 characters (the anvil limit). Register `AnvilPrompt.Events` (done in `PulseGamesPlugin`).
- `/pulse adminmenu` (needs `pulsegames.admin`, the `/pulse` permission). One screen model (`AdminScreen`) drawn as a 54-slot chest menu (Java) or a button form (Bedrock) by `AdminUi`; confirms use `Forms.confirm` or the chest `Confirm`.
  - Games and arenas: per game, maps with on/off state; turn off/on (confirm) moves the file to/from `arenas/disabled/` and calls `ArenaService.load()`; bots per map writes `settings.bots-enabled`; start now / end match for instances on that map.
  - Matches running: players, spectate (`GameInstance.watch`), start, end (confirm).
  - Players: online list or AnvilPrompt name; view progression, set level, add XP, reset (confirm), 16-style grid with grant/revoke and grant/revoke all. All go through `ProgressionAdminCommand.handle` (#86), so its own `PROGRESSION` audit line also appears.
  - Events: schedule list, start the next one now (`EventService.admin start`), skip next (new small `EventService.skipNext()` / `lastOpened()`).
  - Bots: server difficulty (writes `bots.difficulty` and saves config), diagnose shortcut.
  - Server: reload (confirm), map board, lobby placements; these dispatch the existing `/pulse ...` commands as the admin.
- Audit: `PGADMIN <actor> <action> <target>` on the console log (`AdminAudit`).

## Chat prompts converted
`ArenaSetupMenu` (setup GUI): display name, route name, min/max players, per-mode capacity, team count now use AnvilPrompt (`ask`). The item-pool prompt stays on chat because comma-separated material lists exceed the 50-character anvil limit; typing `cancel` is no longer needed on the others (close the anvil).

## Not done / notes
- No config keys, no new permissions.
- Arena toggle edits files by scanning `arenas/` and `arenas/disabled/` for matching `id`/`game`; a running match on a disabled map carries on.
- Bots-per-map and difficulty rewrite YAML via Bukkit (comments are kept on Paper 1.21+).
- Not run on a live server; AnvilPrompt written against the 1.21.4 `AnvilView` API.

## Acceptance (Java and Bedrock)
- `/pulse adminmenu`: six sections; Events hidden when `events.enabled: false`.
- Turn a map off: it vanishes from queues after confirm, file sits in `arenas/disabled/`; turn on restores it.
- Set level 10 for a player: anvil appears, "abc" is refused and the anvil stays, "10" applies and the player screen returns; closing the anvil cancels and returns; no paper left in inventory (check after Esc, shift-click, number keys).
- End match asks first. Spectate joins a running round. Console shows one `PGADMIN` line per action.
- Bedrock: every screen is a button form; text asks open an input box.
