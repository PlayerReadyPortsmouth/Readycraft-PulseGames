# Christmas engineering handover (XM, 16 November 2026)

Author: **AurorasChaos (on behalf of Player Ready)**. Draft only, no deployment.

PulseGames owns the shared `GameInstance`, matchmaking `PlayService`, Citizens-backed
`BotService` and `BotMovement` objective hooks. PulseKarts reuses these in `games/karts`;
it is not a separate checkout. Survival chat lives in PulseAddon's `season` subsystem.
PulseContent lives in `ReadyCraft-PulseAddon/plugins/pulse-content`.

## Enable a local trial

1. Build with Java21: `bash gradlew test --tests '*seasonal.*' --tests '*christmas.*' jar`.
   The jar task also exports `build/christmas-test-maps/*.schem` and marker CSVs.
2. Install the jar in an isolated Paper server. Keep existing hub configuration.
3. `plugins/PulseGames/seasons.yml` starts `enabled: false`. Enable it explicitly,
   optionally restrict `canary-players` to tester UUIDs, then `/season reload`.
4. `/season preview christmas` is read-only. `/season set christmas` persists the
   calendar override; `/season clear` resumes the calendar. Neither bypasses `enabled`.
5. Build each small flat map: `/admin games setup christmas-test presentpanic` and
   `/admin games setup christmas-test grinchhunt`. These create new worlds named
   `rc_build_xm_pp`/`rc_build_xm_gh`, never overwrite existing worlds. Build one at a time.
   `/admin games world rc_build_xm_pp` teleports an administrator into it.
6. Validate before capture:
   `/admin games setup markers presentpanic xm_pp -46 64 -46 46 112 46 check`
   and similarly `grinchhunt xm_gh`. Repeat without `check` to capture. Existing
   arena/template IDs are refused; choose a new ID for a rebuild.
7. `/play presentpanic teams` or `/play grinchhunt classic`. The shared queue fills
   Present Panic with bots if the existing Citizens/bots configuration allows it.
   Use `/admin games start` for controlled solo flow checks; competitive balancing
   still needs several real players. Grinch Hunt has no pretend bot support.

Optional: use the exported Sponge v3 schematics instead of the builder. Paste at
**0,0,0**, preserving offset **-46,64,-46**. Both cover93×93, Y64–112. Present Panic
has88 signs; Grinch Hunt63. All20 test trees are at least15 blocks apart. Spectators
and launch points have supporting blocks; these maps have no entities/redstone.
They are engineering layouts, not substitutes for the builders' production maps.
The format follows the [Sponge specification](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md).

## Marker contract and failure recovery

The full builder brief was read from `~/agent-jobs/native/w3/christmas-maps-brief.md`.
No keys are renamed. A standing **oak sign** front has `[pulse]` on line1, a lowercase
key on line2, optional `n/e/s/w` on line3, optional developer note on line4. The sign
block position is used, with X/Z centred and Y unchanged; facing becomes yaw.

`setup markers <game> <new-id> x1 y1 z1 x2 y2 z2 [check]` scans all tile entities in
the specified box one chunk per tick, including unloaded chunks, up to200×200 blocks.
It checks required counts (the brief’s "about20" trees allows16+), colours, unique singleton keys, consecutive numeric belt
nodes and paired snow/path regions before touching signs. Repeated team spawns and
spectators are retained as lists. `settings.markers` stores records with `key` and
`location` values: dots never become YAML paths. Runtime coordinates bind to the
cloned match world. Cached marker lookups preserve numeric belt order.

On capture a durable `marker-manifests/<game>-<id>.yml` records every sign including
facing/notes before removal. Then signs are removed, the world is captured, and the
arena is published. A reported capture/publication failure restores sign snapshots.
Keep the build world unchanged during this operation. A process crash during capture
can leave stripped signs; the manifest is the recovery source. Never run setup on a
live match world. Capture does not grant visual/playtest approval in the map ledger.

## Rules and configurable interpretations

The map brief contains map design and markers, not a complete gameplay rulebook. The
following fill the unspecified gaps and should be reviewed during playtesting.

**Present Panic:**300 seconds by default (`settings.time-limit`,60–1200).2–4 teams,
up to6 per team. Move near an item-display toy to pick it up. Sneak near a wrapping
bench to hold progress; moving away/cancelling resets it. Match paper to toy colour,
then visit the bow bench for the value detour. Walk into your team's sleigh to bank.
Correct3, correct+bow6, wrong1, unwrapped0; a stolen wrapped present adds1. All those
points double in the final30 seconds. Carries3; Packer4. Every item slows you.
Speedy runs faster, Wrapper wraps in half the time, Snowballer fires faster. Use the
iron nugget to cycle roles (15s cooldown, cannot shrink an overfull stack).

Use the blaze rod to throw snowballs: enemy hits knock back and drop the top
present. Anyone can recover it; the former holder has a short repick delay.
Every45s before the last30s, one15s event: double conveyor supply/speed; twice-long
wrapping; naughty-list catch-up point per delivery visit for the trailing team; or
snow/speed reduction. Defaults use60/30 tick wrapping,40 tick toy supply. These
short mechanical constants are in `PresentRules`; event cadence is in the game.
The leading sleigh team(s) win; an empty0-score game is a draw. Sleigh displays fly
towards the exact `.launch` markers. MVPs: Master Wrapper, Present Packer, Sneaky Elf.

**Grinch Hunt:**300 seconds (`settings.time-limit`). One Grinch, two with12+ players.
Invisible Grinches sneak at trees to steal, carry up to3 and bank at `gh.cave.pile`.
Bank15 for an immediate win. At timeout, an uncaught Grinch carrying at least
`settings.timeout-held-min` (default1) wins; otherwise the village wins. This is the
explicit interpretation of "holding presents at time-out". Footprints every
`settings.footprint-spacing` blocks (default2.5,1–6), face travel. Fresh snow prints
last90s; cleared paths/indoors/sneaking are fainter/shorter. Carrying leaves glitter.
Sneaky Shoes quiet the trail; Decoy Prints mislead trackers; Snow Flurry briefly
obscures nearby villagers; Heart Grows Three Sizes grants one automatic/manual
escape; Chimney Dive cycles nearby chimney markers. The cave back exit is one-use.

Villagers cycle Tracker, Lamplighter, Snowballer, Bell Ringer, Elf Inventor, Night
Watch using the nugget and use the held gadget to activate their role. Tracker's
compass/dog follows freshest prints including decoys. Lamps reveal nearby Grinches;
Snow Dust reveals and direct snowballs count as tags; bell towers jingle nearby
Grinches; traps reveal/slow; flares reveal eyes. Catch with3 spaced hits or a3s sneak
hold within reach/line of sight. The one heart escape is consumed before capture.
Caught carriers return cargo to its original trees. Tree theft rings an alarm.

Catch-up defaults: below4 banked at halfway, Grinches get a small speed boost; at10+
banked, village bells reveal Grinches briefly every15s. Endings celebrate the village
or Grinch; MVPs: Present Pilferer, Village Tracker, Christmas Saver. The game owns its
completion decisions so shared last-player-standing logic cannot end it prematurely.

## Cosmetics, visuals and editions

Finished seasonal rounds unlock `xm-snow-trail` and `xm-bells`. These cannot be bought
with gameplay tokens. Ownership/equipped choices persist when seasons change;
`wear-out-of-season` suppresses rendering/equipping rather than deleting rewards.
Survival has a separate cosmetic-only challenge wallet, documented in the addon PR.

The games use PulseContent's additive `pulse:christmas_*` item models only when every
human viewer has acknowledged compatible content. Mixed/Geyser/declining groups use
visible wool presents, carpet prints, minecart sleighs, dye eyes, a vanilla sniffer
Wolf, vanilla sounds and particles. No invisible barrier walls or Java-only menus.
Each match bounds its owned displays, dogs and projectiles below the builder budget;
cleanup removes them on end, quit and aborted/shutdown cleanup.

PulseContent's current runtime targets Paper26.2. To test on Paper1.21.4 without that
runtime, use the separately exported **christmas-java-1.21.4.zip**. Host it yourself
when approved and set `resource-packs.christmas.url` (HTTPS) and `sha1` in seasons.yml.
The server offers it optionally as an additive pack, tracks the exact hash-derived
UUID, and uses it only after SUCCESSFULLY_LOADED for that offer. No URL/hosting is
configured or published by this draft. Bedrock always has the vanilla fallback.

Seasons use inclusive London dates, a New Year window spanning December/January,
and Gregorian Easter relative to its Sunday. Default disabled, reloadable at runtime.
Config decorations are native particle IDs; pack overlays select additive models
rather than forcing pack replacements. Other seasonal game/content lists are extension
points; only the two requested Christmas games are added here.

## Verification remaining before enabling

Record the actual queued build/test results in the PR. These drafts do not claim live
or client gameplay validation. Human checks: Java+Geyser queue/start/role controls,
wrap interruption, carry/drop/steal, all events, tag hold and one escape, second Grinch,
footprint visibility/lifetimes on all three ground types, cosmetics across reloads,
win/lose/timeout/quit/shutdown, return hub and no leftover entities. Production builders
must measure20–25s workshop loops and25–35s cave trails on their real maps, and provide
their screenshots/handover. No deploy, merge, hosting or production flag writes here.

Observed verification on16November2026: final ready-gate job `20261116-171557-eli`
passed Java compilation,180 selected tests (seasonal, both games, queue-fill, shared
engine and economy), jar build and schematic export. Independent
`python3 tools/check-christmas-maps.py` parsed both complete NBT files and verified
88/63 exact sign texts/positions against the CSVs, unique positions and sign support.
The initial180-test run exposed3 issues; the scoped seasonal admission guard,
earned-only catalogue expectation and no-server sound fixture were corrected before
this final pass. No client/server gameplay or production deployment was performed.
