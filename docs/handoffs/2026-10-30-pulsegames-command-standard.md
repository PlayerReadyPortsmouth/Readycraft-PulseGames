# PulseGames command standard (stream B), 30 Oct 2026

Implements stream B of `ops/readycraft/command-standard-2026-10-30.md`. No version bump.

## What changed
- `core/command/AdminRoutes` owns every admin verb, addressed as area `games`. Standard names: `types` (was `games`), `matches` (was `list`), `progress xp|level|namestyle|progression` (was bare), everything else unchanged. `/pulse results` (duplicate of `/results`) is gone.
- Hidden route command for the hub: **`admin-route <area> [args]`** (plugin.yml, permission `pulsegames.admin`). Areas: `games` (verbs; no verb opens the admin menu) and `server` (opens the admin menu). Tab-complete is empty for the first word. PulseAddon will use the same command name for `survival`, so the hub should dispatch the namespaced form `pulsegames:admin-route games ...` (Bukkit fallback prefix) to avoid ambiguity.
- `/pulse` kept as a hidden alias for one release: same handler, legacy verbs mapped, sends one line "/pulse is moving: use /admin games ...", no tab-complete. Delete in the release after the hub switch.
- `core/command/Routes` holds every dispatched command string (AdminMenu buttons, party/namestyle/results/event clicks). `ArenaSetupMenu` parses `Routes.SETUP_PREFIX` (`/admin games setup `). About 100 usage strings now say `/admin games ...`.
- plugin.yml: `pulsegames.admin` is now the parent of `.games .matches .players .events .bots .server .maps .setup` (all default false, children of the op parent). Declared only: verbs still check `pulsegames.admin`; per-area enforcement is a follow-up once the hub grants nodes (D2).
- D5: `/lobby` alias `leave` dropped. D4: `/wardrobe` untouched.

## Tests (1302 total, 0 failures)
`CommandStandardTest`: every dispatched string resolves to a plugin.yml command/alias (and `admin-route` area and verb exist; `namestyle` is PulseNetwork's), no `performCommand/dispatchCommand/runCommand/run_command:'/` literal outside `Routes`, no `/pulse ` text left, node tree declared, `/lobby leave` gone, legacy verb mapping, console verbs. `CommandsBotDiagnosticsTest`: route tab/permission, and the alias hint/hidden/still-runs test.

## For the hub (stream A)
Dispatch `admin-route games` (open menu) and `admin-route games <verb> ...`; the old `pulse adminmenu` still works until the alias is removed.

## In-game acceptance (Java and Bedrock, nobody online)
1. `/admin-route games` as admin opens the admin menu; as a non-admin it is refused.
2. Admin menu buttons Diagnose bots, Reload (confirm), Map readiness board, Lobby placements all work.
3. `/pulse bots` still works and prints the one-line hint; `/pulse` is not offered in tab-complete.
4. `/admin-route games setup start <game>` then the setup GUI checklist rows still run (prefix parse).
5. `/lobby` works, `/leave` no longer does. Party menu buttons, name style click in Wardrobe and Level menu, [Join] and [View results] chat clicks work.
