# Minigames development and play-test tracker

Last updated: 12 October 2026. Owner: Aurora. Agents should update this file as
work and player feedback arrive; keep it in the PulseGames repository.

Readable report: [HTML](MINIGAMES-TRACKER.html) · [PDF](MINIGAMES-TRACKER.pdf).
After editing this source, regenerate both exports with
`node tools/render-minigames-tracker.mjs --pdf`; see [update instructions](tools/TRACKER-README.md).

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

## Current test-server baseline

- PulseGames **0.3.2** and PulseContent Paper **0.3.1**, last verified 11 October.
- Java and Bedrock can connect through the primary proxy. Access is no longer
  restricted by MinigamesTest's whitelist; proxy authentication remains in place.
- Lobby mob spawning disabled; 62 loaded hostile mobs removed earlier.
- New `/pulse world <name>` build worlds default to void, with a 7×7 quartz spawn
  platform. Existing worlds and edits persist. Use `normal` explicitly for terrain.
- Six initial test arenas existed. Three user-built Duels arenas were subsequently
  saved, and the server logged **9 loaded arenas** at 17:20 UTC on 11 October.
- Current bot controllers cover only **4/23 games**: Duels, KitPvP FFA, KOTH and
  Dodgeball. Global enablement, per-arena permission, capacity and feature access
  also matter. Other games are not ready simply by setting `bots-enabled: true`.

## Prepared update — not deployed

| Change | Current status | Remaining acceptance |
|---|---|---|
| Arena Setup Book and interactive checklist | Implemented; approved mockup; focused tests and local Paper menu probe passed | Real Java/Bedrock clicks, prompts and save flow |
| 1.8-style PvP | Scoped OCM candidate passed local module/attack-speed restoration check on an owned NPC | Mixed human/bot damage, knockback and blocking checks on both clients |
| Dodgeball pacing and feedback | Prepared: 0.75s → 0.35s default throw delay; visible cooldown, hit sounds/messages, elimination title | Player feel and crossplay feedback acceptance |
| Prop Hunt startup failure | Root cause confirmed; corrected validation accepted all 10 actual BetterModel props; tracker creation passed locally | Actual disguise attachment and full two-client round |
| Prop Hunt two-player start | Prepared: both modes minimum 2; reject a solo forced start | One seeker and one hider completing Classic and Infection |
| Bot difficulty and co-op controllers | Villager Defense merged in PR4; ReadyPlayerZ fighting, earned-point purchases and revival locally checked in the next candidate | Full multi-wave survival, human/bot balance and crossplay |
| Bot queue target and Block Party | Merged candidate targets half of map/mode capacity, or full capacity for maxima 1–4; Block Party public-colour tile navigation locally checked | Capacity/load tests, full colour rounds and human/bot acceptance |
| PulseNetwork helper | Paper, Velocity and Create/NeoForge adapters prepared; public chat, local fallback chat games and queue Join actions | Per-plugin moderation/privacy/ignore checks, Java click and Bedrock form acceptance; not installed |
| PvP team sizes and map capacity | Prepared solo/doubles/triples/squads for main combat games, 2v2 Duels rounds and per-mode map limits | Test each mode on suitable maps; do not enable team modes on undersized maps |
| CoBuilder command-announcement disconnect | Client mod 0.4.1 prepared separately | Install/relaunch client at a convenient time and retest announcements |
| Distant lobby Trial Chamber | Diagnosed; bounded cleanup tooling prepared | Separate permission and fresh edit-preserving checks before cleanup |

PulseGames PR4 was merged at Aurora’s request on 11 October (merge `0eda3bd`).
PulseContent PR28 remains a draft. PR5 (merge `f511551`) is now merged and its helper bundles are staged privately for all 12 Minecraft services;
none of these merge/build results means the update is deployed.

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
| dropper | Crystal Descent | `rc_build_dropper_crystal_descent` | built; awaiting Aurora review (47,515 checked states) | Awaiting approval/setup |
| dropper | Canopy Plunge | `rc_build_dropper_canopy_plunge` | built; awaiting Aurora review (46,387 checked states) | Awaiting approval/setup |
| dropper | Reactor Dive | `rc_build_dropper_reactor_dive` | built; awaiting Aurora review (47,099 checked states) | Awaiting approval/setup |

All twelve are now built and builder-checked. All three Kit PvP maps are approved and registered (FFA/OITC, 2–8 players, eight spawns each, bots enabled, void-y 74). Wildwood received ferns, grasses, flowers and flowering shrubs. Ember Spire is also approved and registered for solo/teams (2–8 players), with its operator-marked hill region. Clockwork Plaza is also approved and registered for solo/teams (2–8 players), with its operator-marked hill. Frostbound Keep is also approved and registered for solo/teams (2–8 players), completing all three KOTH registrations. Neon Circuit is registered for Dodgeball solo/teams (2–12 arena limit, twelve spawns), with an exterior neon city backdrop. Bamboo Courtyard and Orbital Hangar now have garden and spaceport backdrops, respectively, and are approved and registered for solo/teams. All nine combat maps are registered. Aurora review continues in Crystal Descent; three Dropper registrations and all gameplay acceptance remain pending.

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
- Team modes do not imply bot support. Live controllers still cover 4/23 games;
  Villager Defense, ReadyPlayerZ and Block Party are merged; the latter two passed focused local checks.
  Avalanche, Spleef, TNT Run and Volcano are prepared in draft PR6, bringing controllers to 11/23; live support remains 4/23.
- See [team and capacity instructions](TEAM-MODES.md) for exact settings and examples.

## Queue bots and network helper — merged and staged

- Requested fill target counts **humans plus bots**: maximum 2 → 2 total, 4 → 4,
  5 → 3, 8 → 4, 16 → 8. Odd halves round up. The playable minimum can raise
  that target. Humans reclaim bot seats before a match starts.
- No human means no bot-only queue. Per-arena permission, feature access and
  server-wide safety caps remain enforced. Prepared defaults allow 31 bots per
  match and 64 total; existing installations retain their configured caps.
  MinigamesTest’s smaller live caps have **not** been raised.
- Citizens skin refresh can replace the Bukkit player entity. The merged candidate
  follows the replacement instead of ending the game; its precise pathfinder
  can reach single safe tiles without teleporting.
- PulseNetwork is a separate helper with Paper, Velocity and NeoForge artifacts.
  `/g` toggles global public chat; `/localchat` restores local chat. Local chat
  plugins retain their own events and formatting. Optional local maths/scramble
  games defer to existing chat-game providers.
- Waiting queues advertise a Java **Join** action; Bedrock gets a native
  `/queues` form and `/queues chat` fallback. Exact-instance joins retain normal
  party/capacity/preview checks; expired links cannot create another match.
- Global chat is a distinct channel: synchronising third-party mute/ignore lists
  and custom private-channel rules needs compatibility acceptance. All helper
  features default off; nothing is installed on the network yet.
- Resource increase proposal remains pending separate restart approval:
  MinigamesTest 2 → 4 CPU quota, 3 → 5 GiB container RAM (4 GiB derived heap).
  No resource change or server restart was performed for this candidate.
- Implementation and acceptance checklist: [PulseNetwork guide](network-helper/README.md).

## Per-game readiness

“Two humans” below describes catalogue requirements before arena limits. Where
additional players are required, suitable bots or a deliberate test mode are still
needed. Do not bypass team/role requirements and call that a completed test.

| Game | Maps / player testing | Current bots | Two humans | Next work |
|---|---|---|---|---|
| **Duels** | **3 user-built maps: Courtyard, Ruined Temple, Foundry. Aurora reports gameplay works.** Initial trial arena also retained. | Regular/Sumo; Crystal/Mace pending | Yes | Draft: combat-style selector, individual kits and Crystal/Mace. Balance kits; test clients, team sizes and opted-in maps |
| KitPvP | Trial arena; standalone kit picker prepared | FFA only | Yes | Test default/owned kit selection and respawn; kit balance; OITC bot |
| KOTH | Trial arena available; full acceptance not recorded | Solo and teams | Solo yes; teams currently needs 8 total | Fix test arena capacity for teams; hill contesting and team balance |
| Dodgeball | Court test arena; Aurora reports cooldown too slow and hits unclear | Solo and teams | Yes | Test prepared cooldown/feedback; dodge and lead targets; difficulty |
| Dropper | Two-drops test arena | None | Race yes; practice is solo | Test landing/checkpoint/reset behaviour; controlled-fall bots |
| Prop Hunt | **All five upper-area/barrier revisions checked; All five maps approved and registered; gameplay tests pending.** Trial arena exists; startup fix pending deployment | None | Live minimum 3; prepared update lowers to 2 | Deploy validated startup fix; test palette, snapping and head props; fair seeker/hider bots |
| Spleef | No accepted map/test recorded | Prepared: Classic, Decay, Splegg | Yes | Maps; full rounds, floor loss, projectile impacts and balance |
| TNT Run | No accepted map/test recorded | Prepared: Classic | Yes | Full rounds, corners and layers; Double Jump remains human-only |
| Block Party | No accepted map/player test recorded | Prepared Classic/Hardcore | Yes | Build maps; full rounds, elimination, difficulty and two-client tests |
| Avalanche | No accepted map/test recorded | Prepared: Classic | Yes | Maps; full falling-block rounds and survival balance |
| Volcano | No accepted map/test recorded | Prepared: Classic, Eruption | Yes | Full lava/rock rounds; map-specific climbing routes |
| Parkour | No accepted map/test recorded | None | Sprint yes; elimination needs 3 | Builder-authored routes, real jumps and checkpoints |
| Elytra Racing | No accepted map/test recorded | None | Yes | Flight routes, checkpoint passage, rockets and recovery |
| PulseKarts | No accepted map/test recorded | None | Yes | Ice track, boat steering, checkpoints, laps and item use |
| Skywars | No accepted map/test recorded | None | Solo/insane yes; doubles needs 4 | Loot/equip, bridging, safe combat and team tests |
| Bedwars | No accepted map/test recorded | None | Duos needs 4; quads needs 8 | Resources, shops, bed defence/destruction, bridging and respawns |
| Build Battle | No accepted map/test recorded | None | Yes | Theme-aware building in plots, phases and fair voting bots |
| Death Run | No accepted map/test recorded | None | Classic needs 3; double trouble 4 | Runner routes, Death roles and trap timing |
| Villager Defense | No accepted map/player test recorded | Merged Easy/Hard | Yes | Full multi-wave survival, Mayor defence, co-op and two-client tests |
| ReadyPlayerZ | No accepted map/player test recorded | Prepared Classic/Frenzy | Yes | Full matches, kiting/balance, power-ups and two-client tests |
| Lucky Pillars | No accepted map/test recorded | None | Yes | Item decisions, bridging and safe combat |
| Supermarket Sweep | No accepted map/test recorded | None | Yes | Shopping-list decisions, real chest/inventory actions and checkout |
| Party Games | No accepted map/test recorded | None | Yes | Controller switching between colour matching, sumo and sprint |

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
| Prop Hunt | `Prophunt Test Arena` | `trial-prophunt` | `prophunt-trial-prophunt.yml` |

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

Additional modes **prepared in draft PR6, not deployed or accepted on these maps**:

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

- Backstage Mayhem built in its permanent world with ten furnished rooms and three stage sets. Exact verification: 44,608/44,608 states; actual room/stair/catwalk/facade images inspected. Final review found roof access through the rear catwalk opening; one bounded correction pass requested after the workflow limit of three passes. Map is not marked finished, approved or registered.

- Original map revision brief: the initial maps were too small and lacked hiding clutter. Aurora requested the larger, denser Wharf, revisions of all three earlier builds, then Backstage Mayhem.


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

- [Draft PR6](https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/6)
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


## Duels selector and personal loadouts — prepared, not live

- PulseGames 0.3.6 / draft PR6: `/duels` or the game menu opens combat style → discipline
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

- Backstage Mayhem final correction completed at Aurora’s request: framed/glazed rear catwalk wall closes roof access; castle lamps have full stone supports. Actual close-up images inspected and all 44,855 states match, with zero missing/wrong/unloaded states. All five commissioned maps are now built and awaiting Aurora visual approval; none is registered or gameplay-tested. Review starts with Market Day, then Crooked Library, Harvest Festival, Smuggler’s Wharf and Backstage Mayhem. Server updates/restarts remain with Aurora’s other agent after review/setup.

- Aurora approved Market Day for setup and specified reachable upper hiding areas across all five maps, plus barrier containment to prevent escaping. Market Day now has eight ladder lofts and two mezzanines with complete barrier enclosure (47,794 checked states). Crooked Library already has reachable furnished upper levels; containment and roof preservation checked (49,062 states). Harvest Festival additions require fresh verification in the farm world after the player returned to the lobby during the build. Wharf and Backstage upper-area/containment revisions remain next. No arena registration or restart performed.

- Prop Hunt setup update: Aurora requested saving the approved map and proceeding to the next. Farm verification was repeated in the correct world (42,945 states), resolving the earlier lobby mismatch. Registered Harvest Festival and the previously approved Market Day; checklists passed and `/pulse arenas` lists both saved IDs/templates. Settings: classic/infection, configured 2-16, seven spawns, bots disabled, void-y74. Wharf (67,514 states) and Backstage (66,024 states) upper additions/containment are now checked. Player is in Smuggler’s Wharf for review; Crooked Library and Backstage approvals remain pending. No match, restart or deployment performed.

- Aurora approved Smuggler’s Wharf; registered `smugglers_wharf` with Classic/Infection, configured 2-16, seven spawns, bots disabled and void-y74. Checklist passed; live-save message and `/pulse arenas` confirm registration/template loading. Corrected the earlier Market Day/Harvest Festival negative-coordinate spawn and spectator points: old cells intersected walls; new cells have solid floors and clear air. Re-saved both with every setting preserved. Backstage Mayhem is now loaded for review; Crooked Library still needs approval. Gameplay/crossplay tests and restarts remain pending.

- Aurora approved Backstage Mayhem subject to audience seating correction: all 80 wooden seats now look toward the stage with red wool behind; actual interior images inspected and all 66,024 states match. Registered `backstage_mayhem`; seven clear-floor spawns, enclosed waiting/spectator points, Classic/Infection, configured 2-16, bots disabled and void-y74. Checklist/live-save and `/pulse arenas` confirm template loading. Crooked Library is loaded for the final review. Played rounds, crossplay acceptance and server restart remain pending.

- Crooked Library review revision: Aurora requested more random clutter in the bottom area. Added 18 varied groups of book sorting piles, barrel cabinets, repair desks, lecterns and supplies (180 blocks), preserving existing shelves and stair approaches. Actual archive images reviewed; all 49,242 states match. Map remains in Aurora review and is not registered.

- Aurora approved Crooked Library after the archive clutter revision. Registered `crooked_library` with Classic/Infection, configured 2-16, seven clear-floor spawns across all three levels, bots disabled and void-y67 below the archive floor at y73. Waiting/spectator points are in the enclosed upper galleries. Checklist passed; live-save and `/pulse arenas` confirm all five commissioned Prop Hunt arena/template IDs loaded. All visual reviews and registrations are complete; played rounds, Java/Bedrock acceptance and server updates/restarts remain pending with the other agent.

- Lobby additions completed and checked (8,701 states); Aurora commissioned twelve new maps: three each for Kit PvP, KOTH, Dodgeball and Dropper. New batch build/review/registration progress is tracked above; no new arenas registered without approval.

- Twelve-map construction batch completed: three each for Kit PvP, KOTH, Dodgeball and Dropper. Actual section images and full plan states checked; spawn clearance/boundaries checked, Dropper water targets/apertures checked. Sunken Citadel centre stair gap corrected and rechecked. Player returned to Sunken Citadel for Aurora review. No new batch arenas registered or played rounds claimed.
