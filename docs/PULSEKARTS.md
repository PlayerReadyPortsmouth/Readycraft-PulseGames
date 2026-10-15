# PulseKarts custom-controller trial

Updated 15 October 2026. PulseGames 0.3.19 and PulseContent Paper 0.5.5 are deployed on MinigamesTest. The bot and handling improvements below are prepared for PulseGames 0.3.20 and are not deployed yet.

The controller replaces boat movement, not merely the boat model. PulseGames owns acceleration, braking, reverse, steering, gravity, collisions, kart speed effects and checkpoint progression. An invisible, gravity-free ArmorStand is the seat/carrier. PulseContent renders `readycraft:racing_kart`; the driver remains visible. There is no fallback boat mode.

## Included

- Raw player directional input feeds a fixed 20 Hz controller. Movement stays frozen on the grid.
- Prepared 0.3.20 physics uses the actual rotated 1.5 × 1.875-block footprint for terrain collisions, bounded movement/rotation collision checks, small steps/slabs, rider headroom checks and unloaded-chunk rejection. Deployed 0.3.19 uses a conservative 2.41-block square, which can stick in otherwise wide enough gaps. Complex block bounding boxes (such as stairs) remain conservative.
- Prepared top speed is 12 blocks/second, up from 10, with a base steering rate of 126 degrees/second (scaled by speed and style). Bots submit ordinary inputs with different cautious target speeds; they do not receive a physics advantage.
- Prepared support checks reject even tiny downward penetration, keeping a parked chassis exactly on the block/slab surface. This also prevents its origin from settling inside the supporting block; Java black-model rendering still needs a client retest.
- Each player owns one seat and visual. Dismounts return/remount at the last safe checkpoint; quitting, elimination, match end and error cleanup remove owned entities only. Audience changes refresh visuals.
- Ordered segment-based checkpoint crossing catches fast passes through thin gates. Existing Parkour/Elytra endpoint behaviour is unchanged.
- One held power-up at a time. Item boxes require departure/re-entry, with a five-second cooldown. Turbo changes actual controller speed for two seconds. Lightning and a forward, wall-limited shell pulse slow actual kart speed. Shells hit chassis positions, not player-only hitboxes.
- Compass resets to the last reviewed checkpoint/grid slot. Resets reject obstructed or unsupported positions. Match admission checks the PulseContent API/model and loaded player packs.

## Opening style choice and bumps (prepared)

The opening freeze is 15 seconds. Players choose a style through the inventory menu (hotbar slot 4 reopens it); Balanced is the default and choices lock when driving starts. Bots choose varied styles and use the same tradeoffs.

| Style | Acceleration | Steering | Top speed | Tradeoff |
|---|---|---|---|---|
| Balanced | 1x | 1x | 12 blocks/s | Standard item mix and bump effects |
| Light | 1.25x | 1.15x | 12 blocks/s | Incoming bump disruption lasts 1.25x as long |
| Heavy | 0.8x | 0.9x | 12 blocks/s | Outgoing bump slowdown duration 1.35x; incoming duration 0.7x |
| Trickster | 1x | 1x | 0.92x (11.04 blocks/s) | Item weights: 15% shell, 45% turbo, 40% lightning; other styles use 34% / 33% / 33% |

Karts do not physically block one another side by side. A qualifying rear impact triggers a short spin and slowdown instead: the victim's speed is reduced, steering spins briefly and a style-scaled slow effect follows. Each hit grants three simulation seconds of bump immunity. This is 60 physics ticks, not a promise of three wall-clock seconds on an overloaded server. Resetting does not remove existing immunity. Test rear hits, side passes, head-on passes, repeated hits and bots overtaking on both clients. These mechanics are prepared; balance and feel remain unaccepted.

## Map setup

Keep the existing grid spawns, numbered checkpoint regions, finish region, item boxes and void-y. Use at least three blocks of clear track width and approximately three blocks between grid centres. Start and reset headings point down the route. Give drivers at least 1.8 blocks of collision headroom. Ice is no longer required: controller speed is independent of surface slipperiness.

Add reviewed `checkpoint-spawn1`, `checkpoint-spawn2`, etc. locations using existing arena setup location commands. Each records position and heading. Without them the last crossing position is retained, but resets reject unsupported/blocked positions and fall back to the grid. Prefer reviewed reset points, especially on jumps and bridges.

## Content and release dependencies

The original custom controller requires PulseGames 0.3.19 and PulseContent Paper 0.5.5, now merged and deployed. The prepared bot and handling release is PulseGames 0.3.20. Merge the racing-kart example's entity/model into the existing catalogue, add `readycraft:racing_kart` to `minigames-visuals.allowed-content`, and run the established BetterModel/Geyser pack preparation pipeline. Do not overwrite existing catalogue entities or packs with the standalone example. The example preview token is not required in production.

The model is original: red chassis, gold stripe, four dark wheels and visible seat. It is 1.5 × 1.875 × .875 blocks, with its nose along local +Z. Wheel animation is not included yet.

ReadyPlayerZ PR #19, Avalanche PR #20 and the custom kart controller were merged through the combined release and deployed. This bot/handling pass has not been deployed or restarted. Future deployment needs fresh restart approval. A newly registered content model also needs the primary Geyser proxy restart and matching client packs; do not infer that every physics-only update needs a proxy restart.

Companion **PulseContent Paper 0.5.6 and Geyser extension 0.3.3** are prepared, not deployed. Paper uses immediate chassis yaw for kart visuals. The new Bedrock-only `pulsecontent:kart_link` channel links the registered kart visual to its ArmorStand with zero offset; the existing cosmetic player-parent path remains separate. It requires explicit `kart-attachments.json` enablement and the exact compiled model allowlist on the proxy, plus backend/proxy deployment and fresh restart permission. The prepared strict-floor fix addresses a probable cause of the black Java model, but that diagnosis and visual improvement still need a client retest. See the companion PulseContent `docs/KART-VISUAL-FOLLOWUP.md` for configuration and acceptance.

## Acceptance runbook

1. Use a broad, flat test circuit and confirm both clients finish loading the updated pack. Verify model orientation/ground contact, seated visible driver, and no exposed carrier or animal fallback.
2. On Java and Bedrock, test forward, brake, reverse, left/right, held controls over the start countdown, looking independently of steering, and dismount/remount. ArmorStand passenger input through Geyser is an unverified client dependency.
3. Test all four styles during the opening selection and confirm choices lock after 15 seconds. Drive into walls, shallow steps, low ceilings, corners and off-track edges. Check recovery has a sensible heading and that resets do not skip checkpoints.
4. Pass another kart side by side without blocking. Test rear-impact slowdown/spin, three-second immunity, repeated attacks, head-on passes and each style’s bump tradeoff.
5. Cross thin gates at full turbo speed; verify ordered checkpoints and all laps. Attempt walking and reverse-order shortcuts.
6. Collect each power-up, remain in a box, leave/re-enter, fire at the chassis and behind a wall, then check boosts/slows expire. Check inventory restrictions and the reset compass.
7. Leave with one racer while another continues: remaining visuals must persist/rebuild. End/abort the game and confirm no seats/models remain; unrelated decorative entities must survive.

Prepared in 0.3.26, not deployed: the first finisher starts a remaining-racer grace period; finished racers spectate, and the round closes when everyone finishes/leaves or grace expires. Set `finish-grace-seconds` to 5–60 (default 30). Same-tick crossing fractions determine order and exact first-place ties share victory. A departed winner keeps their recorded placing without transferring victory rewards. Live 0.3.25 still uses immediate first-finisher completion. Wheel animation and a podium presentation remain future work. Cup defaults to three laps on one circuit; the existing `laps` arena setting supports a custom positive lap count. Sprint remains one lap. Do not mark Bedrock controls, rendered pack alignment or racing feel accepted from automated tests alone.


## Bot driving line setup

Kart bots require a reviewed driving line for each map. They do not infer corners from checkpoint centres. Keep `bots-enabled: false` until the line has been checked; human-only maps remain usable without bot waypoints. Admission refuses missing, malformed or incomplete routes even if bots-enabled is true.

Use the existing arena setup session and explicit coordinate commands (no wand required):

```text
/pulse setup setloc bot-waypoint1 <x> <y> <z> [yaw pitch]
/pulse setup setloc bot-waypoint2 <x> <y> <z> [yaw pitch]
/pulse setup setloc checkpoint-spawn1 <x> <y> <z> <yaw> <pitch>
```

Continue `bot-waypoint3`, `bot-waypoint4`, etc., with no numbering gaps (3–128 points). Place waypoint1 just after the finish line, outside every gate. Follow the driving direction around the full circuit, through checkpoint1, checkpoint2, etc., and finally finish. The last point connects back to waypoint1, so do not duplicate waypoint1 at the end. The route must enter each gate exactly once in this order. Place points before and after bends so straight segments remain on the track; keep a wide clear corridor around them, with more points near tighter corners. Use points every 4–12 blocks, with tighter spacing around bends; no segment may exceed 24 blocks. Straight-line gate coverage is validated, but track clearance between points still needs a driving test.

Record `checkpoint-spawnN` after each numbered checkpoint with a forward heading and clear, supported kart footprint. Each reset must lie within three blocks of the segment crossing its gate, after entry into the gate and before that segment’s endpoint. Bots recover along the section after the last earned checkpoint; recovery never awards progress. Use the same reviewed driving line for Sprint and Cup. Existing general `bot-route-*` location lists do not replace the kart's numbered driving line.

Run arena validation before publishing, then enable `bots-enabled` through the normal setup setting command. Test one bot from the grid, every corner, all checkpoint recovery points, item use and three complete Cup laps. Test two bots side by side, a human overtaking them, a blocked route, off-track recovery, player replacement before the race, and cleanup after the last human leaves. Only mark that map bot-ready after these checks; route validation alone is not evidence that the kart can physically drive it.
