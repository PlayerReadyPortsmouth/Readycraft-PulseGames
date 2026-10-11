# Map & Arena Setup Guide

Everything is done **in-game** - you never have to edit a config file. You build (or
import) a map world, mark its gameplay elements with commands and the wand, and save.
The plugin captures the world as a template and the arena goes live immediately.

## The flow (any game)

1. **Get into your map world.**
   - Import: drop the world folder into the server root, then `/pulse world <foldername>`.
   - Or build fresh: `/pulse world buildworld1` creates and teleports you there.
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

### Spleef (`spleef` - classic, decay, splegg)
- **Spawns:** 4+ around the edge of the floor.
- **Regions:** `floor1` (and `floor2`... for multi-layer maps) - flat snow-block layers.
- **Settings:** `void-y` below the lowest floor.
- Build: snow block floors work best; anything inside the floor regions is breakable/shootable.

### TNT Run (`tntrun` - classic, doublejump)
- **Spawns:** 4+ on the top layer.
- **Settings:** `void-y` under the bottom layer.
- Build: classic maps use 2-3 stacked layers of TNT-on-sandstone; the game removes the
  block underfoot plus the one beneath it after a short delay.

### Block Party (`blockparty` - classic, hardcore)
- **Regions:** `floor` - a one-block-tall dance floor (the plugin recolors it every round).
- **Spawns:** 1+ on the floor. **Settings:** `void-y` just below it.

### Avalanche (`avalanche`)
- **Regions:** `arena` - the survival platform volume; blocks fall from its **top Y**,
  so make the region tall (platform at the bottom, ~20 blocks of air above).

### Volcano (`volcano` - classic, eruption)
- **Regions:** `arena` - the whole climbable mountain; lava starts at the region's
  **bottom Y** and rises one layer at a time. Optional `lava-interval` setting (seconds).

### King of the Hill (`koth` - solo, teams)
- **Regions:** `hill` - the capture zone. **Spawns:** 4+ around it.
- Optional: `target-score`, `kit` (a kit id from kits.yml).

### Duels (`duels` - classic, op, sumo)
- **Spawns:** exactly 2, facing each other. **Settings:** `void-y` (sumo ring-out).
- Optional: `rounds-to-win` (default 2), `kit`.

### Kit PvP (`kitpvp` - ffa, oitc)
- **Spawns:** 4+ spread out. Optional: `target-kills`, `kit`.

### Parkour Race (`parkour` - sprint, elimination)
- **Regions:** `checkpoint1..N` in course order, `finish` at the end.
- **Settings:** `void-y` - falling teleports you back to your last checkpoint (no death).
- Optional: `elim-interval` for elimination mode.

### Elytra Racing (`elytra` - sprint, grandprix)
- **Spawns:** high launch platform. **Regions:** `checkpoint1..N` (big air rings),
  `finish`. **Settings:** `void-y`. Optional: `laps`, `rockets`.

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
  CoD-zombies style. Optional: `rounds`.
- Kills earn points; emerald opens the Mystery Shop (weapons, armor, perks, heals).
  Downed players revive when the team clears the round.

### Lucky Pillars (`luckypillars` - classic, chaos)
- **Spawns:** one per pillar top. **Settings:** `void-y`.
- Optional: `drop-interval`. Chaos mode adds levitation/swap/moon-jump events.

### Prop Hunt (`prophunt` - classic, infection)
- **Spawns:** spawn 1 is the seeker release point; add more for hider scatter points.
- Build: lots of clutter (hay bales, barrels, crafting tables, logs, melons...) so
  hider head-blocks blend in. Optional: `hide-seconds`, `time-limit`.

### Supermarket Sweep (`supermarket` - classic, rush)
- **Regions:** `checkout` - walk through it to bank list items.
- Build: stock shelves with **chests containing the food items** (the default pool:
  apple, bread, carrot, potato, egg, milk bucket, cooked beef, pumpkin pie, cookie,
  melon, berries, honey, cake, golden carrot, mushroom stew).
- Optional: `list-size`, `item-pool` (custom materials), `time-limit`.

### Party Games (`partygames`)
- **Spawns:** 4+ at the central hub.
- **Regions:** `floor` (Color Rush dance floor), `sumo` (knock-off platform - the
  game spawns players on its top), `finish` (Sprint goal).
- **Settings:** `void-y` below all three areas. Optional: `rounds`.

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
- Each running instance copies its template to `pulse_<id>/` in the server root,
  loads it with a void generator, and deletes it after the game. Nothing you do in a
  match ever touches the template.
- Arena definitions are written to `plugins/PulseGames/arenas/<game>-<arenaId>.yml`;
  they're human-readable if you ever want to tweak a number, then `/pulse reload`.

### Dodgeball (local validation required)

Modes: `solo` (2–12), `teams` (2–4, two teams). Provide at least two opposite
spawns (first two are team bases), an enclosed court and `void-y`. No region is
required. Players receive a blaze rod: right-click throws one snowball every
0.75 seconds. Three enemy hits eliminate; friendly hits do nothing. Balls expire
after five seconds. The last player/team wins; the four-minute timeout is a draw.
`blueprints/dodgeball-court.json` supplies a simple enclosed court.

### Dropper (local validation required)

Modes: `practice` (one player), `race` (2–12). Provide a first launch spawn,
a `finish` region covering the final **water blocks**, and `void-y` below the
lowest landing. Optional `checkpoint1..N` regions cover intermediate water pools,
in numeric order. **Each** requires a `checkpoint-spawnN` location setting for
the next stage's safe launch platform. Reaching a pool advances and teleports to
that launch; damage or falling below `void-y` retries the current stage. The first
player to complete all pools and the finish wins. Timeouts award no winner.
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
