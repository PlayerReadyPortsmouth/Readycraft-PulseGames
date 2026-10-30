# PumpkinHunt standalone (Lobby): handoff, 30 Oct 2026

Brief: `claude-cobuilder/docs/halloween-spawn-2026-10-29/PLUGIN-BRIEF-pumpkin-hunt.md` (lobby list). Draft PR, version 0.1.0.

## What
New top-level module `pumpkin-hunt/` with its own Gradle build (paper-api 1.21.4, Java 21, nothing shaded). A port of PulseAddon's `pumpkinhunt` package for the Lobby, which has no PulseAddon. PulseGames and `network-helper/` are untouched. Full detail in `pumpkin-hunt/README.md`.

## Differences from the PulseAddon version
- Config keys are top level (no `pumpkin-hunt:` section). Ships `enabled: false`.
- Progress: `plugins/PumpkinHunt/progress.json`, written by `ProgressStore` on its own thread, 5 s debounce, atomic replace, flushed on disable.
- Wording: `messages.yml` (keys without the `pumpkins.` prefix), same text as PulseAddon.
- `/pumpkins reload` re-reads config.yml and messages.yml itself.
- Lantern ids made unique: `house_1`..`house_8`, `field_1`, `field_2`, `field_extra_1`..`field_extra_3`. Coordinates and hints are exactly the brief's. World is `world`; confirm that is the Lobby's spawn world name.

## Not tested in game
There is no local Lobby server. Unit tests cover progress, season window, cooldown, the store and the shipped config.

## Lobby acceptance (Java and Bedrock)
1. Jar in `plugins/`, lanterns placed, `enabled: true`, `/pumpkins reload`. Console shows no "not a jack-o'-lantern" warnings.
2. Right-click a lantern inside the spawn region: action bar progress, sound and flames for you only. Same by tapping on Bedrock; spamming within 1 s counts once.
3. An unregistered jack-o'-lantern, off-hand and left clicks do nothing.
4. `/pumpkins` shows hints only, no coordinates; `/pumpkins list` as admin shows coordinates and counts.
5. Find all 13: title, one firework that hurts nobody, reward once, one broadcast; later clicks and a relog do nothing.
6. `/pumpkins top`, `reset <player>` and `stats` work.
7. Set `end` to yesterday and `/pumpkins reload`: clicks ignored, `/pumpkins` says the hunt is over. Restart: progress survives.
