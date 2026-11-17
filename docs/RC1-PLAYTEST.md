# RC1 minigame candidate

Draft only. No deployment, pack hosting, flag changes or production data writes.
Prerequisites carried from PulseGames #101 (XM) and #99 (kart physics), and
PulseAddon #107 (XM/Christmas content) and #106 (stable pack revisions).

Present Panic preserves unfinished cargo at sleighs, scores unwrapped stolen toys
as zero, uses the same wrapping clock for bots and humans (the former bot loop
was 2.5 times slower), shows named paper/own-team prompts, warns at mismatched paper and offers
use-to-toggle wrapping alongside sneak-hold on both editions. Bow, hit and delivery
cues use vanilla fallbacks. Grinch Hunt resets temporary catch-up speed after
its condition expires, announces map-specific timeout requirements, reports the
actual end condition and has a clue-based bot controller. Both games validate
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

See the final rc1 job status and accompanying evidence manifest. Do not interpret
this draft's implementation notes as completed acceptance results.
