# Block Party patterns and gameplay blockers — 0.3.24 candidate

Prepared 15 October 2026. Includes the earlier 0.3.23 countdown changes. Local build only: not merged/deployed; no restart performed.

## Block Party

Approved offset mosaic, diagonal bands, crossed tiles and shifted concentric rings. Patterns never repeat consecutively; colour order, offsets and reflections vary. Four-block patches become three-block patches after round five. Classic begins with five colours and adds one each four rounds; Hardcore begins with six and adds one each three rounds, capped at ten.

Called colours must exist on walkable floor and be reachable from every remaining contestant. A bounded breadth-first search respects clear headroom; it budgets three blocks per second plus a reaction second. It prefers the intended deadline, extends only as needed up to ten seconds, and avoids the previous target when an equally timely alternative exists. This is a conservative route estimate, not proof of every client's movement or navigation.

One-block-high floor regions are limited to 4,096 tiles. Invalid/unreachable layouts stop with a diagnostic instead of making an impossible call. Contestants outside the floor or outside its normal standing/jumping height are eliminated before a new call, rather than aborting other players' game. Bots search the full validated floor; their selected destination is cached until the next round. Actual bot pathfinding through obstacles still needs testing.

## Other fixes

- Party Games restores its original Colour Rush floor before round exits and during cleanup, and only calls colours actually present.
- Supermarket Sweep deduplicates valid item materials, bounds list size and rejects an invalid configured pool. Shelf stock is still a map-author acceptance check; no broad world inventory scan was introduced.
- Skywars generates loot into unique chest slots, avoiding accidental overwrites.
- Villager Defense searches authored loaded spawn regions for safe floor/headroom. Staggered spawns remain pending through bounded retries; exhausted retries stop clearly rather than awarding an empty wave. No unsafe player-spawn fallback.
- Parkour elimination, Lucky Pillars drops and Volcano lava intervals clamp to at least one second, preventing modulo-by-zero failures.

## Validation

164 targeted tests pass, zero failures/errors/skips. Includes pattern variety and tiny floors, disconnected reachability, hundreds of generated corner-spawn scenarios, falling-player isolation, bots finding distant targets, floor restoration, shopping pool settings, loot uniqueness, safe spawning/retries and prior countdown/game regressions. Actual generator output inspected in actual-patterns.png. No Java/Bedrock live acceptance claimed.

## Test after an authorised deployment

1. Block Party Classic and Hardcore: play several rounds; check distinct patterns, readable target hints, changing difficulty and fair timers. Try two humans plus bots, narrow floors, obstacles, stepping off the edge and late-round rings. Confirm navigation and actual difficulty—not just mathematical reachability.
2. Party Games: complete Colour Rush and continue to another round; force-end midway, confirming no lingering holes.
3. Supermarket: check duplicate/invalid custom pools and list-size zero. Ensure shelves physically contain every eligible item.
4. Skywars: inspect opening chests for four to six entries without slot overwrites.
5. Villager Defense: test valid and obstructed spawn regions, retry behaviour and leaving mid-wave.
6. Recheck all countdown scenarios in the 0.3.23 testing notes.

Remaining audit work includes Volcano's flood-write budget, Lucky Pillars inventory overflow, KOTH ties, Duels team-spawn spread, race reset/ranking improvements and progression-aware bots. These are not claimed fixed here. PulseKarts custom pickup models remain the next feature track; no new kart assets are included.
