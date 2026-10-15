# PulseKarts custom-controller trial

Prepared 15 October 2026. This replaces boat movement, not merely the boat model. PulseGames owns acceleration, braking, reverse, steering, gravity, collisions, kart speed effects and checkpoint progression. An invisible, gravity-free ArmorStand is the seat/carrier. PulseContent renders `readycraft:racing_kart`; the driver remains visible. There is no fallback boat mode.

## Included

- Raw player directional input feeds a fixed 20 Hz controller. Movement stays frozen on the grid.
- Physics uses bounded collision substeps, supports small steps/slabs, checks rider headroom and refuses unloaded chunks. The conservative 2.41-block square footprint contains the rendered kart at every heading; complex stair shapes and tight corners may feel restrictive.
- Each player owns one seat and visual. Dismounts return/remount at the last safe checkpoint; quitting, elimination, match end and error cleanup remove owned entities only. Audience changes refresh visuals.
- Ordered segment-based checkpoint crossing catches fast passes through thin gates. Existing Parkour/Elytra endpoint behaviour is unchanged.
- One held power-up at a time. Item boxes require departure/re-entry, with a five-second cooldown. Turbo changes actual controller speed for two seconds. Lightning and a forward, wall-limited shell pulse slow actual kart speed. Shells hit chassis positions, not player-only hitboxes.
- Compass resets to the last reviewed checkpoint/grid slot. Resets reject obstructed or unsupported positions. Match admission checks the PulseContent API/model and loaded player packs.

## Map setup

Keep the existing grid spawns, numbered checkpoint regions, finish region, item boxes and void-y. Use at least three blocks of clear track width and approximately three blocks between grid centres. Start and reset headings point down the route. Give drivers at least 1.8 blocks of collision headroom. Ice is no longer required: controller speed is independent of surface slipperiness.

Add reviewed `checkpoint-spawn1`, `checkpoint-spawn2`, etc. locations using existing arena setup location commands. Each records position and heading. Without them the last crossing position is retained, but resets reject unsupported/blocked positions and fall back to the grid. Prefer reviewed reset points, especially on jumps and bridges.

## Content and release dependencies

Requires PulseGames 0.3.19 and PulseContent Paper 0.5.5 from their companion drafts. Merge the racing-kart example's entity/model into the existing catalogue, add `readycraft:racing_kart` to `minigames-visuals.allowed-content`, and run the established BetterModel/Geyser pack preparation pipeline. Do not overwrite existing catalogue entities or packs with the standalone example. The example preview token is not required in production.

The model is original: red chassis, gold stripe, four dark wheels and visible seat. It is 1.5 × 1.875 × .875 blocks, with its nose along local +Z. Wheel animation is not included yet.

The next combined release must also preserve pending ReadyPlayerZ PR #19 and Avalanche PR #20. No server/restart was performed for this trial. New model registration needs the primary Geyser proxy restart as well as MinigamesTest; request fresh approval after concrete packs and backups are ready.

## Acceptance runbook

1. Use a broad, flat test circuit and confirm both clients finish loading the updated pack. Verify model orientation/ground contact, seated visible driver, and no exposed carrier or animal fallback.
2. On Java and Bedrock, test forward, brake, reverse, left/right, held controls over the start countdown, looking independently of steering, and dismount/remount. ArmorStand passenger input through Geyser is an unverified client dependency.
3. Drive into walls, shallow steps, low ceilings, another kart, corners and off-track edges. Check recovery has a sensible heading and that resets do not skip checkpoints.
4. Cross thin gates at full turbo speed; verify ordered checkpoints and all laps. Attempt walking and reverse-order shortcuts.
5. Collect each power-up, remain in a box, leave/re-enter, fire at the chassis and behind a wall, then check boosts/slows expire. Check inventory restrictions and the reset compass.
6. Leave with one racer while another continues: remaining visuals must persist/rebuild. End/abort the game and confirm no seats/models remain; unrelated decorative entities must survive.

Not yet implemented: driving bots, wheel animation, exact oriented collision, finish grace/podium and improved between-gate standings. Cup remains three laps on one circuit. Current first-finisher race semantics are preserved. Do not mark Bedrock controls, rendered pack alignment or racing feel accepted from automated tests alone.
