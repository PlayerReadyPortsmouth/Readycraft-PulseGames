# Readycraft-PulseGames

Single Paper plugin (Java 21, Gradle) that runs the whole Readycraft minigames network:
21 games, multiple simultaneous instances on one server, each round in a throwaway clone
of a map template. No database — everything is YAML/JSON under `plugins/PulseGames/`.

## Build

```bash
./gradlew build          # Windows: .\gradlew.bat build
# output: build/libs/PulseGames-0.1.0.jar  (version from gradle.properties pluginVersion)
```

Needs Java 21 and network access to `repo.papermc.io`. Compiled against
`paper-api:1.21.4-R0.1-SNAPSHOT` (`gradle.properties`). There is **no test suite** —
verification is running the jar on a server.

## Architecture

- `src/main/java/uk/co/playerready/pulsegames/PulseGamesPlugin.java` — wires all services in `onEnable`.
- `core/` — engine. Key services: `game/InstanceManager` + `game/GameInstance` (state machine
  `GameState`: LOADING→WAITING→COUNTDOWN→RUNNING→ENDING→RESETTING), `world/WorldService`
  (clone template → `pulse_<id>` world, void generator,
  delete after game, purge leftovers on boot), `arena/ArenaService`, `party/`, `lobby/`,
  `economy/` (Pulse Tokens + cosmetics shop), `setup/ArenaSetupManager` (in-game `/pulse setup`),
  `setup/MapBuilder` (headless blueprint builds), `shapes/ShapeService` (JSON geometry primitives).
- `games/` — one package per game, each a `MiniGame` subclass. **All games are registered in
  `games/GameCatalog.java`** with their modes and setup requirements — new game = subclass
  `MiniGame`, register there; the engine handles lifecycle, teams, scoreboards, stats.
- `src/main/resources/` — `plugin.yml` (commands), `config.yml`, `kits.yml`, plus example
  `arenas/`, `shapes/`, `blueprints/` shipped as defaults.
- `docs/MAP-SETUP.md` — the canonical per-game arena requirements (spawn counts, region
  names like `floor1`, `checkpoint1..N`, `bed1..N`, required `void-y` setting) and the
  shape-primitive reference (box/cylinder/sphere/dome/pillars/ring/checker/scatter).

## Two ways to make maps

1. **In-game**: `/pulse setup start <game> <arenaId>` → wand + checklist → `/pulse setup save`.
   Saves the world template to `plugins/PulseGames/maps/<arenaId>/` and the definition to
   `plugins/PulseGames/arenas/<game>-<arenaId>.yml`. See `docs/MAP-SETUP.md`.
2. **Headless blueprint pipeline** (`core/setup/MapBuilder.java`): drop a JSON into
   `plugins/PulseGames/blueprints/<name>.json`, then run `pulse build <name>` from the
   **server console** (no player needed); `pulse blueprints` lists them. Format: `shapes`
   coords are offsets from `origin`; `lobby`/`spectator`/`spawns`/`regions` are ABSOLUTE
   world coords (documented in MapBuilder's header comment). Working examples live in
   `blueprints/` at the repo root — copy one as a starting point.
   **Build one map at a time**: MapBuilder holds a `building` flag and rejects overlapping
   builds because concurrent builds raced on chunk-saving and produced empty templates.

## Deployment (prod: VPS Minigames server via Crafty)

Not in this repo — full runbook in project memory `pulsegames-deploy-and-maps.md`
(see also `readyapp-vps-access.md`). Short version, last verified Jun 2026:

- Prod = Crafty 4 server "Minigames" on the ReadyApp VPS,
  `/var/opt/minecraft/crafty/crafty-4/servers/25c213df-70c3-4dd6-b746-6cbcd3464c74/`,
  `paper-1.21.4-232.jar`, MC port 30068, runs as user `crafty`.
- Deploy: build locally → scp jar to `plugins/` → `chown crafty:crafty` → restart.
  No PlugMan; a jar swap needs a full server restart.
- Restart gotcha: Crafty's API enforces MFA (`superMFA: true`). The memory note has the
  temp-TOTP-row trick, plus the simpler console-injection alternative:
  `echo "cmd" > /proc/<paper-pid>/fd/0` runs a console command directly.
- Prod lobby world is `Arcade` (there is no world literally named "world";
  `config.yml` lobby.world must be set accordingly).

## Local test server

`C:\Users\Aura\Documents\Ready\pulse-test-server\` — Paper 1.21.4 (matches prod),
offline mode. Run `start.bat`, connect a 1.21.4 client to `localhost`. Redeploy by
copying `build/libs/PulseGames-0.1.0.jar` into its `plugins/`. Solo testing:
`/play <game>` then `/pulse start` to force-start; karts plays fully solo, PvP games
need a second client to end naturally.

## Gotchas

- **Karts = boats**: a mounted player fires no `PlayerMoveEvent`; `GameListener.onVehicleMove`
  routes the passenger's movement to the game logic (checkpoints, item boxes, void falls).
  Keep that path intact when touching movement handling. Track surfaces must be ice or
  boats crawl (`blueprints/karts-circuit.json` uses BLUE_ICE).
- **Mounted players can't be teleported**: `LobbyService.sendToLobby` and
  `WorldService` dismount (`leaveVehicle()`) first, and instance worlds are only deleted
  after they actually unload — deleting under a loaded world makes Paper spam
  `Failed to save level` every tick.
- Spawn order = team order in team games (spawn 1 → Red, spawn 2 → Blue …).
- `plugin.yml` version is injected from Gradle (`processResources` expands `${version}`) —
  bump `pluginVersion` in `gradle.properties`, not the yml.
- Admin permission is `pulsegames.admin`; main admin entrypoint is
  `core/command/Commands.java` (`/pulse …`).

## How to work in this repo

Shared engineering practice lives in `~/Documents/Ready/ready-docs/engineering/` — read the relevant doc before starting that kind of work:

- [working-style.md](../ready-docs/engineering/working-style.md) — any task: scout first, right altitude, verify end-to-end, report outcome-first.
- [bug-hunting.md](../ready-docs/engineering/bug-hunting.md) — bugs: reproduce → root cause → fix the cause → regression test → verify.
- [coding-standards.md](../ready-docs/engineering/coding-standards.md) — style, comments, error handling, tests, dependencies.
- [shipping.md](../ready-docs/engineering/shipping.md) — feature-flag + canary rule, deploy conventions, ready-to-enable checklist.
- [agents-and-parallelism.md](../ready-docs/engineering/agents-and-parallelism.md) — when to use subagents; structure independent work to run in parallel.

**Repo-specific:**

- **No test harness.** Verification = `./gradlew build` clean, copy the jar to the local
  test server, and actually play the affected flow (queue, start, win/lose, return to
  lobby). The regression check for a bug fix is a documented repro on that server:
  failing before, passing after.
- **New games/mechanics** go into `GameCatalog` following the existing `MiniGame`
  pattern — ship as a new game/mode rather than altering live games' behaviour.
- **Engine changes touch all 21 games** — test at least one game per affected hook.
- **House style here:** one service per concern, wired in `PulseGamesPlugin.onEnable`;
  Adventure MiniMessage via `Text.msg`.
