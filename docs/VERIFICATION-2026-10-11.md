# Follow-up verification — 11 October 2026

Foundation PR #2 is merged. This follow-up remains source under review. No
production files, runtime flags, packs or server versions were changed.

- PulseGames `test jar`: 50 tests, zero failures/errors/skips. Version 0.3.0,
  Java 21 toolchain, Paper 1.21.4 compile API.
- Companion PulseContent `:paper:test :paper:jar`: 10 tests, zero failures/errors/skips.
  Version 0.3.1, Java 25, Paper 26.2. Based on PulseAddon PR26 head `0e33e5c`.
- Localhost-only disposable server: Paper 26.2 build56 commit `8cd4f47`, Temurin
  Java25.0.4.1, Citizens2.0.44 build4261 from official Citizens CI. No public server.
- Eight live NPC assertions passed: real valid Player actors; explicit Bot_ names;
  absent from online sessions; movement over 3 blocks on a flat platform; ordinary
  Player.attack reduced a second NPC from 20 to 19 health; individual removal;
  idempotent provider cleanup; no surviving Bukkit entities. Actual movement was
  6.76 blocks. The server was then stopped cleanly.
- Both `dodgeball-court` and `dropper-two-drops` blueprints stamped geometry,
  captured templates and registered arenas on the isolated server.

The probe initially exposed Citizens' separate enum/string metadata stores:
string protection settings did not apply. The adapter now resolves the public
enum constants and uses the enum overload. Respawn is explicitly disabled with
-1. No forced damage or cancellation override was used to make the probe pass.
Navigation without human clients required force-loaded chunks in the test world.

Queue timing, seat replacement, allowlists and cleanup are unit-tested with an
injected transport. The live probe tests transport rather than a complete match
with connected humans. Java/Bedrock bot appearance, complete game sessions,
multiple simultaneous arenas, difficult maps and provider shutdown mid-match
still need acceptance testing. New games and bots default off.

Prop Hunt's companion model API has boundary/lifecycle tests but no live rendered
player model or mixed-edition acceptance. An original model with the required
animation tracks must be compiled through PulseContent, then the acceptance list
in PULSECONTENT.md must pass. Do not describe this as complete Geyser Prop Hunt.

Local evidence and reproducible probe source:
`/home/ara/Projects/Ready/output/pulsegames/2026-10-11/`.
The disposable server and its libraries are under `/mnt/data/tmp/pulsegames-bots-lab`.

Artifact SHA256:

```text
PulseGames-0.3.0.jar 5dcaa6636b122ec5f3801360d14305af179e79fa76944f7f6f760ccbbc2750b1
PulseContent-Paper-0.3.1.jar 256447af5d17cdb724384e98db8a423040b6ff97974d71e760c66619b6d588e7
```
