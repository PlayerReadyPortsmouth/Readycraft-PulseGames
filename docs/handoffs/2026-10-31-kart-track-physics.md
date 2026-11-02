# Kart track physics implementation handoff

31 October 2026. Local candidates only. No commits, pushes, merges, server writes, restarts or gameplay acceptance performed.

## Implemented

PulseGames 0.10.0 provides per-pad jump profiles (launch speed, gravity, flight deadline, minimum approach speed, cooldown, optional heading and bounded air steering), per-pad boost profiles (strength, duration, cooldown and direction), swept entry and rearming, momentum-preserving configured flight and validated map settings.

Authored anti-gravity surfaces now have actual three-dimensional contact frames, local steering, adhesion, quaternion orientation and oriented swept block collision. They can bank, climb walls, invert onto ceilings and traverse a vertical loop. Entry is restricted to the beginning of a section; endpoints are upright/horizontal. Side departures release momentum and use safe reset if the upright chassis cannot clear. Bots use the same surface solver with local centre-seeking steering. Drift and ordinary rear-impact spins are excluded while attached; anti-gravity contact boosts use full oriented overlap.

New mechanics require a default-off `karts.track-physics` gate and all original human racers' UUIDs. Finished racers remain in the eligibility snapshot so the remaining bots can finish. Disabling the trial or losing the required renderer cancels without a normal race result and cleans up seats/models. Existing unconfigured maps retain legacy physics.

The companion PulseContent compiler/Paper/Geyser changes provide an owner-scoped pose API, Java quaternion rendering with a 20 Hz animation clock, a Bedrock pose root and integer-millidegree transport. The compiler salts the kart schema so changed generated assets cannot overwrite the immutable old model ID. Surface admission requires exact model `pc_readycraft_racing_kart_3321ee79bf82`; both old and new revisions remain accepted for ordinary kart attachment. Bedrock also requires explicit Bedrock trial configuration and a fresh proxy capability heartbeat from the patched generator.

## Source locations

- PulseGames: `/mnt/data/worktrees/Readycraft-PulseGames-kart-track-physics`, base `3af224d954700b55e9a880f3296bafd73594873c`.
- PulseContent: `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content`.
- Builder guide: `docs/PULSEKARTS-TRACK-PHYSICS.md`.
- Contact data example: `docs/examples/kart-surface-settings.json` (not a built map).
- GeyserModelEngine integration source, dependency lock, license, build script, contract harness and receipt: `plugins/pulse-content/integrations/geysermodelengine-kart-pose/` in the PulseAddon worktree.

Earlier chat/safeguarding candidate worktrees were preserved and are not included in these isolated candidates. Combine through the normal reviewed release process, rather than replacing their jars with these candidates blindly.

## Validation record

The first PulseGames test run compiled but had two invalid test-surface fixtures after endpoint validation was tightened. Those were corrected; a subsequent reset fixture had nested Mockito stubbing and was corrected separately. The first wrapper invocation failed because the file was not executable; Bash invocation works. These were not passing runs.

Gate `20261031-003745-kwt`: 219 focused kart/content checks passed. Gate `20261031-004038-t95`: renderer-revocation follow-up also passed. Final gate `20261031-004112-yvf`: **222 tests passed**, including the finished-human eligibility and huge-coordinate regressions; final jar rebuilt.

PulseContent gate `20261031-004111-wet`: compiler 2, Paper 13, Geyser 20 focused checks passed and jars built. Two added readiness-boundary tests passed in `20261031-004234-1ms`; the extended exact-model attachment regression passed in `20261031-004432-213`. **37 unique companion JUnit tests passed** across these focused runs. The earlier incorrectly scoped Gradle `test` invocation selected compiler test names in the Geyser module and failed with no matching tests; explicit task paths fixed selection.

GeyserModelEngine gate `20261031-004050-vd3` passed: compile against pinned release binaries; preserved original public API; actual generated output for new/legacy/unrelated models; admission rejection without matching registration; real BetterModel reflective contracts and pose-clock construction. Candidate extension SHA-256: `d6a4c9857c43353dc72190f2f2c0e0cd01ac52ed11f88ef81ae717916464e8ed`. The earlier intermediate extension build is superseded and must not be deployed.

Targeted tests were used, not full repository suites. No Java/Bedrock client playtest has occurred. Final artifact hashes and test counts: `/home/ara/Projects/Ready/ops/readycraft/kart-track-physics-2026-10-31-validation.json`. The refreshed 59-page tracker was rendered and visually reviewed; its new section and cover were checked.

The model source bundle exported successfully in `20261031-004458-3gx` to `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content/build/kart-surface-source-bundle/manifest.json`. It includes actual BetterModel source and Bedrock bridge geometry/animation/texture inputs with hashes. It is not the final client resource pack: BetterModel Java asset generation and GME pack generation remain the authorised test-server stage.

## Deployment dependencies and limits

This is not an authorised rollout. Prepare a backed-up, reviewed MinigamesTest change containing the matching PulseGames, PulseContent Paper, PulseContent Geyser, patched GeyserModelEngine extension and regenerated packs; compare installed dependencies against the pinned versions first. No hot reload. Aurora's separate restart approval remains required.

Keep `karts.track-physics.enabled` and `kart-surface-visuals.bedrock-pose-trial-enabled` off until the corresponding trial is deliberately configured. A test map requires real geometry aligned with the contact data, ordered checkpoints and upright recovery points. Start with broad banks and a forgiving jump, then validate walls/ceilings and loops on both editions. Check missing packs, reconnect/transfer, forced cancellation, drift/trick presentation, reset yaw, collisions and NPC drivers.

The model rotates; vanilla seated player bodies and cameras remain upright. Arbitrary wall detection, surface-normal jump launches, split-route checkpoint graphs, lap records and wall-aware item trajectories are not implemented by this change. Existing shells/bananas still use their ground-oriented behaviour. The current exact zero-origin kart is supported; arbitrary replacement models need their own geometry/rotation review.

Rollback disables the trial, force-cancels active configured races and leaves ordinary maps available. Restore the previous matched jars/packs under the usual backup procedure if reverting the bundle; do not mix generated model revisions or remove registration while clients remain connected.

## Built artifacts

- `/mnt/data/worktrees/Readycraft-PulseGames-kart-track-physics/build/libs/PulseGames-0.10.0.jar`
- `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content/build/libs/PulseContent-0.6.3.jar`
- `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content/paper/build/libs/PulseContent-Paper-0.5.9.jar`
- `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content/geyser/build/libs/PulseContent-Geyser-0.3.5.jar`
- `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content/integrations/geysermodelengine-kart-pose/build/GeyserModelEngineExtension-1.0.9-pulse-kart-pose.jar`
- `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content/build/kart-surface-source-bundle/manifest.json`
