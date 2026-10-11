# PulseGames

A multi-instance minigame engine for the Readycraft network, built as a single
Paper plugin. One Minecraft server runs many simultaneous games, each in its own
throwaway copy of a map world.

## Games (23, including two opt-in previews)

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
| Dodgeball (preview) | `dodgeball` | solo, teams |
| Dropper (preview) | `dropper` | practice, race |

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
./gradlew test jar  # regression tests and plugin jar
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
| `/pulsekits` (alias `/kits`) | choose a default or owned KitPvP loadout for the next spawn |
| `/pulse bots` | queue-bot provider status, count and supported modes |
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

## Current development review

See [the 11 October review](docs/REVIEW-2026-10-11.md) for verified gaps, the first foundation fixes, proposed new games, PulseContent/Geyser integration requirements and quiet-time NPC options. This branch adds [automatic queue-filling bots](docs/QUEUE-BOTS.md), Dodgeball and Dropper previews, and an experimental [PulseContent Prop Hunt palette, block snapping and head disguises](docs/PULSECONTENT.md). All three are off by default and require an explicit trial configuration. The companion rendering API needs mixed Java/Bedrock client acceptance before it can be enabled.

## Development and play-test status

See [MINIGAMES-TRACKER.md](MINIGAMES-TRACKER.md) for the maintained map register,
per-game bot readiness, two-player tests, reported issues and pending rollout work.
Agents should update it as each item progresses.

### Duels combat and personal kits (0.3.6 candidate)

`/duels` opens combat style → discipline → team size, with a separate personal-kit
picker. Each opponent chooses their own compatible kit. See [Duels setup and acceptance](docs/DUELS.md)
for map opt-ins, compatibility and the remaining Java/Bedrock test checklist.
