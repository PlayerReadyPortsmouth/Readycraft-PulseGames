# PulseGames

A complete multi-instance minigame engine for the Readycraft network, built as a single
Paper plugin. One Minecraft server runs many simultaneous games, each in its own
throwaway copy of a map world.

## Games (21)

| Game | `/play` id | Modes |
|---|---|---|
| Skywars | `skywars` | solo, doubles, insane |
| Bedwars | `bedwars` | duos, quads |
| Spleef | `spleef` | classic, decay, splegg |
| TNT Run | `tntrun` | classic, doublejump |
| Build Battle | `buildbattle` | classic, speed |
| Death Run | `deathrun` | classic, doubletrouble |
| Villager Defense | `villagerdefense` | easy, hard |
| ReadyPlayerZ (zombies) | `readyplayerz` | classic, frenzy |
| Party Games | `partygames` | classic |
| Volcano | `volcano` | classic, eruption |
| Parkour Race | `parkour` | sprint, elimination |
| Block Party | `blockparty` | classic, hardcore |
| Avalanche | `avalanche` | classic |
| Duels | `duels` | classic, op, sumo |
| Kit PvP | `kitpvp` | ffa, oitc |
| King of the Hill | `koth` | solo, teams |
| Lucky Pillars | `luckypillars` | classic, chaos |
| Prop Hunt (hide & seek) | `prophunt` | classic, infection |
| Elytra Racing | `elytra` | sprint, grandprix |
| Supermarket Sweep | `supermarket` | classic, rush |
| PulseKarts (kart racing) | `karts` | sprint, cup |

## Core features

- **Multiple instances on one server** - each round clones its map template into a
  fresh world (`pulse_<id>`), then deletes it afterwards. Crash leftovers are purged on boot.
- **Random map selection** - add any number of maps per game; matchmaking picks one at random.
- **Parties** - `/party invite|accept|leave|chat`; parties queue together and are kept on
  the same team.
- **Lobby + GUI** - compass menu to browse games and modes; `/play <game> [mode]` works too.
- **Per-game modes** - every game registers multiple gamemodes (team sizes, difficulty, twists).
- **Fully in-game arena setup** - `/pulse setup` with a selection wand, a requirement
  checklist (chat + chest GUI) and automatic world-template capture. No config editing needed.
- **Scoreboards, titles, sounds, particles, fireworks, kits, stats** (YAML-backed, swappable for a DB).
- **Pulse Tokens economy** - earn tokens for playing, winning, kills, playtime (every 15 min)
  and daily logins. Spend them in the token shop (`/shop` or the lobby sunflower) on
  **kill effects, victory effects, lobby trails, Kit PvP kit unlocks and 2x token boosters**.
- **Shape schematics** - generate arena geometry from JSON data files
  (`/pulse shape paste <file>`); see the map guide. Author maps as code, paste them in-game.

## Building

Requires Java 21 and network access to `repo.papermc.io`:

```bash
gradle build        # or: ./gradlew build once you add the wrapper
# output: build/libs/PulseGames-<version>.jar
```

Set the Paper API version for your server in `gradle.properties` (`paperApiVersion`).
This was developed against the 1.21 API; for a Paper build for Minecraft 26.1 set the
matching artifact version and bump `api-version` in `plugin.yml` if needed.

## Server setup (5 minutes)

1. Drop the jar in `plugins/` on a **dedicated minigames server** (keep survival separate).
2. Start once, stand at your hub spawn and run `/pulse setlobby`.
3. Build or import a map world, then follow [docs/MAP-SETUP.md](docs/MAP-SETUP.md)
   to register arenas entirely in-game.
4. Players join, grab the compass, pick a game. Done.

## Accessibility & SEN-friendly features

Built for a community that includes young people with special educational needs:

- **`/calm`** - calm mode per player, persisted: full-screen title flashes become quiet
  action-bar lines, loud effect sounds and particle bursts are skipped. Players opt in once.
- **`/practice <game> [mode]`** - a private solo instance that starts in 5 seconds.
  Learn any game with zero pressure and nobody watching.
- **Encouragement messages** on elimination instead of just "you lost" (toggle in config).
- **Chill modes** - e.g. Block Party `chill` (4 colors, 10s timers, relaxed pace);
  every game's pace settings are per-arena tunable.
- **No punishment loops** - no death screens, no item loss, instant respawns where
  the game allows, and parties always keep friends on the same team.

## Bedrock support (Geyser/Floodgate)

Run [Geyser + Floodgate](https://geysermc.org/) on the server (or your proxy) and
Bedrock players can join. PulseGames detects Bedrock clients automatically (Floodgate's
version-0 UUIDs) and adapts:

- **Double jump** (lobby + TNT Run): Bedrock clients don't send flight toggles through
  Geyser, so they get a tappable **Boost feather** instead.
- **Party invites**: clickable chat doesn't exist on Bedrock; invites include the plain
  `/party accept` command.
- Chest GUIs, scoreboards, titles, action bars, boats, elytra and shops all translate
  through Geyser natively. Some particles map to approximations - cosmetic only.

## Commands

| Command | Purpose |
|---|---|
| `/play <game> [mode]` | queue for a game (no args = GUI) |
| `/party invite/accept/deny/leave/disband/list/chat` | parties |
| `/lobby` (aliases `/hub`, `/leave`) | leave game / return to hub |
| `/stats` | your per-game stats |
| `/shop` (aliases `/tokens`, `/cosmetics`) | token shop |
| `/pulse setup ...` | in-game arena creation (see map guide) |
| `/pulse shape list/paste/undo` | generate geometry from JSON shape files |
| `/pulse world <name>` | load/create a build world on this server |
| `/pulse list / arenas / games` | inspect running instances, maps, games |
| `/pulse start / end` | force-start/-end the game you're in |
| `/pulse setlobby / reload` | hub spawn / reload configs |

Admin permission: `pulsegames.admin`.

## Project layout

- `core/` - engine: game state machine, instance/world management, parties, lobby,
  kits, teams, scoreboards, stats, matchmaking, in-game setup.
- `games/` - one package per minigame; each is a `MiniGame` subclass registered in
  `games/GameCatalog.java` with its modes and setup requirements.

To add a new game: subclass `MiniGame`, override the hooks you need, register it in
`GameCatalog` with modes + setup requirements. The engine handles everything else.
