# RC1 minigame candidate

Draft only. No deployment, pack hosting, flag changes or production data writes.
Prerequisites carried from PulseGames #101 (XM) and #99 (kart physics), and
PulseAddon #107 (XM/Christmas content) and #106 (stable pack revisions).

Present Panic preserves unfinished cargo at sleighs, scores unwrapped stolen toys
as zero, uses the same wrapping clock for bots and humans (the former bot loop
was 2.5 times slower), shows named paper/own-team prompts, warns at mismatched paper and offers
use-to-toggle wrapping alongside sneak-hold on both editions. Bow, hit and delivery
cues use vanilla fallbacks. NPC cargo slows preserve Citizens' original movement
attribute base and apply the same role/cargo ratio; Paper's walk-speed setter had
replaced that base and made bots crawl. Cleanup restores both values. The small
workshop now gives all four teams equal 25-block bow-to-sleigh return routes.
Citizens node arrival uses a 0.45-block margin instead of 0.15.
Grinch Hunt resets temporary catch-up speed after
its condition expires, announces map-specific timeout requirements, reports the
actual end condition and has a clue-based bot controller. Villagers only pursue
visible Grinches or visible public clues; their ordinary melee tags use the normal
three-hit, immunity and heart-escape rules, with a difficulty cooldown. Both games validate
complete marker manifests in setup and before starting. Late spectators or pack
loss downgrade existing displays and cues to vanilla; sniffer dogs become visible
again when their custom model is removed.

The game selector shows a how-to-play/rewards card for every game through the
existing feature canary (and seasonal admission for seasonal games). Java uses the
shared protected chest menu; Bedrock uses the same instructions in a Cumulus form,
with chest fallback. Waiting/countdown sidebars label prospective token eligibility;
RUNNING uses the roster eligibility frozen at start. Practice never promises match
tokens or competitive records. Token amounts are read from configuration.

Flags: seasons.enabled=false; bots.enabled=false; features.broad=false. Existing
per-player canary and map opt-ins remain. Owner: Aurora. Review: 24 November 2026.
Retain off switches; consider broad trial only after human Java/Bedrock matches,
role balance, input controls, audio, rewards and cleanup are accepted.

## Reproduce locally

Use a fresh disposable server directory with Paper 1.21.4 and Citizens supporting
that version. The observed dependency hashes and results belong in the job status.
Create `RC1_LOCAL_ONLY`, `eula.txt`, and server.properties binding 127.0.0.1:25641
with offline mode, secure-profile enforcement disabled, allow-flight=true and no spawn protection.
The synthetic Java observer has client physics disabled across world transitions;
Citizens NPC physics and movement remain active.
The probe refuses non-loopback or unmarked servers. It is never bundled in PulseGames.
Install mineflayer separately outside the repo, then run through the shared queue:

```sh
ready-gate exec -- bash gradlew test --tests '*christmas.*' --tests '*seasonal.*' --tests '*ChristmasSetupTest' --tests '*GameGuideTest' --tests '*BotServiceTest' jar playtestJar
ready-gate exec -- python3 tools/playtest/run.py --server-dir /absolute/disposable/server --client-dir /absolute/mineflayer/install
```

The runner starts a synthetic offline client, builds/imports both small seasonal
maps, creates flat smoke maps for the catalogue, runs bots, samples state and
objective counters, invokes timeout, cleans up and stops its own server. It writes
`results.json`, `server-console.log` and `client-console.log` under that server.
Supported controllers and lifecycle-only injected NPCs are distinguished explicitly.
Flat smoke maps are deliberately unsuitable for assessing production map design.
Physical Java/Bedrock rendering, touch input, audio judgement and full-map balance
still require a human session.

## Observed results

17 November 2026, disposable loopback Paper 1.21.4 build 232 / Citizens 2.0.37
build 3733, synthetic Java observer and real Citizens NPCs:

- 356 selected seasonal/setup/guide/bot/kart/form/platform checks passed at
  3e7cdfe. After playtest fixes, 167 bot/Christmas/seasonal checks passed at
  c46459c; the final eight Christmas action checks passed at c07c2ff. These
  selections overlap; do not add them as unique test coverage.
- Both exported schematics independently passed NBT/sign/support/CSV checks:
  88 Present Panic and 63 Grinch Hunt markers, unique coordinates. All four
  workshop return routes are 25 blocks.
- Catalogue trial: all 26 guide menus contained the instruction and reward cards;
  25 games reached RUNNING (17 supported controllers plus eight explicitly started
  NPC lifecycle probes). Every attempt passed actor/instance/cloned-world cleanup.
  Nonseasonal smoke time follows each game's preparation freeze. Default modes only.
- Karts correctly refused: “PulseKarts needs the compatible PulseContent kart API
  and racing_kart model.” The built content adapter targets Paper 26.2 / Java 25;
  it was not installed on this 1.21.4 fixture. No readiness bypass or fake renderer
  was used. This is blocked gameplay coverage, not a kart gameplay pass.
- Present Panic initially had zero deliveries. After preserving the NPC movement
  base and moving the stationary observer off the route, all three NPCs wrapped,
  bowed and delivered nine presents in 120 seconds. Team points finished 18–42;
  the natural end animation and full cleanup passed. Cargo bases scaled from the
  observed Citizens base 0.3 rather than Paper's human base 0.1.
- Grinch Hunt twice banked 15 through ordinary theft/navigation, naturally ending
  at 64 and 56 seconds. Eleven NPCs plus the observer exercised the two-Grinch
  threshold and ordinary gadget-use events. No captures occurred in these rounds;
  the visible-only melee/cooldown regression passed, but village/role balance and
  tactical gadget use need human acceptance.

The full catalogue gate reported failure for zero Present Panic deliveries and
Karts admission. The focused Present Panic retest then passed; Karts remains blocked.
Earlier probe and test-fixture failures are retained with their causes in the job
status/evidence. No GitHub Actions results were used.

Guide items and round/reward messages were observed on the Java protocol client.
Mineflayer did not decode the newer scoreboard score packets reliably, so no HUD
image/render acceptance is claimed. Real Java/Bedrock devices, touch controls,
optional-pack decline/reconnect, sound judgement and production-map balance remain
unverified. Keep the candidate draft and flags off. Durable evidence and jars:
`/mnt/data/artifacts/rc1/`; status: `~/agent-jobs/native/w3/rc1-STATUS.md`.
