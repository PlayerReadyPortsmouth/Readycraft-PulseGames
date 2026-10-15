# Player Ready Z survival expansion — prepared for testing

15 October 2026. Local development only; no running server or authored map has been changed.

## Behaviour

Matches hold their instance at midnight and suspend daylight/weather cycling, restoring those values during cleanup. Zombies reconsider the nearest active, non-downed player every second within 48 blocks; they no longer require line of sight to acquire a target. Walls still constrain native pathfinding.

Random waves mix walkers and faster runners; plated brutes unlock in round 2 and slowing venom enemies in round 3. Every fifth round includes a larger, tougher Foreman. Stats increase within bounds rather than growing without limit. Bosses use a 1.5-scale native body; custom model size and collision require client acceptance.

Each spawn selects its location at execution time, 8–48 blocks from the nearest active player, inside an authored, unlocked mobspawn region. It requires loaded terrain, solid full-block footing and body clearance (three blocks for bosses). It never falls back to spawning directly on a player. Six failed attempts stop the match with a diagnostic instead of leaving an unwinnable wave. Map builders must provide connected, loaded routes with enough spawn area for moving players and bosses.

Power-up chance falls from 8% to 3% per eligible kill, with at least 30 seconds between drops across the match. Nukes are 2% of that pool, available from round 5 and separated by at least three rounds: a nominal 0.06% per eligible kill before cooldowns, previously 2%. Nukes halve surviving boss health instead of deleting bosses, and cannot create recursive kill rewards or drops. Pickups expire after 20 seconds.

## Optional flat arena settings

| Setting | Default | Bounds |
| --- | --- | --- |
| `rounds` | 15 classic / 10 frenzy | 1–50 |
| `aggro-range` | 48 | 16–96 blocks |
| `spawn-min-distance` | 8 | 3–24 blocks |
| `spawn-max-distance` | 48 | min + 1 through 96 |
| `powerup-drop-chance` | 0.03 | 0–0.10 |
| `powerup-cooldown-seconds` | 30 | 10–120 seconds |

Use `zombie-model-walker`, `zombie-model-runner`, `zombie-model-brute`, `zombie-model-venom` and `zombie-model-boss` for optional fully qualified PulseContent IDs. Missing mappings use native zombies. Configured but unavailable content fails readiness; it does not silently substitute a pig or other model. Art approval has been received for Rustwalker, Runner, Plated and Foreman; asset validation and client acceptance remain separate work.

See `READYPLAYERZ-MAP-PROGRESSION.md` for command-only team doors, gated spawn regions, round unlocks and wall buys. Existing maps retain the emerald shop. Wall buys offer Minecraft weapons, ammo, healing and timed perks; custom firearms and persistent unlock progression are not part of this change.

## Client acceptance

1. Play a current map on Java and Bedrock: night, ordinary waves, runners, targeting around corners and darkness should remain playable.
2. Move between starting and purchased areas. Confirm spawns track the squad, never enter locked areas, and never spawn inside players or solids.
3. Test a door with insufficient points, before its round unlock, then with two simultaneous purchasers. Only the successful purchaser pays, once; the whole team can pass.
4. Buy each wall offering. Full inventory, full health and an already-active perk must refuse without charging. Check weapon damage and ammunition use.
5. Reach round 5. Verify a large boss fits routes and is damaged, not killed outright, by a nuke. Confirm drops are visibly rarer.
6. Leave mid-wave, complete a match, and run two copies of the same map. Check enemy cleanup, independent doors, restored instance settings and an untouched source template.
7. With approved content installed, check all four textures and animation states on both editions, model/hitbox agreement and no lingering models after death or quit.

This branch does not include the separate prepared Prop Hunt edge-movement fix. A combined release must include that change deliberately before deployment.
