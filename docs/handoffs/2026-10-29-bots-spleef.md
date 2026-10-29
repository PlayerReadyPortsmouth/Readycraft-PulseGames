# Spleef bots play like a person, 29 October 2026

Status: draft PR from `origin/main` 42775fa (0.7.0). Version not bumped. Not played in a client. Suite: 1068 tests green.

## Diagnosis (Aurora, after playing 0.7.0)
"Spleef bots only move, and pause/get stuck."
- No opponent in line of sight (or beyond 16) meant `botTick` returned and `botDestination` scored a flat floor, so
  `nextStep` returned null and the bot stood still. `navigationIntervalTicks` was `pathTicks` (14-28 ticks), so even
  moving bots paused between steps.
- With an opponent: only the block directly under them, only within 4.5 blocks, only on an exact ray hit; a failed
  accuracy roll did nothing; decay mode ignored cracking blocks.
- `nextStep` was 3 deep / 32 visits, so it could not route round holes and stalled once the floor broke up.

## What changed
- `SpleefTactics` (new, pure, no Bukkit): `safeToDig` (never own block, never last exit, never strands the bot on a
  smaller island), `ahead` (velocity prediction, lead capped at 2), `trench`, `classicTargets` (priority list), `comfort`
  (intact cells in a 5x5, cracking penalty), `randomNeighbour`, `StuckWatch`.
- `SpleefGame`: awareness is the nearest alive opponent within 32 blocks, no LOS needed (LOS counts 4 blocks nearer),
  last-known position kept 3 s; per-bot `Brain` (reaction time, roam goal, strafe side, stuck state); velocity is
  sampled from positions (NPC velocity is unreliable). Re-path every 5 ticks.
  - Never idle: no enemy -> roam to a random intact block; null from the search -> random safe neighbour; same block
    for 2 s while moving was wanted -> unstick goal 2-3 cells away for 2 s.
  - Classic: dig under an adjacent enemy; Hard leads with a trench two blocks out toward an approaching enemy; then the
    predicted-ahead block, then under the enemy, then a trench; far enemies (4.5-14) get snowballs at the predicted block.
  - Splegg: eggs lead the target by flight time, keep firing while strafing; misses fire with 3x aim error instead of
    silently skipping; keeps 6.5 blocks.
  - Decay: needs 2 exits before any dig, strongly prefers dense cells and leaves cracking ones; keeps about 4 blocks.
  - Human touches: reaction delay on first sight (`reactionJitter`*3 + 6 ticks), wrong-adjacent-block digs
    (`aimErrorDegrees`*4 percent), a miss still swings, 6-13 tick beat after a hit, Easy digs beside itself 4% of
    actions (classic only). Strafing is a sideways bias in the score.
- `GroundNavigation`: `nextStepDeep` (depth 6, 96 visits) plus a package-visible overload with limits. Default
  `nextStep` unchanged (3/32).

## Shared fixes and games touched
Only `GroundNavigation` gained an overload; nothing existing calls it but Spleef. No game besides Spleef changes
behaviour. Checked the siblings: TNT Run (own corridor search; null means no safe tile), Block Party (null deliberately
means "stand on the safe colour"; shuffle phase), Avalanche and Volcano (null means "already safe"; they use the 3/32
search and could adopt `nextStepDeep` later). A generic wander fallback in `BotService` would break Block Party and the
safe-standing cases, so it was not added.

## Config keys
None. Uses `BotDifficulty` profile values `aimErrorDegrees`, `reactionJitter` and the existing delay/accuracy fields
(so `bots.combat.tiers.*` overrides apply).

## Left out
No real-server test of `breakBlock`/ray reach on dig-ahead targets; Splegg does not check self-isolation (eggs do not
choose blocks near the bot); Easy self-dig is skipped in decay and splegg.

## In-game acceptance
1. Classic, 3 bots, empty arena: they walk about, never stand still for more than a second or two.
2. A bot alone at the far side walks toward you (not just rotates), within 32 blocks and behind a pillar.
3. Walk away in a straight line: bots dig a block or two ahead of you; Hard bots cut a gap in front as you approach.
4. Holes everywhere late round: bots route round them and are not stuck on a lone block.
5. Bots never fall because they dug their own block or last exit (except an occasional Easy slip).
6. Decay: bots leave blocks as they crack and stay on the dense part of the floor.
7. Splegg: eggs land ahead of a moving player, bots keep moving sideways while firing.
8. Misses are visible swings; wrong-block digs happen on Easy more than Hard.
