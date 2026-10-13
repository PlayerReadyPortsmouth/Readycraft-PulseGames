# Match reliability — PulseGames 0.3.10 / PulseNetwork 0.1.3

Prepared by **AurorasChaos (on behalf of Player Ready)**. This document describes prepared changes, not a deployed release.

## Joining, leaving and returning

Saved return locations are plain coordinates and world identifiers, so unloaded worlds no longer break plugin startup through Bukkit Location deserialisation. Legacy files remain readable. Missing, changed, obstructed or unsafe destinations keep the player at the backend arrival point. PulseGames can reopen an existing managed editing world without generating a replacement. Return requests are authenticated and duplicate requests are ignored.

A brief disconnect retains the original server for five minutes while the proxy remains running. A late connection callback cannot discard that saved origin. Leaving a match restores a cloned inventory snapshot plus armour, health, food, saturation, experience, effects and flight state. A repeated restore without a snapshot does nothing. Rejected join teleports roll back admission, and game hook failures are recorded during cleanup.

## Arena checks

The Setup Book has a Validate arena action; `/pulse setup validate` runs the same read-only checks. Saving is blocked by errors. Checks cover required spawns, regions and settings; finite coordinates and world limits; numbered region gaps; void thresholds; mode and team capacity; Bedwars beds and generators; Deathrun trap/trigger pairs; and Dropper checkpoint starts. Duels supports at most two teams, and the engine supports at most 32.

Loaded chunks are checked for clear feet/head space. Missing floors, liquids, unloaded chunks and missing recorded bounds produce warnings for a builder to review. Validation neither loads chunks nor changes terrain. Warnings do not substitute for a playable-map review.

## Match diagnostics

Administrators can use `/pulse diagnose` for recent match references and `/pulse diagnose <reference>` for the latest 20 events. Reports cover world loading, admission, game hooks, cleanup and deletion; bots also record suspected stalls. Reports deliberately omit player names, chat, inventories and coordinates. At most 100 reports with 128 events each are retained, and writes are coalesced off the server thread. Disk-write failure is reported without an endless retry loop.

## Verification

Targeted checks passed on 13 October 2026: 89 PulseGames tests and 32 network tests (17 Paper, 10 Velocity, 5 common). The final diagnostics shutdown adjustment also passed its two-test rerun. A subsequent 18-test cleanup/join run passed, including two new regressions proving failed combat/sidebar cleanup cannot skip inventory restoration or lobby return (the other 16 overlap prior coverage).

```sh
./gradlew test --tests '*ArenaValidationTest' --tests '*MatchDiagnosticsTest' --tests '*PlayerStateServiceTest' --tests '*PlayService*' --tests '*World*' --tests '*ArenaSetup*' --tests '*Bot*' jar
./gradlew -p network-helper :paper:test :velocity:test :common:test :paper:jar :velocity:jar
```

A separate real Paper 26.2 integration probe passed 15/15 checks: inventory/armour/vitals/effects/flight restoration; harmless duplicate restoration; safe, obstructed and magma return spots; simultaneous independent clones of the same template; instance unload/deletion; and idle editing-world save/unload/reopen with its placed gold block intact. This automated probe does not replace human Java/Bedrock testing.

Probe source: `/home/ara/Projects/Ready/output/pulsegames/2026-10-13/reliability/ReliabilityProbe.java`. Runtime evidence is held in the local Paper lab's `reliability-result.json` and log. Targeted build logs are `/tmp/pulse-reliability-final-tests.log`, `/tmp/pulse-network-reliability-final-tests.log` and `/tmp/pulse-diagnostics-final-tests.log`.

Prepared artifacts:

- `build/libs/PulseGames-0.3.10.jar`
- `network-helper/paper/build/libs/PulseNetwork-paper-0.1.3.jar`
- `network-helper/velocity/build/libs/PulseNetwork-velocity-0.1.3.jar`

## Remaining limits and rollout checks

Return visits and inventory snapshots are in memory: a proxy restart loses visit history, and a hard backend crash does not have durable snapshot recovery. RESTORE has no application-level acknowledgement/retry. Physical arena validation only inspects loaded chunks. Diagnostics in-game lookup is for the current process; older retained reports are files.

Before accepting the release, check Java and Bedrock queue joins, cancellation, completed-game return, disconnect/reconnect, and an obstructed return spot across the actual proxy/backends. Verify an existing edited map in the Setup Book and inspect a match report. Rollout needs the prepared plugin jars, backups and fresh restart approval; no server restart is implied by the local test results.
