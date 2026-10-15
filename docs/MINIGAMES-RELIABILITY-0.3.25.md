# PulseGames 0.3.25 reliability candidate

Status: locally built and tested, not pushed, merged or deployed. Includes the earlier 0.3.23 countdown and 0.3.24 Block Party improvements.

## Changes

- KOTH timeout requires a unique positive leading score; ties draw.
- Build Battle recognises joint winners for tied highest averages.
- Parkour ranks equal checkpoint progress by proximity to the next gate; tied last positions avoid arbitrary elimination, tied first positions draw at timeout.
- Race resets prefer safe authored checkpoint-spawnN locations and clear momentum. Elytra detects swept gate crossings and relaunches after a successful reset.
- DeathRun validates matching numbered trap/trigger regions, prevents overlapping activation and restores active traps on cleanup.
- Duels and KitPvP reject missing compatible loadouts before match startup. One in the Chamber retains its built-in equipment.
- Spleef decay retires sampled holes and removed tiles, with bounded scans.
- Volcano scans at most 1,024 cells per tick and invalidates old scheduled flood work on cleanup.
- Bedwars generators pause at nearby accumulated resource limits: 64 iron, 16 gold, 8 diamonds. Collection allows generation to resume.
- Lucky Pillars drops inventory overflow beside the player within the active match world.

## Verification

46 focused tests passed, zero failures/errors/skips. Jar build and diff whitespace check passed. An initial Spleef unit test run failed because Material.isAir required a running Paper registry; using the equivalent Block.isEmpty API resolved that test boundary. No live server or Java/Bedrock client verification performed.

## In-game acceptance

1. Check countdowns and Block Party floor readability on both clients using the earlier release checklist.
2. Race across Elytra gates quickly, reset, and verify safe launch direction. Map authors should supply checkpoint-spawnN for airborne gates; otherwise the previous safe launch remains the fallback.
3. Fire DeathRun traps repeatedly and leave while a trap is active; confirm restoration and clear setup errors for mismatched regions.
4. Exercise tied KOTH/Build Battle/Parkour finishes. Parkour uses geometric proximity, not route distance on winding courses.
5. Fill a Lucky Pillars inventory; confirm reward overflow is collectible. Collect capped Bedwars generator piles and verify generation resumes.
6. Run late Spleef decay and a long narrow Volcano arena; check responsiveness and clean teardown.

## Still pending

PulseKarts custom pickup artwork awaits the preview decision. Additional bot controllers, ReadyZ progression-aware bots, TNT floor-only destruction, Duels team spawn spreading and richer race finish ordering remain separate work. KOTH contested-hill scoring policy is unchanged. No claim that every audit item is complete.

## Volcano pacing follow-up

Classic now starts rising at 5 seconds, every 4 seconds initially; Eruption starts at 3 seconds, every 3 seconds initially. The interval drops by one second every 30 elapsed match seconds, minimum one second. Scheduled deadlines replace modulo timing so a busy flood cannot miss a complete interval boundary. The 1,024-cell budget and no-overlap guard remain.

Arena overrides: lava-interval (initial cadence), lava-grace-seconds, lava-acceleration-seconds, lava-start-y (absolute world Y, clamped inside arena). Existing explicit lava-interval values remain honoured and also accelerate. Review deployed arena overrides before rollout. A deeply buried arena minimum can still delay visible lava; lava-start-y lets authors put the first layer at the intended floor. No live map configuration changed.

Four focused Volcano tests and rebuilt jar passed after this follow-up. Client playtesting still needed to judge difficulty, especially very large footprints whose fill time can exceed the requested cadence.
