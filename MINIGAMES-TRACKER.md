# Minigames development and play-test tracker

Last updated: 15 October 2026. Owner: Aurora. Agents should update this file as
work and player feedback arrive; keep it in the PulseGames repository.

Readable report: [HTML](MINIGAMES-TRACKER.html) · [PDF](MINIGAMES-TRACKER.pdf).
After editing this source, regenerate both exports with
`node tools/render-minigames-tracker.mjs --pdf`; see [update instructions](tools/TRACKER-README.md).

## 15 October - Custom karts deployed; driving bots being prepared

Current deployed release: **PulseGames 0.3.19 and PulseContent Paper 0.5.5** on MinigamesTest, with the primary proxy's kart content registration updated. This replaces boat steering with a custom kart controller/model, enables Avalanche's inward shrinking arena and fixes the ReadyPlayerZ round-start sound failure that prevented zombie spawning; active-wave darkness is included. Deployment is distinct from client acceptance.

**PulseKarts bots are a pending candidate, not live or accepted.** Work covers Sprint/Cup driving through the same physics and checkpoint rules as human racers, route-aware recovery, difficulty, item use and queue eligibility. Each map needs explicit consecutive `bot-waypoint1` onward plus reviewed `checkpoint-spawnN` reset points. Validation checks an ordered circuit through every checkpoint and finish; it cannot prove physical traversability. Existing maps do not gain bot eligibility merely because the controller exists. See [kart setup and testing](docs/PULSEKARTS.md).

**Prepared 0.3.20 candidate:** the 15-second opening menu offers Balanced, Light (1.25x acceleration, 1.15x steering, 1.25x incoming bump duration), Heavy (0.8x acceleration, 0.9x steering, 1.35x outgoing bump duration, 0.7x incoming duration) and Trickster (0.92x top speed; more turbo/lightning). Bots use the same style tradeoffs. Base speed is 12 blocks/second with 126-degree/second steering before speed/style scaling. Rotated terrain collision follows the actual kart footprint and strict floor contact keeps the chassis above the road surface. Karts pass side by side; qualifying rear impacts cause slowdown/spin with three simulation seconds of immunity. Cup already supports custom `laps` settings (default three); Sprint is one lap. Final candidate checks are still in progress and no new gameplay pass is claimed here.

**Companion visual candidate:** PulseContent Paper 0.5.6 uses immediate chassis rotation; Geyser extension 0.3.3 adds an explicitly configured, allowlisted kart-to-ArmorStand visual link with zero offset, separate from player cosmetics. Both remain prepared. The strict-floor change addresses a probable black-model lighting cause; Bedrock movement/rotation and Java appearance must be retested after an authorised deployment.

**Open player feedback:** Bedrock kart movement lags and rotation follows late; Java's kart appears black; driving feels slow or stuck. These reports remain unresolved client acceptance items. Do not mark rendering, handling, bot laps or crossplay passed from automated tests. Further deployment/restarts need fresh authorisation.

## 15 October - Natural bot behaviour and Quake loadouts: prepared

Aurora reports Quake should spawn participants across the arena and bots should
try to escape instead of constantly approaching. Build 0.3.15 implements safe
random opening/respawn placement for both humans and bots, including teams, plus
weapon-range strafing and close-range/low-health retreat. A new optional
`spawn-area` region supports multilevel playable volumes; existing maps fall back
to the envelope of their anchors and retain those anchors as emergency fallback.
Loaded chunks only, bounded floor probes, headroom/hazard checks and player
separation are covered by targeted tests. Client movement and map suitability
remain unverified. This update is prepared, not deployed; no restart performed.

The same prepared build now adds playful NPC-labelled names for all supported
bots, steadier visible-target choices and varied generic combat reactions/path
updates. Urgent hazard/bridge controllers keep their timing. Block Party varies
equally suitable tile choices and Spleef varies action timing. Quake has a
15-second opening selection for one of three weapons and two of four abilities:
Railgun/Scattergun/Pulse Blaster; Dash/Jump/Shield/Sprint. Bots select varied
loadouts and use the same cooldowns. Temporary effects restore on leaving/death.
These are implemented changes awaiting client tests, not a claim of human-level
play across every game. See [bot coverage](docs/QUEUE-BOTS.md).

Since the previous deployment, Sunken Archive and Brassworks have been configured
with bots enabled. The missing `games.preview` entry for Quake was corrected on
15 October; configuration reload was requested in-game. See [Quake guide](docs/QUAKE.md).

## 15 October - Quake and map disguise palettes deployed

PulseGames 0.3.14 is live on MinigamesTest. The primary proxy restarted with fresh
Geyser registrations; both services are healthy. All 129 combined targeted tests
passed before deployment. Existing content was preserved, and the five palette
settings are the only arena-definition changes made by this release. The server
loaded 59 arenas and 24 games; the live bot service reports `quake:classic` ready.

The 27 supported props are available in the expanded content catalogue. Reviewed
map palettes now offer Backstage Mayhem 20, Crooked Library 11, Harvest Festival 16,
Market Day 19 and Smuggler's Wharf 13 choices. Players, bots and decoys use the same
map list. The Setup Book and `/pulse setup palette` commands support bounded scans
of loaded, selected regions, manual review and global fallback. No world templates
were changed. Java and Bedrock content revision and registry fingerprints match.

Quake bots are deployed for solo/team variants, with weapon choice, visible-target
combat, abilities and adaptive aim/reaction. No Quake maps are configured yet.
Build at least four spawns, solid cover, connected walkable routes and protected
spawn sightlines; see [Quake setup](docs/QUAKE.md). Actual navigation and weapon
balance need testing on built maps.

Next acceptance: reconnect Bedrock for the updated pack, then test each map's
selector, new prop appearances, bot/decoy disguises, movement and locking on both
clients. Backend verification is not visual client approval. Backup contains only
changed plugin/content/settings data (about 6 MB), not worlds. See
[Prop Hunt setup and rollout](docs/PROP-HUNT.md).

## Working agreement

- Goal: a broad mix of party, co-op, racing and competitive games, all with proper
  objective-playing queue bots, followed by map building and two-human-player tests.
- Test on **MinigamesTest**, reached through the existing primary proxy. Never use
  SurvivalTest for this work. Preserve lobby and build-world edits.
- **Ask Aurora before every server restart.** The one-time permission on 11 October
  was consumed by the 0.3.2 cleanup/update. No standing restart permission exists.
- Use PulseContent for custom items/entities and check Java and Bedrock through
  Geyser. Do not treat a successful Java test as crossplay acceptance.
- A bot must perform objectives, obey game rules, handle death/respawn and finish
  rounds. Spawning or filling a slot alone does not count as support.
- Preserve distinctions: planned → implemented → locally checked → deployed to
  MinigamesTest → player tested → accepted. Merged does not mean deployed.
- Record who reported a result, date, map/mode, client, player/bot counts and any
  evidence. Write “not recorded” where details are unknown. Do not invent passes.
- Verify map names against the saved `plugins/PulseGames/arenas/*.yml` definitions: record `display-name`, `id`, `game` and `world-template`. Do not infer names from build-world folders. Check the template exists and record modes/spawns separately from player acceptance.
- Before changing a status, check current source, PR and server version. Append
  significant results to the dated log; do not erase failures to make a row green.

## 14 October - Adaptive bots, queue rules and parties: prepared candidate

Maps are being built; existing controllers still need player testing. PulseGames
0.3.12 and paired PulseContent Paper 0.5.4 are prepared, not merged or deployed.
The content candidate excludes the separate cosmetic movement trial.

- **Filling:** maps above four slots allow bots up to half capacity, rounded down,
  alongside humans: 12 slots + five humans permits six bots. Four slots or fewer
  retain the full-fill exception. Match/global caps, playable minimum and map
  opt-in still apply. Humans replace bots during waiting/countdown, not mid-round.
- **Competitive eligibility:** freeze the actual starting roster. Larger games
  count with at least 50% humans; small games need more humans than bots. Thus
  3 humans + 1 bot in a four-slot game counts, while 2 + 2 does not. Eligible
  rounds use existing records and match rewards; NPCs never earn either.
- **Adaptive difficulty:** per-player/per-mode recent results calibrate reaction
  and accuracy; general decisions use the group's lower median. Profiles freeze
  per round. New profiles start Normal; six results minimum, neutral smoothing,
  last 40 outcomes. No damage, health or reach advantage. Fixed Easy/Normal/Hard
  overrides remain available through the Setup Book.
- Calibration has separate local storage; old mixed-mode competitive totals are
  not imported. Forced, drawn, zero-duration and abandoned rounds do not train it. Winning
  teammates, co-op outcomes and Infection role conversion receive explicit handling.
- **New controllers:** KitPvP One in the Chamber uses actual arrows, ammunition,
  bow draw and melee fallback. Prop Hunt Classic/Infection hiders use supported
  cells and normal locking; seekers use visible motion, short memory, nearby
  heartbeat clues and ordinary reach inspections. No role powers or authored hiding routes yet.
- Custom Prop Hunt bots require the paired NPC prop API, which validates owned
  Citizens subjects while retaining resource-pack checks for real viewers.
- **Parties:** existing server-local parties are exposed from the game menu;
  stale invitations, changed queue membership and duplicate admission counting
  are corrected. Bedrock has typed accept/deny commands. Cross-server parties
  and guaranteed team-preserving placement are future work.

Focused bot, queue, party, Prop Hunt and eligibility tests pass; paired renderer
has 16 focused tests passing. Both jars build. No live world/configuration changes
or restart are part of this preparation. Source coverage becomes **12/23 games**
with the Prop Hunt addition; OITC expands an existing game. Crystal/Mace Duels,
Skywars/Bedwars, racing/building controllers and TNT Run Double Jump remain future work.

Next: test queue counts and replacement, eligible/ineligible rewards, parties,
OITC ammunition/respawn and both Prop Hunt roles on approved maps in Java/Bedrock.
Existing explicit arena difficulty overrides are preserved; choose Adaptive to
activate calibration. See `docs/QUEUE-BOTS.md` for rules and acceptance steps.

## 14 October - Command setup and optional bot navigation: draft extension

PulseGames draft #13 now includes coordinate-based arena setup. `pos1`/`pos2`
accept explicit block coordinates; `region` accepts both corners directly.
Lobby, spectator, spawn, named location and location-list commands accept absolute
coordinates with optional facing. Region/spawn/list removal commands support
corrections without clicking an item. Existing permissions, map-world checks,
selection limits, validation and save requirements remain enforced. These are
commands for an authorised in-game agent account, not a console session API.

The Setup Book includes optional patrol points, hiding spots, recovery walk targets
and named ordered routes, all with command equivalents. Generic combat patrols and
Prop Hunt consume the relevant hints; hazard modes retain their own movement.
Hints do not load chunks or teleport bots. Unsafe hints are ignored, route
reachability still needs map testing, and full-player clearance remains required.

65 focused tests pass and the candidate jar builds. Prepared only: no merge,
deployment, map changes or restart. See `docs/MAP-SETUP.md` for syntax and limits.

## 14 October - Parties, team balance, diagnostics and Skywars: draft extension

All four improvements are prepared in draft PR13, with 87 targeted tests passing
and the PulseGames 0.3.12 jar rebuilt. Nothing deployed or restarted in this pass.

- **Party admission:** whole groups reserve room before joining, reclaim waiting
  bot seats together, and cannot trigger a start part-way through admission.
  Cancelled/failed additions roll back. Parties too large for a team are rejected;
  team placement keeps parties intact. Solo modes still treat friends as opponents.
- **Team balance:** coarse recent player difficulty and bot strength guide team
  allocation while preserving parties and configured capacity. It is a bounded
  balancing heuristic, not a promise of perfectly equal matches.
- **Diagnostics:** admins can use `/pulse bots inspect [name-or-UUID]` for current
  objective, difficulty, target/destination, route, progress and stall reason.
  No player win/loss history is exposed.
- **Skywars:** solo, doubles, insane and generated team-size variants gain nearby
  visible chest looting, finite inventory/equipment upgrades, bounded level
  bridging and ordinary melee. Inventory and placement protections are respected.
  Flat full-cube paths are required; stairs, mining, bows and advanced item tactics
  are not implemented. Missing blocks or an unsafe step causes a stop.

Candidate source coverage is now **13/23 games**; deployed coverage remains
unchanged. Cross-server parties, Crystal/Mace controllers, Bedwars and the other
remaining game controllers still need work. Earlier dated entries describe their
state at that checkpoint, not the latest candidate.

Next acceptance: party joins during countdown, failed/cancelled admissions,
whole-party team placement and mixed skill balance; then Skywars chest protection,
bridging/exhaustion, movement, team combat and cleanup on Java and Bedrock.
See `docs/QUEUE-BOTS.md` and `docs/MAP-SETUP.md` for the test and map requirements.

## 14 October - Network parties, match flow and bot practice

All five follow-ups are prepared in the existing draft, not deployed. PulseGames
remains the unreleased 0.3.12 candidate; the network helper becomes 0.1.4.

- **Network parties:** proxy-owned invitations and group transfers to advertised
  queues. Everyone must arrive before group admission. Each person retains their
  own source-server return ticket; failed and late transfers have cancellation
  handling. Parties are memory-only across proxy restarts. Split-server parties
  join existing advertised queues, rather than creating a new remote queue.
- **Results:** personal/team scores, kills/deaths, actual end-of-round token credit
  and competitive eligibility. `/results` offers Play again, Change game and Return
  through an inventory menu or typed commands. Reopening results cannot pay again;
  eliminated participating teammates receive their team's victory outcome.
- **Rejoining:** a default 30-second waiting-seat reservation pauses countdown and
  forced starts. Live-round disconnects retain elimination rules; a quick return
  can spectate an ongoing round, without a new life or reward entitlement. Proxy
  reconnect acknowledgement allows this before normal source-return handling.
- **Private practice:** `/pulse practice start <game> <mode> <map> <difficulty> [bots]`,
  plus report/end/repeat. One tester gets a private cloned map, fixed difficulty,
  normal bot caps and no competitive rewards or adaptive history. Five-second
  samples report observed objectives, stalls and blocked-route reasons. Repeating
  preserves the scenario settings, not random loot or exact outcomes.
- **Island bots:** Skywars gains checked adjacent height steps, straight stairs/slabs,
  ordinary jumping and finite bow actions. Bedwars gains real resource pickup,
  finite shop purchases, bounded bed defence/bridges and public enemy-bed objectives.
  Protection events and finite inventories remain enforced. Long void jumps,
  pearls, potion tactics and polished map navigation remain outside this baseline.

Validation: **176 targeted PulseGames tests and 39 network-helper tests passed**,
with no failures or skips. All three candidate jars built. No live world or server
configuration changes, merges, deployments or restarts were performed.

Candidate source coverage becomes **14/23 games** with Bedwars. This is not a
claim of player-tested or deployed coverage. See `docs/PLAYTEST-WORKFLOW.md` for
commands, test cases, limitations and the required Java/Bedrock acceptance pass.

## 14 October - Map reviews and Prop Hunt powers

Prepared in existing draft PR13; not deployed. Map agents can register builds,
check saved configuration and export a standalone HTML review report with
`/pulse maps`. Built, visual approval, Java, Bedrock and bot playtests are separate
explicit evidence records. Configured is calculated independently; no automatic
check grants visual approval or proves gameplay works.

- Evidence records include reviewer, date and notes. Captures and configuration
  changes make older evidence stale; external terrain edits require an explicit
  revision command. Missing templates and damaged capture markers cannot reuse
  current approval evidence.
- Validation checks game requirements without loading worlds or chunks. Physical
  clearance, navigation, appearance and client acceptance remain manual tests.
- Prop Hunt bots choose fitting palette disguises, place nearby independent
  decoys and use sprint and seeker pulses through the normal player handlers.
  Seekers receive coarse signals, never a hidden bearing; decoys can fool them.
- Existing five-map visual approvals remain recorded below. They are not silently
  imported into a server ledger. These tools do not automatically publish maps.

Next acceptance: Classic and Infection, both roles, Java and Bedrock, one-block
gaps, wall locks, decoy limits, false pulse signals and concurrent-map cleanup.
Decoy placement is local and random; elaborate scene planning is not implemented.
See `docs/MAP-REVIEW.md` for commands and the testing checklist.

## 14 October - PvP bots, kits and shared rewards

Prepared extension to PulseGames draft13 and PulseContent draft35; not deployed.
All four agreed tracks are implemented for testing. Source bot coverage remains
14/23 games; Crystal and Mace expand Duels rather than adding new game families.

- Crystal bots use finite obsidian/crystals/totems, ordinary protection events,
  visible opponents and blast-spacing heuristics. Mace bots use real wind charges,
  native lift and falling strikes. Difficulty adjusts reactions and accuracy;
  no damage or movement boosts. Live physics/Citizens acceptance is outstanding.
- Personal kit menus retain style -> discipline/team size -> kit, add pagination,
  equipment previews and unavailable-choice explanations. Independent preferences
  persist through the existing backend store; preferences were not migrated to SQL.
- Diagnostics explain calibration, fixed overrides and cold starts. Practice
  reports highlight repeated navigation trouble, difficulty samples and early
  tester elimination without claiming measured balance or training profiles.
- Shared SQL progression uses atomic match receipts, counters and cosmetic awards.
  A durable PulseGames outbox retains failed delivery for retry. Practice, NPCs,
  spectators and forced/aborted endings are excluded; agreed human/bot thresholds
  are unchanged. `/challenges` shows goals, progress and dated unlocks.
- Configurable milestones cover games played, wins and kills, scoped globally,
  by game or by named event. Event trophy ownership is supported; physical trophy
  models/displays remain planned. Survival cosmetic restrictions still apply.

Progression defaults off pending explicit additive SQL migration and configuration.
No live schema, configuration, deployment or restart was performed. SQL concurrency
checks use H2/MySQL mode; real MariaDB acceptance remains required. Advanced bot
healing, pearls and aerial steering are future work. See
`docs/PVP-PROGRESSION-TESTING.md` for the four-part test runbook.

## Current test-server baseline

- Last verified deployed on 13 October: PulseGames **0.3.11** and PulseContent Paper **0.5.2**. The new bot candidate below is not deployed. Older dated entries are historical checkpoints.
- MinigamesTest now has **4 CPUs / 5 GiB RAM**. PulseNetwork 0.1.2 is installed for Lobby, SurvivalTest and MinigamesTest plus the primary proxy.
- Java and Bedrock can connect through the primary proxy. Access is no longer
  restricted by MinigamesTest's whitelist; proxy authentication remains in place.
- Lobby mob spawning disabled; 62 loaded hostile mobs removed earlier.
- New `/pulse world <name>` build worlds default to void, with a 7×7 quartz spawn
  platform. Existing worlds and edits persist. Use `normal` explicitly for terrain.
- Six initial test arenas existed. Three user-built Duels arenas were subsequently
  saved, and the server logged **9 loaded arenas** at 17:20 UTC on 11 October.
- Current bot controllers cover only **11/23 games**: Duels, KitPvP FFA, KOTH,
  Dodgeball, Villager Defense, ReadyPlayerZ, Block Party, Avalanche, Spleef, TNT Run and Volcano. Global enablement, per-arena permission, capacity and feature access
  also matter. Other games are not ready simply by setting `bots-enabled: true`.

## Released update — player acceptance outstanding

| Change | Current status | Remaining acceptance |
|---|---|---|
| Arena Setup Book and interactive checklist | Implemented; approved mockup; focused tests and local Paper menu probe passed | Real Java/Bedrock clicks, prompts and save flow |
| 1.8-style PvP | Scoped OCM candidate passed local module/attack-speed restoration check on an owned NPC | Mixed human/bot damage, knockback and blocking checks on both clients |
| Dodgeball pacing and feedback | Prepared: 0.75s → 0.35s default throw delay; visible cooldown, hit sounds/messages, elimination title | Player feel and crossplay feedback acceptance |
| Prop Hunt startup failure | Fix deployed; Aurora reports Prop Hunt works nicely on Bedrock (13 October) | Record tested map/mode/player count; revised movement, preview and powers need separate acceptance |
| Prop Hunt two-player start | Deployed: both modes minimum 2; reject a solo forced start | One seeker and one hider completing Classic and Infection |
| Bot difficulty and co-op controllers | Villager Defense merged in PR4; ReadyPlayerZ fighting, earned-point purchases and revival locally checked in the next candidate | Full multi-wave survival, human/bot balance and crossplay |
| Bot queue target and Block Party | Merged candidate targets half of map/mode capacity, or full capacity for maxima 1–4; Block Party public-colour tile navigation locally checked | Capacity/load tests, full colour rounds and human/bot acceptance |
| PulseNetwork helper | Paper, Velocity and Create/NeoForge adapters prepared; public chat, local fallback chat games and queue Join actions | Java click, Bedrock form and real two-player moderation/return acceptance |
| PvP team sizes and map capacity | Prepared solo/doubles/triples/squads for main combat games, 2v2 Duels rounds and per-mode map limits | Test each mode on suitable maps; do not enable team modes on undersized maps |
| CoBuilder command-announcement disconnect | Client mod 0.4.1 installed locally; running client must be relaunched | Fixed mod installed locally; fully relaunch Minecraft and retest announcements |
| Distant lobby Trial Chamber | Diagnosed; bounded cleanup tooling prepared | Separate permission and fresh edit-preserving checks before cleanup |

PulseGames PR4 was merged at Aurora’s request on 11 October (merge `0eda3bd`).
PulseContent PR28 and PulseGames PR6, PR7 and PR8 are merged. Game/content updates are running on MinigamesTest; the final helper activation is restricted to Lobby, SurvivalTest and MinigamesTest plus their primary proxy. Human acceptance remains outstanding.

## Active map batch: Prop Hunt

**Register each map after Aurora approves it, then move to the next review.**
Aurora authorised this sequence on 11 October after the upper-hiding and barrier
feedback. This supersedes the earlier all-five-before-any-setup gate. Construction
checks alone remain insufficient permission to register an unapproved map.

- Preserve the permanent building worlds, map geometry and main lobby.
- All five now have reachable furnished upper hiding areas and barrier containment.
- Record Aurora's approval per map, then capture/register only that approved map.
- Arena setup is separate from Java/Bedrock acceptance and played rounds. Server
  updates/restarts remain with Aurora's other agent; no restart was made here.

| Map | Build-world name | Build state | Aurora visual review | Arena setup |
|---|---|---|---|---|
| 1 - Market Day | `rc_build_prophunt_market_day` | 93x93; eight ladder lofts, two mezzanines and containment. Builder checks passed (47,794 states). | Approved for setup; requested additions completed | Saved/live: `market_day`; template `market_day` |
| 2 - Crooked Library | `rc_build_prophunt_crooked_library` | 113x47; three furnished levels, paired stairs, 18 additional archive clutter groups and containment with preserved roof edges. Builder checks passed (49,242 states). | Approved by Aurora after archive clutter revision | Saved/live: `crooked_library`; template `crooked_library` |
| 3 - Harvest Festival | `rc_build_prophunt_harvest_festival` | 93x85; six barn/mill mezzanines, farmhouse loft and containment. Builder checks passed (42,945 states). | Approved in farm world; setup requested | Saved/live: `harvest_festival`; template `harvest_festival` |
| 4 - Smuggler's Wharf | `rc_build_prophunt_smugglers_wharf` | 109x93; warehouse upper floors, four mezzanines, cabin roof hiding and containment. Builder checks passed (67,514 states). | Approved by Aurora | Saved/live: `smugglers_wharf`; template `smugglers_wharf` |
| 5 - Backstage Mayhem | `rc_build_prophunt_backstage_mayhem` | 101x85; ten room mezzanines, repaired catwalk, corrected stage-facing seats and containment. Builder checks passed (66,024 states). | Approved; requested seating correction completed | Saved/live: `backstage_mayhem`; template `backstage_mayhem` |

Visit with `/pulse world <build-world name>`. These names identify permanent build
worlds; the saved arena/template IDs are separately recorded above. Each registered
map has Classic and Infection, 2-16 configured players, seven spawns (one seeker,
six hider scatter points), enclosed waiting/spectator platforms and
`bots-enabled: false`. Void-y is 74 for the four floor-80 maps and 67 for Crooked
Library, below its archive floor at y73. Setup checklists passed and `/pulse arenas` confirmed all five
saved IDs/templates loaded. Gameplay and crossplay tests remain pending.

## Active map batch: Combat and Dropper

Aurora commissioned **three maps each for Kit PvP, King of the Hill, Dodgeball and Dropper**, in that order, after completing the lobby additions. Dedicated permanent worlds retain the `rc_build_<game>_<map>` naming scheme. Build checks, Aurora approval, registration and played tests are separate milestones. None of this new batch is registered until approved.

| Game | Map | Permanent build world | Build/review state | Arena setup |
|---|---|---|---|---|
| kitpvp | Sunken Citadel | `rc_build_kitpvp_sunken_citadel` | approved; registered; gameplay test pending (48,010 checked states) | Registered |
| kitpvp | Neon Freight | `rc_build_kitpvp_neon_freight` | approved; registered; gameplay test pending (42,657 checked states) | Registered |
| kitpvp | Wildwood Ruins | `rc_build_kitpvp_wildwood_ruins` | flora added; approved; registered; gameplay test pending (41,701 checked states) | Registered |
| koth | Ember Spire | `rc_build_koth_ember_spire` | approved; registered; gameplay test pending (44,257 checked states) | Registered |
| koth | Clockwork Plaza | `rc_build_koth_clockwork_plaza` | approved; registered; gameplay test pending (44,569 checked states) | Registered |
| koth | Frostbound Keep | `rc_build_koth_frostbound_keep` | approved; registered; gameplay test pending (44,967 checked states) | Registered |
| dodgeball | Neon Circuit | `rc_build_dodgeball_neon_circuit` | city backdrop added; registered; gameplay test pending (31,371 checked states) | Registered |
| dodgeball | Bamboo Courtyard | `rc_build_dodgeball_bamboo_courtyard` | themed backdrop added; registered; gameplay test pending (23,885 checked states) | Registered |
| dodgeball | Orbital Hangar | `rc_build_dodgeball_orbital_hangar` | themed backdrop added; registered; gameplay test pending (22,879 checked states) | Registered |
| dropper | Crystal Descent | `rc_build_dropper_crystal_descent` | approved; registered; gameplay test pending (47,515 checked states) | Registered |
| dropper | Canopy Plunge | `rc_build_dropper_canopy_plunge` | approved; registered; gameplay test pending (46,387 checked states) | Registered |
| dropper | Reactor Dive | `rc_build_dropper_reactor_dive` | approved; registered; gameplay test pending (47,099 checked states) | Registered |

All twelve maps are built, builder-checked, visually approved by Aurora and registered. Three each for Kit PvP, KOTH, Dodgeball and Dropper are available in their configured random map pools. Dropper landing regions were selected by the operator with the named wand. Played acceptance remains pending: combat rounds and bot navigation, Dodgeball solo/teams, and actual Dropper falls, retries, checkpoint transitions and race/practice completion.

Requirements checked in `docs/MAP-SETUP.md` and game source: Kit PvP scattered spawns; KOTH a `hill` capture region with four or more approaches/spawns; Dodgeball opposite first-two team bases and an enclosed court; Dropper water-only finish/checkpoints with matching next-launch locations. Dropper paths require actual falling/steering acceptance, not only block checks.

Lobby additions are built and builder-checked: reactor, four district emblems, guarded rooftop viewing/jump route with ladder, café/garden/terrace, hidden arcade/maintenance passage and unobstructed north Skippy photo point. Final exact check: **8,701/8,701 states**. Automatic parkour resets and arcade gameplay are not implemented. No restart performed by this builder.

## Team-size and map-capacity programme

Aurora confirmed: **sizes 1–4 (solo, doubles, triples, squads), with the number of
opposing teams controlled by each map**. This is separate from how many people
are currently queued.

- Prepared variants: Duels, KitPvP, KOTH, Dodgeball, Skywars, Bedwars and Lucky Pillars.
- Duels stays two opposing sides (including 2v2, 3v3 and 4v4); a round ends after
  the opposing side is out, not on the first teammate death.
- Skywars/Bedwars use map-defined team slots, bounded by spawns and required bases.
  Solo Bedwars uses one-player teams with separate beds.
- Setup Book: per-mode minimum/maximum players, team count and bot difficulty.
  Existing saved arena modes and live maps have not been changed.
- Prop Hunt's seeker/hider roles and Party Games' individual rotating competition
  remain distinct; symmetric team variants for these need a separate rules design.
- Team modes do not imply bot support. Deployed controllers now cover 11/23 games;
  Villager Defense, ReadyPlayerZ and Block Party are merged; the latter two passed focused local checks.
  Avalanche, Spleef, TNT Run and Volcano are prepared in merged PR6, bringing deployed controllers to 11/23. Full player acceptance is still pending.
- See [team and capacity instructions](TEAM-MODES.md) for exact settings and examples.

## Queue bots and network helper

- Requested fill target counts **humans plus bots**: maximum 2 → 2 total, 4 → 4,
  5 → 3, 8 → 4, 16 → 8. Odd halves round up. The playable minimum can raise
  that target. Humans reclaim bot seats before a match starts.
- No human means no bot-only queue. Per-arena permission, feature access and
  server-wide safety caps remain enforced. Prepared defaults allow 31 bots per
  match and 64 total; existing installations retain their configured caps.
  MinigamesTest now permits up to **15 bots per match / 24 total**, while retaining the half-capacity fill rule.
- Citizens skin refresh can replace the Bukkit player entity. The merged candidate
  follows the replacement instead of ending the game; its precise pathfinder
  can reach single safe tiles without teleporting.
- PulseNetwork is a separate helper with Paper, Velocity and NeoForge artifacts.
  Public chat is configured globally by default on the three active servers; `/g` toggles it and `/localchat` restores local chat. Local chat
  plugins retain their own events and formatting. Optional local maths/scramble
  games defer to existing chat-game providers.
- Waiting queues advertise a Java **Join** action; Bedrock gets a native
  `/queues` form and `/queues chat` fallback. Exact-instance joins retain normal
  party/capacity/preview checks; expired links cannot create another match.
- Global chat is a distinct channel: synchronising third-party mute/ignore lists
  and custom private-channel rules needs compatibility acceptance. The helper is installed; 0.1.2 is installed. Third-party mute/ignore compatibility has focused tests but still needs player acceptance.
- Authorised resource increase is applied: MinigamesTest **4 CPUs / 5 GiB RAM**.
  Restart permission was supplied for this rollout; future unrelated restarts still require approval.
- Implementation and acceptance checklist: [PulseNetwork guide](network-helper/README.md).

## Per-game readiness

“Two humans” below describes catalogue requirements before arena limits. Where
additional players are required, suitable bots or a deliberate test mode are still
needed. Do not bypass team/role requirements and call that a completed test.

| Game | Maps / player testing | Current bots | Two humans | Next work |
|---|---|---|---|---|
| **Duels** | **3 user-built maps: Courtyard, Ruined Temple, Foundry. Aurora reports gameplay works.** Initial trial arena also retained. | Regular/Sumo; Crystal/Mace pending | Yes | Deployed: combat-style selector, individual kits and Crystal/Mace. Balance kits; test clients, team sizes and opted-in maps |
| KitPvP | Trial arena; standalone kit picker prepared | FFA only | Yes | Test default/owned kit selection and respawn; kit balance; OITC bot |
| KOTH | Trial arena available; full acceptance not recorded | Solo and teams | Solo yes; teams currently needs 8 total | Fix test arena capacity for teams; hill contesting and team balance |
| Dodgeball | Court test arena; Aurora reports cooldown too slow and hits unclear | Solo and teams | Yes | Test deployed cooldown/feedback; dodge and lead targets; difficulty |
| Dropper | Two-drops test arena | None | Race yes; practice is solo | Test landing/checkpoint/reset behaviour; controlled-fall bots |
| Prop Hunt | Five approved maps; per-map palettes and 27 supported props deployed in 0.3.14. New props need client visual acceptance. | Classic/Infection role bots deployed; shared map palette | Both modes minimum 2 | Verify disguises, lock/movement, decoys and complete rounds on Java/Bedrock. |
| Spleef | No accepted map/test recorded | Deployed: Classic, Decay, Splegg | Yes | Maps; full rounds, floor loss, projectile impacts and balance |
| TNT Run | No accepted map/test recorded | Deployed: Classic | Yes | Full rounds, corners and layers; Double Jump remains human-only |
| Block Party | No accepted map/player test recorded | Deployed Classic/Hardcore | Yes | Build maps; full rounds, elimination, difficulty and two-client tests |
| Avalanche | No accepted map/test recorded | Deployed: Classic | Yes | Maps; full falling-block rounds and survival balance |
| Volcano | No accepted map/test recorded | Deployed: Classic, Eruption | Yes | Full lava/rock rounds; map-specific climbing routes |
| Parkour | No accepted map/test recorded | None | Sprint yes; elimination needs 3 | Builder-authored routes, real jumps and checkpoints |
| Elytra Racing | No accepted map/test recorded | None | Yes | Flight routes, checkpoint passage, rockets and recovery |
| PulseKarts | 0.3.19 controller/model deployed; 0.3.20 styles, bumps and handling prepared | Candidate Sprint/Cup route-driving bots; not deployed | Yes | Review routes; test style choice, rear bumps/immunity, custom Cup laps, recovery/items and companion visual fixes on both clients |
| Skywars | No accepted map/test recorded | Candidate: loot/equip, bridges, height steps, melee/bows | Solo/insane yes; doubles needs 4 | Chest, stairs, bridge, ranged combat and team acceptance |
| Bedwars | No accepted map/test recorded | Candidate: resources, purchases, defence and bed objectives | Duos needs 4; quads needs 8 | Resources, shops, bed defence/destruction, bridging and respawns |
| Build Battle | No accepted map/test recorded | None | Yes | Theme-aware building in plots, phases and fair voting bots |
| Death Run | No accepted map/test recorded | None | Classic needs 3; double trouble 4 | Runner routes, Death roles and trap timing |
| Villager Defense | No accepted map/player test recorded | Deployed Easy/Hard | Yes | Full multi-wave survival, Mayor defence, co-op and two-client tests |
| ReadyPlayerZ | No accepted map/player test recorded | Deployed Classic/Frenzy | Yes | Full matches, kiting/balance, power-ups and two-client tests |
| Lucky Pillars | No accepted map/test recorded | None | Yes | Item decisions, bridging and safe combat |
| Supermarket Sweep | No accepted map/test recorded | None | Yes | Shopping-list decisions, real chest/inventory actions and checkout |
| Party Games | No accepted map/test recorded | None | Yes | Controller switching between colour matching, sumo and sprint |
| Quake | Deployed 0.3.14; no arena configured yet | Weapon choice, visible-target combat, abilities, adaptive difficulty; live service ready | Solo and team variants; needs built maps | Build four or more spawns, cover and walkable routes; test navigation, hit feedback and balance on Java/Bedrock. |

## Verified server arena names

Read directly from MinigamesTest's saved definitions on 11 October 2026. All nine
referenced templates contain region files. Names below preserve exact spelling
and capitalisation; no maps were renamed by this documentation check.

| Game | Display name | Arena ID / template | Definition file |
|---|---|---|---|
| Duels | `courtyard` | `courtyard` | `duels-courtyard.yml` |
| Duels | `ruined_temple` | `ruined_temple` | `duels-ruined_temple.yml` |
| Duels | `foundry` | `foundry` | `duels-foundry.yml` |
| Duels | `Duels Test Arena` | `trial-duels` | `duels-trial-duels.yml` |
| KitPvP | `Kitpvp Test Arena` | `trial-kitpvp` | `kitpvp-trial-kitpvp.yml` |
| KOTH | `Koth Test Arena` | `trial-koth` | `koth-trial-koth.yml` |
| Dodgeball | `Dodgeball Court` | `court` | `dodgeball-court.yml` |
| Dropper | `Two Drops` | `two-drops` | `dropper-two-drops.yml` |
| Prop Hunt | Five commissioned maps approved and registered. Aurora reports Bedrock works nicely (13 October); map/mode/player count not recorded. Compact movement, self-preview and role powers deployed on MinigamesTest (PR9 + PR29, 13 October). | `trial-prophunt` | `prophunt-trial-prophunt.yml` | Test one-block gaps, stable lock beside walls, independent decoys and powers on both clients; fair seeker/hider bots remain future work.

The three new Duels definitions all enable `classic` and `op`, minimum/maximum 2,
two spawns, a waiting lobby and spectator point, `bots-enabled: true`, and
`void-y: 74`. This validates the saved setup fields and template presence; further
kit/mode tuning remains a gameplay task.

## Duels map and mode register

Aurora's report on 11 October: three maps are made and Duels works; kits/loadouts
need adjustments. The server logged saves for all three arenas. This confirms
creation, not that every mode has been accepted on every map/client.

| Arena ID | Build world | State | Follow-up |
|---|---|---|---|
| `courtyard` | `rc_build_duels_courtyard` | Built and saved | Kit/loadout tuning; record Classic/OP results per client |
| `ruined_temple` | `rc_build_duels_ruined_temple` | Built and saved | Kit/loadout tuning; record Classic/OP results per client |
| `foundry` | `rc_build_duels_foundry` | Built and saved | Kit/loadout tuning; record Classic/OP results per client |

Additional modes **prepared in merged PR6, not deployed or accepted on these maps**:

- Mace PvP: define loadout, arena verticality, wind charges, fall damage and combat
  timing explicitly. Do not blindly apply legacy sword rules to a mace mode.
- Crystal PvP: define crystals, obsidian placement/breaking, explosions, protection,
  offhand/totems and respawn cleanup; needs its own map and bot acceptance.
- Other modes: record the requested rules before implementing or labelling ready.

## Bot programme

1. Harden the existing four: difficulty levels, kit use, positioning, fairness and
   cleanup. Add a controller interface and shared participant lookup.
2. Co-op: Villager Defense and ReadyPlayerZ; add KitPvP OITC ranged combat.
3. Ground hazards: Block Party, Avalanche, Spleef, TNT Run; reuse controllers in
   Party Games where the actual rules match.
4. Role/interaction games: Prop Hunt and Supermarket Sweep.
5. Route games: Parkour, Volcano, Death Run, Dropper, then specialised Elytra and
   boat steering for Karts.
6. Resource/build combat: Skywars, Lucky Pillars, Bedwars.
7. Build Battle: separate theme/build/voting behaviour, not a combat controller.

Shared blockers found in the source audit:

- Several games resolve participants via Bukkit's online-player lookup. Citizens
  NPCs are deliberately absent from that list. Use `GameInstance.player(...)` for
  game participants, while keeping pack readiness tied to real client sessions.
- Current bot actions lack reusable jump, block, inventory/shop, flight and vehicle
  control. Each game must drive its normal rules; no invisible scoring shortcuts.
- Current co-op targeting cannot reuse an enemy-player-only combat controller.
- The test server's target of 2 players and maximum 3 bots per match cannot fill
  modes needing 8 participants. Capacity, target and feature settings need an
  explicit reviewed configuration change, not just a higher bot count.
- Prop Hunt bots must not search using hidden hider coordinates. Define perception
  and memory limits, hiding choices and lock/unlock behaviour.

## Map setup and acceptance template

Build in a permanent world, never a disposable `pulse_...` match clone. Follow
[docs/MAP-SETUP.md](docs/MAP-SETUP.md) for the game's exact points and regions.
Current commands: `/pulse setup start <game> <arenaId>`, `lobby`, `spectator`,
`addspawn`, selection `wand` and `region`, settings, `check`, `save`.
The prepared Setup Book will expose these actions in an inventory menu.

For each new map or mode, append:

- Date / tester / game / mode / arena ID / build world:
- Server/plugin versions and Java/Bedrock versions:
- Humans and bots (counts, teams/roles, bot difficulty):
- Required points/regions/settings validated:
- Join → start → objectives → win/lose → return to lobby:
- Death/respawn, disconnect, late join and cleanup:
- Crossplay items/entities, resource pack and menu interaction:
- Findings, reproduction steps, screenshots/log references:
- Fix PR/commit, deployment evidence and retest result:
- Status and next action:

## Dated log

### 11 October 2026

- Aurora reported the Duels bot works well and requested difficulty levels.
- Three Duels maps were created/saved: Courtyard, Ruined Temple and Foundry.
  Server saves observed at 17:16, 17:19 and 17:20 UTC; nine arenas loaded afterwards.
  Aurora reports Duels works; kit/loadout tuning and possible Mace/Crystal modes
  remain requested follow-ups.
- Arena setup menu design approved. Setup Book implementation passed a local
  Paper 26.2 rendering/action probe; not installed on MinigamesTest.
- Aurora reported Dodgeball throw cooldown and hit clarity problems. Prepared
  pacing and feedback changes passed focused gameplay tests; no player retest yet.
- Prop Hunt failed at 17:24:55 UTC when `/pulse start` was used with two players.
  Root exception: `Minigame visuals cannot contain model hitboxes`. PulseContent's
  validator confused ordinary visual bounds with collision bones. Fix prepared;
  live server still has the failing version. This was not caused by player count.
- All-game source audit: 4/23 games currently have bot controllers. Universal bot
  readiness is a development programme, not an existing capability.
- No live server changes or restarts were made while preparing these updates.

- Aurora has moved on to building Prop Hunt maps. New map names are not yet recorded; verify saved arena definitions before adding them. Do not interrupt building with an unapproved restart.

- Aurora specified five Prop Hunt maps, followed by her visual verification of all five before arena setup. Added an explicit blocked setup gate and per-map review register.


### 11 October 2026 - bots, team modes and resource review

- Prepared Easy/Normal/Hard bot difficulty without damage, health or reach boosts.
  Existing combat controllers recognise their new team-size variants.
- Prepared Villager Defense bot targeting of tracked wave zombies, return to the
  Mayor and selection of earned sword upgrades. Isolated Paper/Citizens probe:
  a normal-health zombie was killed through the normal Player.attack path and the
  one-enemy test wave completed in four seconds. This was not a full multi-wave or
  human crossplay acceptance test. Initial probe did not tick its remote test
  chunks; repeating with test chunks force-loaded passed.
- 72 focused tests passed covering bots, team/map capacity, Duels team outcomes,
  damage, setup UI, queueing, Dodgeball and combat scoping. Jar build passed.
- MinigamesTest resource review: current limits 2 CPUs / 3072 MiB container,
  2304 MiB Java heap; about 2939 MiB container usage. Host has 16 CPUs and about
  7 GiB available RAM. Recommended 4 CPUs / 5120 MiB container, automatically
  deriving a 4096 MiB Java heap. CPU throttling and memory-limit pressure observed;
  no OOM kills. Awaiting explicit one-restart approval; no resource changes made.
- All new code remains prepared, not deployed. No live map edits or server restart.

- Map-builder update: recorded exact permanent world names. Smuggler's Wharf
  built to the larger/denser brief; Market Day and Crooked Library expanded and
  visually/block checked. Harvest Festival expansion remains next; Backstage
  Mayhem remains planned. All Aurora approvals, arena setup and player tests
  remain pending. Preserved the initial-size feedback and all-five review gate.
- Aurora requested that this shared tracker be kept current and pushed as map
  progress/testing changes. Update the canonical Markdown and regenerate both
  report exports; keep construction checks separate from player acceptance.

- Harvest Festival expansion completed: enlarged grounds, richer indoor storage, orchard/crops/hay maze and clear shed approaches. Builder images and all 23,854 block states checked; Aurora review, registration and gameplay tests remain pending. All three earlier-map revisions are now complete; Backstage Mayhem remains next.

### 11 October — merged baseline, next bots and network helper

- PulseGames PR4 merged (`0eda3bd`), after combining current map-agent tracker
  progress and regenerating its PDF. No deployment/restart followed the merge.
- Prepared PulseGames 0.3.4: half-capacity/full-small-lobby bot filling,
  ReadyPlayerZ and Block Party controllers, exact network queue admission,
  Citizens entity-refresh handling and precise navigation.
- Isolated Paper 26.2/Citizens probe: normal-health zombie killed through regular
  attacks; paid healing and iron sword checked; kill points and offline-NPC
  round revival checked. Block Party bot walked four blocks onto a single
  publicly called red tile, with no teleport. These are focused probes, not
  complete games or human crossplay acceptance.
- PulseNetwork 0.1.0 prepared for Paper, Velocity and the confirmed Create
  NeoForge 21.1.248 backend. Authentication/replay, queue expiry/admission,
  cancelled/private chat, fallback coexistence and loopback transport tests
  passed. Installed AdvancedChat/Essentials/ChatGames integration and Java/Bedrock
  end-to-end checks remain outstanding.
- Preserve all-five Prop Hunt visual approval gate and existing map/lobby edits.
- Final candidate checks: 46 targeted PulseGames tests and 17 helper tests passed.
  Paper 26.2 loaded PulseNetwork and both bot probes passed with PulseGames
  0.3.4. A separate localhost-only NeoForge 21.1.248 server loaded the enabled
  adapter and reached ready state, then was shut down. No live server was restarted.
- [PR5](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/5) merged as `f511551`; built artifacts staged outside active server folders. No install or restart.


## 11 October follow-up: staging and hazard bots

- PulseNetwork 0.1.0 Paper, Velocity and NeoForge jars are staged on readyapp-vps at
  `/srv/ready-gamed/staging/pulsenetwork-20261011-f511551`, with per-server configurations,
  checksums and installation notes. Ten targets belong to the primary proxy network;
  the direct Create proxy and compatibility clone are disabled spares.
- Existing-plugin chat relay remains held on Survival, Creative, SurvivalTest, SkyBlock
  and legacy Minigames until channel/privacy compatibility checks pass. MinigamesTest
  is the prepared queue publisher. SurvivalTest is not a minigames test environment.
- All 12 staged jar checks and five existing active Pulse jar checks passed. No active
  jars changed; no live server was restarted. A later installation/restart needs Aurora's approval.
- PulseGames 0.3.5 candidate adds Avalanche Classic and Spleef Classic/Decay/Splegg bots.
  They use bounded ground navigation; Avalanche reacts only to visible falling blocks.
  Spleef digs through normal block protection and snowball rewards, uses earned ammunition,
  and shares the player Splegg launcher/cooldown. Difficulty controls action timing and accuracy.
- Validation: 29 focused tests passed, including queue filling in all four new modes,
  route boundaries/gaps, offhand launcher compatibility and NPC block protection.
  Local Paper 26.2/Citizens checked actual digging, earned snowballs, out-of-floor protection
  and visible avalanche evasion. These are focused checks, not full-match or crossplay acceptance.
- Remaining: play full rounds on accepted maps, check Splegg projectile impacts and Decay
  floor loss, then tune reaction times. The 0.3.5 candidate is separate from the merged/staged 0.3.4 bundle.


### Next test batch: survival bots and kit selection

- [Merged PR6](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/6)
  now also prepares TNT Run Classic and Volcano Classic/Eruption. TNT uses short,
  continuously checked straight corridors under the normal eight-tick crumble timer.
  Volcano climbs reachable steps; navigation handles stair edges without teleporting.
- KitPvP has a standalone picker: `/pulsekits` (alias `/kits`) and buttons in the game
  menu. Select the map default or an owned Tank/Archer unlock. Locked or missing kits
  are refused. Selection takes effect at the next spawn, never refills mid-fight,
  and does not change One in the Chamber's fixed loadout. Menu item dragging is blocked.
- Validation: 42 focused tests passed. Local Paper/Citizens checks verified kit UI,
  ownership, next-respawn application and default selection; a two-block Volcano climb;
  and eight seconds of TNT running with 27 crumbled tiles, including a turn at the edge.
  This is not full-match or human Java/Bedrock acceptance. TNT Double Jump remains human-only.
- Ready for an approved test rollout, not deployed. Test accepted maps via normal queues,
  then kit selection/death/respawn on both clients. The main tracker and
  [queue-bot checklist](docs/QUEUE-BOTS.md) remain the agent-maintained acceptance record.
- Duels combat profiles and loadouts are now prepared in the following section.
  More KitPvP loadouts and shop expansion remain future work.
  Floor Is Lava is not a separate registered game; Volcano is the rising-lava mode.


## Duels selector and personal loadouts — preparation record

- PulseGames 0.3.6 / merged PR6: `/duels` or the game menu opens combat style → discipline
  → team size, with a separate **Your kit** button. Each player chooses their own compatible
  kit; kit choices share a queue. Classic and Modern rules remain separate queues.
- Classic (1.8-style): Regular PvP and Sumo. Modern (1.9+): Regular PvP, Sumo, Crystal
  and Mace. Duels supports 1v1, 2v2, 3v3 and 4v4, subject to map capacity.
- Personal kit changes apply next round, without an instant refill. Tank/Archer still
  need their existing unlock. Initial Crystal/Mace kits are supplied for balancing.
- Existing map permissions carry over for Regular/Sumo; the setup menu can make profile
  permissions explicit. Crystal needs a combat region and `crystal-enabled`; Mace needs
  `mace-enabled`. No existing map has been enrolled into these disciplines by this work.
- Validation: 83 focused tests passed. Local Paper/Citizens verified distinct round kits,
  Classic/Modern attack-speed switching, Crystal placement/protection/round cleanup,
  native held-totem activation under lethal damage and a wind-charge jump. Full matches, human crossplay, kit balance and Crystal/Mace
  bot controllers remain pending. Prepared controller coverage stays 11/23; live stays 4/23.
- Test next: different kits on Java/Bedrock; next-round changes; team damage; Crystal
  totems/offhand/chain blasts and resets; Mace smash/fall damage. Record results per map.
  See [Duels setup and acceptance guide](docs/DUELS.md). No live restart or installation.

- Backstage Mayhem built in its permanent world with ten furnished rooms and three stage sets. Exact verification: 44,608/44,608 states; actual room/stair/catwalk/facade images inspected. Final review found roof access through the rear catwalk opening; one bounded correction pass requested after the workflow limit of three passes. Map is not marked finished, approved or registered.

- Original map revision brief: the initial maps were too small and lacked hiding clutter. Aurora requested the larger, denser Wharf, revisions of all three earlier builds, then Backstage Mayhem.


## Restart preparation — historical plan

- PR6 merged as `436fd69`; the tested PulseGames 0.3.6 jar and pinned combat provider
  are staged privately. No live installation, config replacement or restart performed.
- PulseContent PR28 remains a separate draft and is a hold for the Prop Hunt content
  fix. Twelve staged network-helper jars rechecked; chat compatibility holds remain.
- Current MinigamesTest still runs PulseGames 0.3.2 / PulseContent 0.3.1, with a
  three-bot per-match cap and six-bot global cap. Review resource allocation and caps
  before expecting half-filled larger queues. Classic combat also needs its staged provider.
- The restart runbook covers fresh backups after graceful save, targeted configuration
  changes, backend/proxy order, rollback and Java/Bedrock acceptance. Aurora must approve
  the restart window. All five Prop Hunt maps still require her visual approval before setup.

- Backstage Mayhem final correction completed at Aurora’s request: framed/glazed rear catwalk wall closes roof access; castle lamps have full stone supports. Actual close-up images inspected and all 44,855 states match, with zero missing/wrong/unloaded states. All five commissioned maps are now built and awaiting Aurora visual approval; none is registered or gameplay-tested. Review starts with Market Day, then Crooked Library, Harvest Festival, Smuggler’s Wharf and Backstage Mayhem. Server updates/restarts remain with Aurora’s other agent after review/setup.

- Aurora approved Market Day for setup and specified reachable upper hiding areas across all five maps, plus barrier containment to prevent escaping. Market Day now has eight ladder lofts and two mezzanines with complete barrier enclosure (47,794 checked states). Crooked Library already has reachable furnished upper levels; containment and roof preservation checked (49,062 states). Harvest Festival additions require fresh verification in the farm world after the player returned to the lobby during the build. Wharf and Backstage upper-area/containment revisions remain next. No arena registration or restart performed.

- Prop Hunt setup update: Aurora requested saving the approved map and proceeding to the next. Farm verification was repeated in the correct world (42,945 states), resolving the earlier lobby mismatch. Registered Harvest Festival and the previously approved Market Day; checklists passed and `/pulse arenas` lists both saved IDs/templates. Settings: classic/infection, configured 2-16, seven spawns, bots disabled, void-y74. Wharf (67,514 states) and Backstage (66,024 states) upper additions/containment are now checked. Player is in Smuggler’s Wharf for review; Crooked Library and Backstage approvals remain pending. No match, restart or deployment performed.

- Aurora approved Smuggler’s Wharf; registered `smugglers_wharf` with Classic/Infection, configured 2-16, seven spawns, bots disabled and void-y74. Checklist passed; live-save message and `/pulse arenas` confirm registration/template loading. Corrected the earlier Market Day/Harvest Festival negative-coordinate spawn and spectator points: old cells intersected walls; new cells have solid floors and clear air. Re-saved both with every setting preserved. Backstage Mayhem is now loaded for review; Crooked Library still needs approval. Gameplay/crossplay tests and restarts remain pending.

- Aurora approved Backstage Mayhem subject to audience seating correction: all 80 wooden seats now look toward the stage with red wool behind; actual interior images inspected and all 66,024 states match. Registered `backstage_mayhem`; seven clear-floor spawns, enclosed waiting/spectator points, Classic/Infection, configured 2-16, bots disabled and void-y74. Checklist/live-save and `/pulse arenas` confirm template loading. Crooked Library is loaded for the final review. Played rounds, crossplay acceptance and server restart remain pending.

- Crooked Library review revision: Aurora requested more random clutter in the bottom area. Added 18 varied groups of book sorting piles, barrel cabinets, repair desks, lecterns and supplies (180 blocks), preserving existing shelves and stair approaches. Actual archive images reviewed; all 49,242 states match. Map remains in Aurora review and is not registered.

- Aurora approved Crooked Library after the archive clutter revision. Registered `crooked_library` with Classic/Infection, configured 2-16, seven clear-floor spawns across all three levels, bots disabled and void-y67 below the archive floor at y73. Waiting/spectator points are in the enclosed upper galleries. Checklist passed; live-save and `/pulse arenas` confirm all five commissioned Prop Hunt arena/template IDs loaded. All visual reviews and registrations are complete; played rounds, Java/Bedrock acceptance and server updates/restarts remain pending with the other agent.


## 12 October — map approval and network rollout

Aurora has explicitly approved **all five Prop Hunt maps**. The prior all-five visual
review gate is satisfied. Saved definitions independently confirm Market Day, Crooked
Library, Harvest Festival, Smuggler's Wharf and Backstage Mayhem. Gameplay and
Java/Bedrock acceptance remain outstanding.

Authorised releases: PulseGames 0.3.7, PulseContent Paper 0.3.2 and
PulseNetwork 0.1.2 for global-by-default public chat and queue return trips. Active
network scope is Lobby, SurvivalTest and MinigamesTest plus the primary proxy. Source Paper helpers preserve departure
coordinates; leaving a queue or match cleanup returns network visitors to their origin.
All three backends restarted and reached ready state with PulseNetwork 0.1.2. MinigamesTest loaded 14 arenas with PulseGames 0.3.7 and PulseContent 0.3.2; Classic PvP provider is ready. Primary proxy and Geyser also reached ready state; proxy loaded PulseNetwork 0.1.2 and the three-server allowlists were verified. Receipt: `output/pulsegames/2026-10-12/rollout/verification.txt` in the Ready workspace.

- Global public chat defaults on for Lobby, SurvivalTest and MinigamesTest. Old Creative, Survival, SkyBlock and Minigames are excluded; Sessions/Create are excluded from the final helper scope too.
- Java queue Join links and Bedrock `/queues` forms are deployed. Return trips restore the source server/location after queue leave or match cleanup; full human end-to-end acceptance is still pending. Disconnects/proxy restarts do not preserve an in-flight return session.
- Checks: 26 focused helper tests, 14 queue/service tests and five content visual-runtime tests passed across the release work. These do not replace player testing.
- CoBuilder 0.4.1 replaces the faulty 0.4.0 client mod locally. Fully relaunch Minecraft to load the null-safe server-message fix.
- New helper metadata credits AurorasChaos (on behalf of Player Ready); the same policy is recorded for future Ready-owned plugin releases.
- Backups on readyapp-vps: `/srv/ready-gamed/backups/minigames-rollout-20261012` and `/srv/ready-gamed/backups/global-chat-20261012`.
- Existing unrelated startup issues remain: Lobby hologram compatibility and ProtocolLib/Paper reflection warning; SurvivalTest wizard NPC script errors. The network helper itself enabled successfully.
- PR7 notification failed HTTP404. PR8 notification was refused because the PR had already merged; no duplicate or misleading notification was sent.

- Lobby additions completed and checked (8,701 states); Aurora commissioned twelve new maps: three each for Kit PvP, KOTH, Dodgeball and Dropper. New batch build/review/registration progress is tracked above; no new arenas registered without approval.

- Twelve-map construction batch completed: three each for Kit PvP, KOTH, Dodgeball and Dropper. Actual section images and full plan states checked; spawn clearance/boundaries checked, Dropper water targets/apertures checked. Sunken Citadel centre stair gap corrected and rechecked. Player returned to Sunken Citadel for Aurora review. No new batch arenas registered or played rounds claimed.

## 13 October - Prop Hunt feedback and compact-prop candidate

Aurora reports that the currently deployed Prop Hunt works nicely on Bedrock. The
map, mode, player count and round outcome were not recorded, so this is not blanket
acceptance of all five maps. Reported improvements: see the disguise continuously
at the hider's feet, remain invisible while moving, fit in low gaps, stop locking
from immediately undoing near neighbouring blocks, and add independently disguised
decoys plus powers for both roles.

**Deployed to MinigamesTest, 13 October:** [PulseGames PR9](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/9)
and [PulseContent PR29](https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/29) merged.
PulseGames 0.3.8 and PulseContent Paper 0.3.3 installed together after the approved
restart and full saved-world backup. Startup completed at 09:54 UTC with 26 arenas,
10 kits and 23 games. All 57 PulseGames YAML files were unchanged; installed jar
hashes match the tested bundle. Human Java/Bedrock acceptance remains pending.
A separate PulseNetwork saved-location load error (`unknown world`) was observed;
world lifecycle and deferred location restoration need investigation.

- Moving/locked hiders use a full-size prop for other participants and a 40% local
  foot preview for themselves, keeping the reduced first-person camera outside it.
- Actual player scale is 0.5 for full props (0.9-block body) and 0.25 for heads
  (0.45-block body). Seeker hit shapes still match the full chosen prop, not the
  tiny player. Original scale/visibility/collision are restored on role/round exit.
- Grid locks retain floor height, do not place a solid map block and debounce paired
  use packets. Invalid snapping preserves the moving disguise and current location.
  Full props still require a full footprint; heads fit smaller spaces. Irregular
  block bounding boxes are deliberately conservative.
- Hiders: three independently disguised decoys (60-second lifetime, 12-second
  placement cooldown), clear-scene control and four-second escape sprint.
- Seekers: proximity pulse (12-block radius) and four-second search sprint. Sprint
  and pulse default to 25-second cooldowns. Decoys can mislead the pulse and break
  when hit. Powers are unavailable to seekers before release and to inactive/frozen
  players. Bounded numeric settings appear in the existing Setup Book checklist;
  `prop-powers: false` disables powers separately from the disguise fixes.
- Validation: 42 focused game/bridge/setup tests, 20 content lifecycle tests and
  13 disposable Paper 26.2/BetterModel 3.5.0 runtime checks passed. The first
  same-tick dimension check saw stale collision bounds; after server ticks, the
  measured heights were 0.90 and 0.45 with scale restored correctly. The temporary
  scale modifier was also verified absent from persisted player attributes. Human camera,
  moving-prop rendering, gap navigation and power balance still need Java/Bedrock
  acceptance. The local lab was stopped and its previous plugin jars restored.
- Prop Hunt bots remain unsupported. Decoys are props, not queue-filling bots.
- Build/evidence bundle: `output/pulsegames/2026-10-13/prop-hunt-tools` in the Ready
  workspace. Controls, settings and acceptance steps: [Prop Hunt guide](docs/PROP-HUNT.md).
- Both required PR notifications failed HTTP404. Drafts were created successfully.

## 13 October - Editing worlds and concurrent matches

**Deployed to MinigamesTest, 13 October:** [PR10](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/10) merged; PulseGames 0.3.9 installed following an approved graceful restart and full saved-server backup. Startup completed at 10:05:10 UTC with 26 arenas, 10 kits and 23 games. All 57 configuration files were unchanged and the installed jar hash matches the tested build.

- Editing worlds opened with `/pulse world`, `rc_build_*` worlds and PulseGames void-marked build worlds save and unload after 15 seconds empty. Rejoining resets the timer. Build terrain is retained; template publishing still requires Setup Book Save.
- The primary world, lobby, active matches and configured keep-loaded worlds are protected. Template capture holds its source loaded. Refused unloads retry without deleting files.
- Same-mode/same-map concurrent matches already use independent copies and game logic, up to the configured instance cap. This draft strengthens IDs and cleans late clones after cancellation.
- 22 focused lifecycle, storage, setup and instance tests passed; 0.3.9 is live. Live save/reopen and two simultaneous played matches remain acceptance checks. Any future restart requires fresh approval.
- Separate follow-up: PulseNetwork 0.1.2 saved return Locations must resolve unloaded persistent worlds safely; startup currently reports an unknown-world deserialisation error.


## 13 October - Reliability, shared SQL and the next Creative phase

**Deployed, 13 October:** PulseGames 0.3.10 adds Setup Book arena validation and bounded match diagnostics. PulseNetwork 0.1.3 fixes loading saved locations for unloaded worlds and improves short reconnect returns. Player inventory restoration is idempotent. Validation covers required map settings, spawn clearance in loaded chunks and team capacity; unloaded chunks still need a visit and physical check. Proxy restart persistence and a return-message acknowledgement remain follow-up work.

- Focused unit checks passed; a local Paper 26.2 probe passed 15/15 checks for inventory/vitals restoration, unsafe return spots, two independent instances of one map, instance cleanup, and saved editing-world unload/reopen. Human Java/Bedrock acceptance is still pending.
- MariaDB on readyapp-vps has separate LuckPerms and PulseAddon databases and private staged credentials. The approved SQL scope is **Lobby, SurvivalTest, MinigamesTest and the primary proxy only**. Older servers remain separate.
- PulseAddon 9.8.0 moves persistent data documents into an exclusive per-backend SQL namespace; it does not share world-bound claims between independent worlds. 32 focused JVM tests, four migration/export CLI tests and an isolated actual-MariaDB probe passed. The migration imported 41 documents / 300,076 bytes, with every digest verified. SQL reads and ongoing writes are active.
- Approved rollout completed: Lobby, SurvivalTest, MinigamesTest and the primary proxy are running LuckPerms 5.5.87 with shared MariaDB and SQL messaging, plus PulseNetwork 0.1.3. SurvivalTest runs PulseAddon 9.8.0; MinigamesTest runs PulseGames 0.3.10. Permission exports match exactly (three groups, three users, no tracks), and all 57 PulseGames YAML files are unchanged. Full saved-server and SQL backups are under `/srv/ready-gamed/backups/shared-sql-rollout-20261013`. Human gameplay acceptance remains pending; future restarts require fresh approval.

**Cosmetics proposal:** [wide-ranging catalogue and progression](COSMETICS-ROADMAP.html), with 11 reward families across eight style collections. PulseContent should own catalogue and presentation; shared SQL should hold player entitlements and idempotent event awards. Gameplay kits remain separate. Existing unlocks must be preserved. These are proposed features, not implemented rewards.

**Next after current work:** [Creative competitions and map-art studio](CREATIVE-ROADMAP.html). Players nominate their builds; staff judge against a rubric; approved results award trophies. A separate protected plot world offers palettes, brushes, shapes, fill, selections, eyedropper, history, shading and later depth tools. Aurora confirmed **no image imports: hand-built artwork only**. Submitted renders receive AI screening followed by human approval before publication or Survival redemption. No moderation provider or external image upload is enabled. Audit the existing Creative world and preview its menus before implementation.

Merged and deployed: [PulseGames PR11](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/11) and [PulseAddon PR30](https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/30). Installed artifact hashes match the tested releases. Lobby retains its pre-existing DecentHolograms compatibility failure; a later proxy connection timeout was observed, so this rollout does not establish that every client disconnect is resolved.

**Merged, not deployed:** [PulseContent wardrobe foundation PR31](https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/31) provides SQL-backed ownership, event-award receipts and equipment slots, with eleven focused tests passing. Runtime connection wiring, catalogue loading, menus, renderers and shop purchases are still to implement. The reserved wardrobe setting defaults off; no cosmetics database is activated. Next work is the catalogue and a read-only Java/Bedrock wardrobe preview, followed by controlled integration. The required PR notification failed HTTP404.

## 13 October - Wardrobe experience and local cosmetic rules

**Prepared, not deployed:** [PulseAddon PR32](https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/32) and [PulseGames PR12](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/12). PulseContent Paper 0.4.0 and PulseGames 0.3.11 jars are prepared locally. No wardrobe database provisioned or enabled, and no server restarted.

- Catalogue: 25 entries across custom hats, trails, weapon effects, finish effects and win animations. Twenty use native particles; five hats are planned and unavailable until a non-hiding attachment renderer and cross-edition assets exist.
- Aurora chose Survival hats, subtle trails and restrained weapon effects. Defaults allow the hat category, Cherry Drift/Gentle Glow and Spark Strike/Arcane Strike; finish/victory effects remain off. Rules can select server, world, game, phase, category and individual cosmetic. Ownership and equipped selections survive suppression.
- Protected inventory wardrobe offers collection browsing, private previews and ownership-checked equip/unequip. Both clients use the normal inventory path; a Bedrock form in the HTML is a design concept only. Admin grants use stable receipts; no automatic progression or purchases yet.
- Match integration supplies explicit contexts and completion/winner triggers. Finish means match completion here. Default competitive effects are limited to Duels/KitPvP; Prop Hunt/editing suppress cosmetics. Rendering respects invisible/spectator players, viewer visibility, distance and particle budgets.
- Validation: 24 core, 11 Paper and 15 PulseGames focused tests passed. Schema constraints/collations and shutdown safeguards checked locally; actual wardrobe MariaDB execution and mixed-client acceptance remain before enabling. Lobby's older Paper needs a separate compatibility plan before this 26.2-targeted jar.
- Candidate and preview: `output/pulsegames/2026-10-13/wardrobe` in the Ready workspace. Both required PR notifications failed HTTP404.

Further collection ideas: shoulder companions, back accessories, animated hats, fishing bobbers/catch effects, social emotes, victory scenes, personal-best celebrations, dated event keepsakes and lobby toys. These are proposals; each needs context rules and appropriate Java/Bedrock assets.

## 13 October - Accessory prototypes and expanded collections

**Draft updated, not deployed:** [PulseAddon PR32](https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/32), commit `38bec52`, now builds PulseContent Paper 0.5.0. [PulseGames PR12](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/12) remains the paired 0.3.11 integration. No merges, live resource-pack changes, SQL provisioning or restarts in this increment.

- 45 entries across ten categories. 27 have implementations: 20 original particle effects, three fishing-catch effects, a native arm greeting and three original accessory prototypes. The other 18 are explicitly planned.
- Starlight Crown, Clockwork Companion and Explorer's Pack use separate non-colliding anchors, preserving the player body and real armour. Original sources compile for Java/Bedrock; they remain behind an off-by-default attachment trial and model allowlist. Actual client positioning, clipping and cleanup need acceptance.
- Category browsing, private model previews and visit-only all/reduced/hidden controls are implemented. Reduced hides others' model accessories and quarters particles; hidden suppresses both. Native arm movements are ordinary player animations and are not filtered. Preferences reset on backend change/reconnect.
- Survival retains the chosen hats, two subtle trails and two restrained weapon effects, plus three harmless catch effects. New shoulder/back accessories and social emotes are enabled by policy only in lobby contexts. Finish/victory effects remain off in Survival.
- Validation: 27 core and 23 Paper focused tests passed. The paired game's earlier 15 tests remain unchanged. Real MariaDB 10.11.19 acceptance passed 19 assertions, including concurrent reward replay, equip fencing, rollback, schema/collation rejection and reopened state. Disposable test resources were removed; no live database touched.
- Review `output/pulsegames/2026-10-13/wardrobe/accessories.html` for rotatable source geometry and the adjacent ZIP for asset sources. Full-body emotes, custom fishing bobbers, richer companion/back models, event keepsake displays and automatic progression remain future work.

Next acceptance: approved isolated installation; grant/equip the three prototypes; verify both editions, armour preservation, sneak/swim/glide/vehicle suppression, invisible/spectator states, world/queue cleanup, viewer controls and private previews. Do not activate from this tracker alone.

**Proposed Survival collection expansion:** Aurora suggested pet effects, fishing, mining chances and farming. Draft PR32 now records paw-print trails/pet reactions, bobber/catch flourishes, successful-break mining particles and planting/mature-harvest effects. These are plans only; Aurora confirmed mining chances mean occasional visual effects only, with no gameplay bonuses. Cosmetic ownership must not change loot, XP, growth or pet statistics. See `plugins/pulse-content/docs/survival-cosmetic-effects.md` in PulseAddon.

### Survival activity cosmetics — local candidate, 13 October

Nine new choices are implemented locally: three pet, three mining and three farming effects. Catalogue total: 54 entries, 13 categories, 36 implemented and 18 planned. Successful native actions and optional PulseAddon pet/custom-harvest/rare-catch signals use ownership, context, visibility, chance and cooldown checks. Mining 15%, farming 30% and pet 25% defaults control particles only; no gameplay rewards change.

Validation: 32 core and 34 Paper focused tests passed; PulseContent Paper 0.5.1 and PulseAddon 9.9.0 review jars built. Not pushed or deployed: the development data drive is mounted read-only. Source patches, jars, hashes and evidence are preserved at `/home/ara/Projects/Ready/output/pulsegames/2026-10-13/activity-cosmetics/`. Apply to writable isolated branches and update draft PRs before release. Mixed Java/Bedrock acceptance remains required. No server restart or live changes.

## 13 October - Cosmetics trial deployed and ready for player testing

**Supersedes the preparation-only statuses above.** PulseAddon PR32 and PR33, and PulseGames PR12, are merged. After Aurora's explicit restart approval, SurvivalTest, MinigamesTest and the primary proxy were saved, backed up and restarted successfully. PulseContent Paper 0.5.2 is active on both test backends, PulseAddon 9.9.0 on SurvivalTest and PulseGames 0.3.11 on MinigamesTest.

- Shared wardrobe SQL is ready on both backends. Java pack downloads match the built files; matching Geyser acknowledgements and all three accessory registrations are verified. Existing arena configuration hashes are unchanged.
- The catalogue has 54 entries across 13 categories: 36 implemented and 18 planned. Owned, ready choices can be selected ahead of a match; local policy still controls previews and actual effects. No automatic progression, purchases or player grants were enabled.
- Initial Survival testing is in the `prettyworld` overworld. Its active Nether and End world names are not mapped in this trial. The separate Lobby backend remains excluded because its older Paper version is incompatible; use the MinigamesTest lobby for cross-server wardrobe checks.
- Local validation passed 474 tests (32 core, 37 Paper, 399 PulseAddon and six focused PulseGames tests). Hosted checks could not start due to account billing/spending restrictions. Human Java/Bedrock visual and gameplay acceptance is still pending.
- Full saved-server archives and SQL backup: `/srv/ready-gamed/backups/wardrobe-release-20261013`. Release evidence: `output/pulsegames/2026-10-13/wardrobe-release` in the Ready workspace. Future restarts require fresh approval.
- Start with the [testing runbook](COSMETICS-TESTING-RUNBOOK.html), also available as `COSMETICS-TESTING-RUNBOOK.pdf`. Record edition, server, world, cosmetic ID and expected/actual result for failures.

The required PR33 opening notification failed: the temporary recovery checkout was outside the helper's permitted workspace, and the retry after merging was rejected because the PR was already merged. The merge and deployment succeeded.

## 13 October - Bot readiness and next map batch verified

Read-only live verification confirms PulseGames 0.3.11 matches the released artifact. Bots are enabled, wait 15 seconds, and have caps of 15 per match / 24 server-wide. The two-account trial remains restricted; half-capacity filling (full when maximum is four or less) still applies, subject to playable minimum and caps.

The deployed allowlist covers 11 games / 21 modes: Duels Classic/OP/Sumo; KitPvP FFA; KOTH Solo/Teams; Dodgeball Solo/Teams; Villager Defense Easy/Hard; ReadyPlayerZ Classic/Frenzy; Block Party Classic/Hardcore; Avalanche Classic; Spleef Classic/Decay/Splegg; TNT Run Classic; Volcano Classic/Eruption. Controller availability is not full played acceptance.

All registered Duels, KitPvP, KOTH and Dodgeball maps permit bots. Dropper and Prop Hunt maps explicitly do not. Seven supported games have no registered maps yet: Block Party, Spleef, Avalanche, TNT Run, Volcano, Villager Defense and ReadyPlayerZ. Recommended build order: Block Party, Spleef and Avalanche first, then TNT Run/Volcano, then the two co-op games. Give Volcano connected climbable paths; do not expect bots to bridge or teleport.

Still unsupported: Prop Hunt, Dropper, Parkour, Elytra Racing, PulseKarts, Skywars, Bedwars, Build Battle, Death Run, Lucky Pillars, Supermarket Sweep and Party Games. Duels Crystal/Mace, KitPvP OITC and TNT Run Double Jump also remain human-only.

### Cosmetic feedback
Aurora reports Bedrock functional checks pass, then identifies visible attachment lag during movement/rotation. Responsiveness acceptance stays open. Models currently follow separate server-positioned anchors, while the local player moves predictively. Direct client attachment needs a prototype on both editions; mounting can improve position without proving local head rotation. No follow-latency fix has been deployed or server restarted.

### Cosmetic movement follow-up: opt-in client attachment prototype

[PulseAddon draft PR34](https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/34), commit `9715701`, prepares Paper0.5.3 and Geyser0.3.2. Java displays now have an opt-in direct player parent; a separate Geyser adapter links Bedrock model carriers as non-controlling passengers. Armour, actual passengers, wardrobe ownership, local rules and existing prop/preview paths are preserved.

49 focused tests passed (34 Paper,15 Geyser); jars and trial instructions are at `output/pulsegames/2026-10-13/cosmetic-follow` in the Ready workspace. Disabled by default, draft only, not deployed. Java yaw remains server-driven; Bedrock alignment/rotation and lifecycle need actual client acceptance. Aurora chose **keep it prepared for later**: do not merge, deploy or restart for this trial without a new go-ahead. PR opening notification failed HTTP404.
