# Map & Arena Setup Guide

Everything is done **in-game** - you never have to edit a config file. You build (or
import) a map world, mark its gameplay elements with commands and the wand, and save.
The plugin captures the world as a template and the arena goes live immediately.

## The flow (any game)

1. **Get into your map world.**
   - Import: drop the world folder into the server root, then `/pulse world <foldername>`.
   - Or build fresh: `/pulse world buildworld1 void` creates an empty world with a 7×7 starter platform and teleports you there in Creative. New worlds default to void; use `normal` explicitly for natural terrain. Existing worlds are loaded unchanged, even if you supply a different type. Choose a new name to create a different world.
2. **Start a setup session** (standing in that world):
   ```
   /pulse setup start <game> <arenaId>     e.g. /pulse setup start spleef frosty
   ```
   You get the **Arena Wand** (blaze rod) and a checklist of exactly what this game needs.
3. **Mark the elements.** Where you stand matters:
   - `/pulse setup lobby` - the waiting lobby (always required)
   - `/pulse setup spectator` - where eliminated players watch from (always required)
   - `/pulse setup addspawn` - add a spawn point (repeat; order matters for team games)
   - Regions: left-click a block for pos1, right-click for pos2, then
     `/pulse setup region <name>` (e.g. `floor1`, `checkpoint3`, `hill`)
   - `/pulse setup setloc <key>` - a single named location (e.g. `villager`)
   - `/pulse setup addloc <key>` - append to a location list (e.g. `diamond-generators`)
   - `/pulse setup set <key> <value>` - plain settings (e.g. `set void-y 40`,
     `set display-name <aqua>Frosty Caverns`, `set max-players 12`, `set modes classic,decay`)
4. **Check progress**: `/pulse setup check` (clickable chat checklist) or
   `/pulse setup gui` (chest menu - green = done, red = missing, emerald block = save).
5. **Save**: `/pulse setup save`. The plugin validates the checklist, writes the arena,
   copies the world into `plugins/PulseGames/maps/<arenaId>/` and loads it. Test with
   `/play <game>` (use `/pulse start` to force-start alone).

### Random map selection

Just repeat the process with a different `arenaId` (in a different build world).
Every arena registered for a game+mode joins the random pool automatically.
Restrict a map to certain modes with `/pulse setup set modes <mode1,mode2>`.

### General tips

- Spawn order = team order in team modes (spawn 1 → Red, spawn 2 → Blue, ...).
- `void-y` is the Y level that counts as "fallen off". Set it a few blocks under
  your play area so eliminations feel instant.
- Make race checkpoints generous (3-5 blocks thick) so fast players can't skip
  through them between ticks - especially for elytra and karts.
- The waiting lobby should be enclosed (players can move freely there before the game).
- After saving you can re-run setup with the same id to overwrite the arena.

---

## Per-game requirements

The checklist shows all of this in-game; this is the reference with map-building advice.

### Spleef (`spleef` - classic, decay, splegg, rounds)
- **Spawns:** 4+ around the edge of the floor.
- **Regions:** `floor1` (and `floor2`... for multi-layer maps) - flat snow-block layers.
- **Settings:** `void-y` below the lowest floor.
- Build: snow block floors work best; anything inside the floor regions is breakable/shootable.
- **Rounds mode:** the floor regions are snapshotted at the start and restored between rounds (no new
  region). Decay mode now cracks each block a second before it vanishes.

### TNT Run (`tntrun` - classic, doublejump, rounds)
- **Spawns:** 4+ on the top layer.
- **Settings:** `void-y` under the bottom layer.
- Build: classic maps use 2-3 stacked layers of TNT-on-sandstone; the game removes the
  block underfoot plus the one beneath it after a short delay.
- **Rounds mode:** no region needed; every block the game removes is remembered and put back between rounds.
- Optional: `crumble-acceleration-seconds` (the crumble gets one tick quicker every N seconds, default 60,
  `0` = off) and `min-crumble-ticks` (fastest delay, default 4; the base delay is 8). These apply to
  the live modes too.

### Block Party (`blockparty` - classic, hardcore)
- **Regions:** `floor` - a one-block-tall dance floor (the plugin recolors it every round).
- **Spawns:** 1+ on the floor. **Settings:** `void-y` just below it.
- Optional: `colour-patterns: true` lays the floor in glazed terracotta patterns instead of wool, so the
  call never depends on telling colours apart. The called colour is always shown as a name and shape
  (for example "▲ RED") on the action bar, and Hardcore shows the hotbar hint briefly instead of hiding it.

### Avalanche (`avalanche` - classic, rounds)
- **Regions:** `arena` - the survival platform volume; blocks fall from its **top Y**,
  so make the region tall (platform at the bottom, ~20 blocks of air above).
- **Rounds mode:** the arena box (extended down to `void-y` so the collapsing edge columns are included)
  is snapshotted and restored between rounds. Keep the region under 300,000 blocks.

### Volcano (`volcano` - classic, eruption, rounds)
- **Regions:** `arena` - the whole climbable mountain; lava starts at the region's
  **bottom Y** and rises one layer at a time. Optional `lava-interval` setting (seconds).
- Optional `min-lava-interval` (seconds, default 2): the late rise never gets faster than one block per
  this many seconds. Without a cap the lava reaches one block a second after about 90 s, which a child
  cannot out-climb on a tall mountain. Set `1` for the old pacing.
- **Rounds mode:** the arena region is snapshotted and restored (lava included); keep it under 300,000 blocks.

### King of the Hill (`koth` - solo, teams)
- **Regions:** `hill` - the capture zone. **Spawns:** 4+ around it; respawns pick the
  spawn farthest from enemies (random among the farthest third) with 2 s protection.
- A hill with two sides on it is **contested**: nobody scores. Kills pay 5, assists 2.
- Optional: `target-score`, `kit` (a kit id from kits.yml). **Hill rotation:** define
  `hill1..N` (two or more) instead of `hill`; the active hill moves every
  `hill-rotate-seconds` (default 75, minimum 20) with a title announcement. A map with
  only `hill` keeps one fixed hill.

### Duels (`duels` - classic, op, sumo)
- **Spawns:** exactly 2, facing each other. **Settings:** `void-y` (sumo ring-out).
- Optional: `rounds-to-win` (default 2), `kit`.

### Kit PvP (`kitpvp` - ffa, oitc)
- **Spawns:** 4+ spread out; respawns pick the spawn farthest from enemies with 2 s
  protection, so more spread-out spawns make it fairer. Optional: `target-kills`, `kit`.
- Streaks (ffa only): 3 kills = golden apple, 5 = speed. The strict leader (3+ kills)
  carries a glowing bounty worth 5 tokens, claimable 3 times per player per match.

### Parkour Race (`parkour` - sprint, elimination)
- **Regions:** `checkpoint1..N` in course order, `finish` at the end.
- **Settings:** `void-y` - falling teleports you back to your last checkpoint (no death).
- Optional: `elim-interval` for elimination mode (seconds between cuts, default 45; each cut has a 10 s warning
  and the cut runner becomes a ghost runner who keeps scoring per checkpoint). `finish-grace` - seconds after the
  first finisher during which others can still finish and get a place (default 30, clamped 5-120).
- Personal best times are kept per arena id, so renaming an arena resets its bests.

### Elytra Racing (`elytra` - sprint, grandprix)
- **Spawns:** high launch platform. **Regions:** `checkpoint1..N` (big air rings),
  `finish`. **Settings:** `void-y`. Optional: `laps`, `rockets`, `finish-grace` (see Parkour).
  Optional **boost rings** `boost1..N` (same cuboid style as checkpoints, not part of the route): flying through
  one refills rockets to `rockets`, once per 3 s per ring.

### PulseKarts (`karts` - sprint, cup)
- **Spawns:** 2+ on the starting grid. **Regions:** `checkpoint1..N`, `finish`
  (drive through every lap), `itembox1..N` on the racing line.
- Build: ice roads make boats fast. Optional: `laps`.

### Skywars (`skywars` - solo, doubles, insane)
- **Spawns:** one per island (doubles: islands need 2-player space; spawn N = team N).
- Place **chests** on islands and at mid while building - they're filled with random
  loot the first time someone opens them (better loot in insane mode).

### Bedwars (`bedwars` - duos, quads)
- **Spawns:** one per team base, in team order.
- **Regions:** `bed1..bedN` - a small box around each team's bed (team N's bed).
  Place the actual beds while building.
- **Locations:** `setloc generator1` ... `generatorN` - each team's iron/gold spawner;
  `addloc diamond-generators` (repeat) for shared diamond points.
- In-game: emerald = item shop (wool, swords, armor, bow, gapples, TNT...). Bed gone = no respawn.

### Build Battle (`buildbattle` - classic, speed)
- **Regions:** `plot1..plotN` - one flat build plot per player, well separated.
- Optional: `themes` (list), `build-seconds`, `vote-seconds`.
- Players build in creative inside their own plot only, then everyone tours and votes 1-5.

### Death Run (`deathrun` - classic, doubletrouble)
- **Spawns:** runner start. **Locations:** `setloc death-spawn` - the Death's perch.
- **Regions:** pair `trapN` (blocks that vanish for 3s) with `triggerN` (the
  button/lever area the Death right-clicks). Build the course so each trap covers
  a mandatory passage. `finish` = runners' goal.
- Optional: `trap-cooldown`, `time-limit` (Deaths win on timeout).

### Villager Defense (`villagerdefense` - easy, hard)
- **Locations:** `setloc villager` - the Mayor's spot (somewhere defensible).
- **Regions:** `mobspawn1..N` - zombie entrances around the village. Optional: `waves`.

### ReadyPlayerZ (`readyplayerz` - classic, frenzy)
- **Spawns:** the squad room. **Regions:** `mobspawn1..N` - zombie windows/doors,
  CoD-zombies style. Optional: `rounds`. Existing maps need nothing else.
- Kills earn points (and 5 per hit); the emerald opens the **Supply Shop** (27-slot chest:
  weapons, armour, perks, heals). Every third round (not a boss round) is a rush round of
  runners; bosses ("Brute Boss") arrive every fifth round.
- **Revive:** a downed player stays where they fell (slow, weakened). A teammate crouching
  within 2 blocks for 8 s revives them at half health (+50 points). Bleed-out is 45 s, then
  spectator until the round clears. A solo player gets one self-revive per match.
- **Results:** round reached, kills, revives, points and MVP show on end; an overrun reads
  "Well played", never "game over". Best round per map is kept in `stats.yml`
  (`_records.readyplayerz.<arena>.best-round`) and shown on the sidebar.
- **Optional extras (all fall back to a Supply Shop entry when absent):**
  - `mysterybox1..N` - setting locations (`x,y,z,yaw,pitch`); right-click the block under it.
    950 points, +10% per use, price resets every round; 3 s action-bar spin, weighted
    weapon or perk.
  - `packapunch` - setting location; right-click with a weapon in hand. 5000 points,
    once per weapon (Sharpness III / Power III / Quick Charge II, renamed, glint).
  - `difficulty` - `relaxed`, `normal` or `hard` (zombie health x0.75 / 1 / 1.25 and wave
    size x0.8 / 1 / 1.25). Unset: relaxed for squads of 2 or fewer, otherwise normal.
  - Existing optional doors, wall buys, `bounds`, darkness and health-tuning settings are unchanged.
- Power-ups: insta-kill, double points, max ammo, nuke, plus Team Shield (10 s of protection)
  and Second Wind (+4 hearts for the squad).

### Lucky Pillars (`luckypillars` - classic, chaos)
- **Spawns:** one per pillar top. **Settings:** `void-y`.
- Optional: `drop-interval` (seconds; default 5 in both modes, floor 3).
- Drops are a weighted pool of ~60 items that gets stronger as the round goes on; players
  with no weapon get boosted weapon odds. TNT, creepers and lava are enabled, so the pillars
  can be blown up: build with that in mind.
- Chaos mode adds ~16 timed events (low gravity, TNT rain, rising lava, shrinking border,
  ice floor, swap, mob swarm...). The border event sets its own border and restores the
  original afterwards; keep players' pillars within ~60 blocks of each other.

### Prop Hunt (`prophunt` - classic, infection, rounds)
- **Spawns:** spawn 1 is the seeker release point; add more for hider scatter points.
- Build: lots of clutter (hay bales, barrels, crafting tables, logs, melons...) so
  hider head-blocks blend in. Optional: `hide-seconds`, `time-limit`.
- **Rounds mode:** best of three with seeker and hider roles swapped each round (whoever has sought
  least seeks next). Plain disguises only: it refuses to start while PulseContent custom props are
  enabled. No map changes. `round-seconds` includes the hide time (default `hide-seconds` + 90).

### Rounds mode (`rounds`: Spleef, TNT Run, Avalanche, Volcano, Dodgeball, Prop Hunt)
A separate mode id; the existing modes are unchanged. Best of three short rounds, a 5 second
intermission with a full reset, "Round 2 of 3" titles, and points per round by placement (the longest
survivor scores the field size, the first out scores 1; players who last the whole clock tie at the top).
The match winner has the most points, ties broken by round wins, then shared. Optional arena settings for
every rounds game: `round-count` (1-5, default 3) and `round-seconds` (20-180, default 75; Dodgeball 90).
An arena with an explicit `modes:` list must list `rounds` before it is offered; an arena with no list offers
it automatically.

### Supermarket Sweep (`supermarket` - classic, rush)
- **Regions:** `checkout` - walk through it to bank list items.
- Build: stock shelves with **chests containing the food items** (the default pool:
  apple, bread, carrot, potato, egg, milk bucket, cooked beef, pumpkin pie, cookie,
  melon, berries, honey, cake, golden carrot, mushroom stew).
- Optional: `list-size`, `item-pool` (custom materials), `time-limit`.

### Party Games (`partygames`)
- **Spawns:** 4+ at the central hub. **Lobby point:** where knocked-out players watch.
- **Regions** (all optional per micro-game; a micro-game is only in the rotation if its
  region exists): `floor` (Color Rush, Mini Spleef, TNT Run, Memory Tiles - Memory needs
  at least 3x3 blocks), `sumo` (Sumo Brawl, Hot Potato - players start on its top),
  `finish` (Sprint). Simon Says needs no region, so every map has at least one game.
- **Settings:** `void-y` below all areas (floor games also eliminate anyone who drops
  below the floor layer). Optional: `rounds` (default 5), `chaos-chance` (percent per
  round after the first, default 15, 0 = never) for a double-points round, and
  `podium1`, `podium2`, `podium3` locations (`x,y,z,yaw,pitch`) where the top three are
  teleported during the final reveal. Without them the reveal happens on the hub.
- Build: keep `floor` a flat single or few-layer block floor; it is restored after each round.

---

## Generating geometry from JSON ("shape schematics")

You don't have to place every block by hand. `plugins/PulseGames/shapes/*.json` files
describe geometry as data - primitives with materials and offsets - and
`/pulse shape paste <file>` stamps them into the world **relative to where you stand**
(`/pulse shape undo` reverts the last paste). Three examples ship with the plugin:
`spleef-arena`, `lucky-pillars` and `skywars-island` (paste the island once per spawn point).

Supported primitives (all coordinates are offsets from your feet):

| Type | Keys | Notes |
|---|---|---|
| `box` | `from`, `to`, `hollow?` | cuboid (hollow = walls only) |
| `cylinder` | `center`, `radius`, `height?`, `hollow?` | flat floors, towers, rings of wall |
| `sphere` / `dome` | `center`, `radius`, `hollow?` | dome = top half only |
| `pillars` | `origin`, `countX`, `countZ`, `spacing`, `height` | grids of columns |
| `ring` | `center`, `radius` | one-block circle outline (race checkpoints!) |
| `checker` | `from`, `to`, `materials` | alternating floor pattern |
| `scatter` | `from`, `to`, `density?` | random decoration fill |

Every shape takes `"material": "SNOW_BLOCK"` or `"materials": [...]` (random per block).

This means a whole arena can be authored as a JSON file - by hand, by a script, or by
an AI assistant - dropped into the shapes folder, pasted, then registered with
`/pulse setup` as usual. A typical loop: ask Claude for "a volcano arena shape file,
40-block radius, with a central crater", save it as `volcano1.json`, `/pulse shape paste
volcano1`, touch up by hand, run setup, save.

---

## How worlds are stored

- Templates live in `plugins/PulseGames/maps/<arenaId>/` (written by `/pulse setup save`).
- Each running instance copies its template into a separate world and deletes it
  after the game. Paper before 26.1 uses `pulse_<id>/` in the server root; Paper
  26.1+ uses `<level>/dimensions/minecraft/pulse_<id>/`. Templates captured on
  older servers must be imported and re-captured after upgrading to modern Paper.
  Capture flushes pending saves before copying, and clones receive new world UUIDs. Nothing you do in a
  match ever touches the template.
- Arena definitions are written to `plugins/PulseGames/arenas/<game>-<arenaId>.yml`;
  they're human-readable if you ever want to tweak a number, then `/pulse reload`.

### Dodgeball (local validation required)

Modes: `solo` (2–12), `teams` (2–4, two teams). Provide at least two opposite
spawns (first two are team bases), an enclosed court and `void-y`. No region is
required. Players receive a blaze rod: right-click throws one snowball every
0.75 seconds. Three enemy hits eliminate; friendly hits do nothing. Balls expire
after five seconds. The last player/team wins. At the four-minute timeout the side with the most lives wins;
level sides play a 45 second sudden death on one life each (still level: shared). The sidebar lists everyone's
lives. Mode `rounds` (2-12) plays best of three 90 second rounds, ranked by lives at the clock.
`blueprints/dodgeball-court.json` supplies a simple enclosed court.

### Dropper (local validation required)

Modes: `practice` (one player), `race` (2–12). Provide a first launch spawn,
a `finish` region covering the final **water blocks**, and `void-y` below the
lowest landing. Optional `checkpoint1..N` regions cover intermediate water pools,
in numeric order. **Each** requires a `checkpoint-spawnN` location setting for
the next stage's safe launch platform. Reaching a pool advances and teleports to
that launch; damage or falling below `void-y` retries the current stage. The first
player to complete all pools and the finish wins, but the race stays open for `finish-grace` seconds
(default 30) so others still get a place and time; unfinished players are placed by stage reached. Timeouts
with no finisher award no winner. Practice mode is a time trial against your per-map personal best.
Pools must be reachable with ordinary falling and steering; provide solid rims
and floors to contain water. Do not overlap launch points and landing regions.
`blueprints/dropper-two-drops.json` supplies two 60-block drops with obstacle gates
and offset water pools. Build and playtest blueprints before enabling these WIP
catalog entries; neither is a record of a production map deployment.


### Prop Hunt block palette trial

`content.prophunt.block-snapping` adds a book selector and slime-ball lock control
when custom PulseContent Prop Hunt is enabled. Provide wide solid floor areas,
three-block-high clear cells, and decorative examples of the allowed palette.
Do not design head-sized crawl spaces: the small head target retains player
walking clearance. Palette/asset setup and client acceptance are documented in
[PULSECONTENT.md](PULSECONTENT.md). Temporary native props belong only in the
throwaway game world; never capture a template while a match is running.

### Setup Book (0.3.3)

`/pulse setup start <game> <arenaId>` gives a selection wand and a tagged **Arena
Setup Book**. Right-click the book to open the checklist; `/pulse setup book`
recovers it and `/pulse setup gui` opens the same menu. These controls require
`pulsegames.admin` and an active setup session.

Green entries are complete, red entries are required, and grey entries are optional.
Click position entries while standing at the desired point. Region entries save
the wand's two selected corners; numbered requirements choose the next unused
number. The menu also manages spawns and regions, selects supported modes, sets
player limits and bot permission, and accepts game-specific values through a
private chat prompt (`cancel` backs out). Prompts expire after two minutes.

Review shows the checklist before saving and explicitly labels replacement of an
existing saved arena/template. Saving still refuses missing required entries and
invalid player limits. Closing the menu keeps the session. Discarding it requires
confirmation. Sessions are not saved across server restarts; this menu configures
new sessions and does not import an existing arena for editing.


## Team sizes, map capacity and bot difficulty (prepared update)

Competitive combat games now expose solo plus doubles, triples and squads. Existing
mode IDs remain valid. Duels variants include `classic-doubles`, `op-doubles` and
`sumo-doubles`, with equivalent `-triples` and `-squads` variants. Duels always has
**two opposing teams** and best-of-rounds scoring; losing one teammate does not lose
the round. KitPvP team variants combine teammate kills. Skywars solo uses one-player
island teams; Bedwars adds `solo` and `triples` alongside `duos` and `quads`.
KOTH, Dodgeball and Lucky Pillars also expose team-size variants of their solo rules.
Prop Hunt keeps seeker/hider roles and Party Games keeps its rotating individual
competition; these are not interchangeable with symmetric combat teams.

In the Setup Book, **Player limits** now offers defaults plus separate minimum,
maximum and team count for each enabled mode. The map controls the available
opposing teams (2–32), limited by spawn count and required game features. Place one
spawn per team/base, not one per player. Bedwars needs a matching bed region per
team. Duels needs two sufficiently large team spawn areas. Build islands/pillars
large enough for the chosen team size.

Example: one six-base Bedwars map supporting solo, doubles and triples:

```yaml
modes: [solo, duos, triples]
min-players: 2
max-players: 18
settings:
  team-count: 6
  modes:
    solo:
      min-players: 2
      max-players: 6
    duos:
      min-players: 4
      max-players: 12
    triples:
      min-players: 6
      max-players: 18
```

The engine may start with fewer occupied teams than the map's maximum. To require
all six full teams, set minimum players to 6, 12 and 18 respectively. Queues skip
maps that cannot meet a mode's minimum/capacity; a forced start cannot invent
missing bases. Existing arena mode lists are preserved, so your three existing
Duels maps do not automatically opt into doubles. To use one, enable the new mode
and explicitly set its capacity, for example:

```
/pulse setup set modes.classic-doubles.min-players 4
/pulse setup set modes.classic-doubles.max-players 4
/pulse setup set modes.classic-doubles.team-count 2
```

These commands act on the active setup session; the book does not import existing
saved definitions. Preserve templates and use the existing recapture workflow.

Bot difficulty: `bots.difficulty: easy|normal|hard` sets the default; arena
`settings.bot-difficulty` overrides it. The Setup Book cycles the three levels.
Difficulty changes attack/throw timing, melee accuracy and path update cadence;
health, reach and damage are unchanged. Villager Defense Easy/Hard now have a
prepared co-op controller, using only tracked wave zombies and the Mayor's guard
position; existing combat controllers also recognise their team-size variants.
Skywars and Bedwars now have candidate island-game controllers; see
`PLAYTEST-WORKFLOW.md` for map restrictions and acceptance checks. Other
objective bot controllers remain outstanding. Team modes do not
imply bot support. Global/arena enablement and existing canary permissions still
apply. Live configuration and server restarts require separate approval.

## Agent-friendly setup and optional bot navigation (0.3.12 candidate)

An agent's authorised in-game player can complete setup using commands, without
clicking blocks or opening menus. Commands still require `pulsegames.admin`, an
active setup session, and the map world. These are player commands, not a new
console/RCON session API. Explicit coordinates are absolute; `~` and `^` are not
supported. Points allow decimal coordinates and optional yaw/pitch; corners use
whole block coordinates. Omitting coordinates keeps the existing current-position
behaviour. Coordinates do not teleport the player or load chunks.

```text
/pulse setup start spleef frosty
/pulse setup lobby 0.5 80 0.5 90 0
/pulse setup spectator 0.5 90 0.5
/pulse setup addspawn 5.5 80 5.5 180 0
/pulse setup region floor1 -10 79 -10 10 79 10
/pulse setup pos1 -12 78 -12
/pulse setup pos2 12 100 12
/pulse setup region bounds
/pulse setup setloc villager 0.5 80 0.5
/pulse setup addloc diamond-generators 8.5 80 8.5
/pulse setup check
/pulse setup validate
```

Use only the named locations/regions required by the selected game's checklist;
the examples above show syntax across different games, not a complete Spleef map.
All game-specific named regions use the same direct-coordinate command. Correct
mistakes with `removeregion <name>`, `removespawn <1-based index>`,
`removeloc <key> <1-based index>` or `clearloc <key>`. Removing a spawn or list entry
shifts later indexes. Finish the checklist and validation before `/pulse setup save`;
saving publishes the template, so keep the established map approval process.

The Setup Book now includes **Bot navigation**, with optional patrol points, hiding
spots, recovery walk targets and named routes. The equivalent commands are:

```text
/pulse setup addloc bot-patrol-points 10.5 80 4.5
/pulse setup addloc bot-hide-points 14.5 80 8.5
/pulse setup addloc bot-recovery-points 5.5 80 5.5
/pulse setup addloc bot-route-courtyard 2.5 80 2.5
/pulse setup addloc bot-route-courtyard 8.5 80 2.5
/pulse setup removeloc bot-route-courtyard 2
/pulse setup clearloc bot-route-courtyard
```

Routes need at least two ordered waypoints. Keep points on supported, clear ground
and lists within 128 entries. Validation checks saved points without loading chunks;
visit unloaded chunks to complete physical checks. At runtime, invalid, obstructed,
unloaded or hazardous hints are ignored. Authored routes are guidance, not proof
that Citizens can traverse every segment: test stairs, doors, gaps and corners.

Generic combat bots use patrol/routes when they have no immediate objective;
Prop Hunt seekers can patrol and hiders can choose authored hiding spots. Recovery
walk targets within 12 blocks may be tried for five seconds after 15 seconds of
stalled progress. They never teleport bots. Hazard games retain their own movement
controllers; these options do not imply every game consumes every hint. Bots use
conservative full-player clearance for hints, so one-block-high hiding hints are
not supported by this first navigation pass.


### Skywars bot map requirements (candidate)

Start with flat, full-cube island walkways and a visible chest within 4.5 blocks
of each bot spawn. Optional routes should lead towards safe island landing points.
The controller handles level steps, adjacent bridges and checked height changes,
including straight stairs/slabs and normal jump physics. It also supports bows.
Long void jumps, pearl and potion tactics remain unsupported. It uses real chest loot and finite
planks/cobblestone; a bot without bridge materials stops rather than receiving
free blocks. `settings.bot-bridge-limit` defaults to 64 per bot per round (0-128).

All authored navigation hints now require full-cube floor coverage under the
player footprint. Thin or hollow support such as trapdoors and stairs is rejected.
Use `/pulse bots inspect <name-or-UUID>` to check objectives, route and stall
reasons. Complete the Skywars acceptance checks in `QUEUE-BOTS.md` before enabling
bots for wider play; source support does not mean the map has passed testing.

Bedwars requires the existing per-team bed regions, spawns and generator locations.
The new bot controller uses those public objectives and actual resource drops;
see `PLAYTEST-WORKFLOW.md` for finite purchase, defence and protected-break checks.

## Quake

See [QUAKE.md](QUAKE.md) for the arena checklist, weapon rules, team variants and bot testing. Quake uses ordinary spawns and optional bot navigation hints; no special regions are required.
