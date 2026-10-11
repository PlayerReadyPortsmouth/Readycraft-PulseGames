# Minigames development and play-test tracker

Last updated: 11 October 2026. Owner: Aurora. Agents should update this file as
work and player feedback arrive; keep it in the PulseGames repository.

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
| CoBuilder command-announcement disconnect | Client mod 0.4.1 prepared separately | Install/relaunch client at a convenient time and retest announcements |
| Distant lobby Trial Chamber | Diagnosed; bounded cleanup tooling prepared | Separate permission and fresh edit-preserving checks before cleanup |

Draft changes currently relate to PulseGames PR4 and PulseContent PR28; neither
is evidence that these new updates are live. See the dated log for verification.

## Active map batch: Prop Hunt

**Build five maps first. Aurora will visually inspect and approve all five before
any of them are configured as arenas.** This is an explicit user gate, recorded
11 October 2026. Building completion is not setup permission.

- Builder agent: create five distinct permanent build worlds/maps and report their
  actual names and visit instructions here. Preserve existing lobby and maps.
- Review: Aurora visually verifies all five. Record her decision and requested
  changes per map; do not infer approval from elapsed time or a successful build.
- Only after Aurora confirms the five-map review is complete may the agent start
  `/pulse setup`, capture templates or register these maps in matchmaking.
- After setup, verify exact saved display names, arena IDs, modes and templates
  from the server definitions, then perform the two-human-player tests.

| Map | Build-world name | Build state | Aurora visual review | Arena setup |
|---|---|---|---|---|
| 1 | Awaiting builder report | Planned / batch in progress | Pending | Blocked on all-five review |
| 2 | Awaiting builder report | Planned / batch in progress | Pending | Blocked on all-five review |
| 3 | Awaiting builder report | Planned / batch in progress | Pending | Blocked on all-five review |
| 4 | Awaiting builder report | Planned / batch in progress | Pending | Blocked on all-five review |
| 5 | Awaiting builder report | Planned / batch in progress | Pending | Blocked on all-five review |

These placeholders are not invented map names or claims that any individual map
has already been built. Replace them with the builder's verified names.

## Per-game readiness

“Two humans” below describes catalogue requirements before arena limits. Where
additional players are required, suitable bots or a deliberate test mode are still
needed. Do not bypass team/role requirements and call that a completed test.

| Game | Maps / player testing | Current bots | Two humans | Next work |
|---|---|---|---|---|
| **Duels** | **3 user-built maps: Courtyard, Ruined Temple, Foundry. Aurora reports gameplay works.** Initial trial arena also retained. | Classic, OP, Sumo | Yes | Adjust kits/loadouts; consider Mace PvP, Crystal PvP and further modes; difficulty levels; test each map/mode |
| KitPvP | Trial arena; bot tested earlier, detailed acceptance not recorded | FFA only | Yes | Kit balance; OITC bow/ammo bot; respawn and mixed-client tests |
| KOTH | Trial arena available; full acceptance not recorded | Solo and teams | Solo yes; teams currently needs 8 total | Fix test arena capacity for teams; hill contesting and team balance |
| Dodgeball | Court test arena; Aurora reports cooldown too slow and hits unclear | Solo and teams | Yes | Test prepared cooldown/feedback; dodge and lead targets; difficulty |
| Dropper | Two-drops test arena | None | Race yes; practice is solo | Test landing/checkpoint/reset behaviour; controlled-fall bots |
| Prop Hunt | **Five-map build batch in progress; Aurora must visually approve all five before setup.** Trial arena exists; startup fix pending deployment | None | Live minimum 3; prepared update lowers to 2 | Deploy validated startup fix; test palette, snapping and head props; fair seeker/hider bots |
| Spleef | No accepted map/test recorded | None | Yes | Maps; safe-floor navigation and digging; Splegg projectiles |
| TNT Run | No accepted map/test recorded | None | Yes | Maps; disappearing-floor routing and double jumps |
| Block Party | No accepted map/test recorded | None | Yes | Maps; colour-aware movement and elimination tests |
| Avalanche | No accepted map/test recorded | None | Yes | Maps; falling-block prediction and evasion |
| Volcano | No accepted map/test recorded | None | Yes | Maps; climbing routes and lava/eruption avoidance |
| Parkour | No accepted map/test recorded | None | Sprint yes; elimination needs 3 | Builder-authored routes, real jumps and checkpoints |
| Elytra Racing | No accepted map/test recorded | None | Yes | Flight routes, checkpoint passage, rockets and recovery |
| PulseKarts | No accepted map/test recorded | None | Yes | Ice track, boat steering, checkpoints, laps and item use |
| Skywars | No accepted map/test recorded | None | Solo/insane yes; doubles needs 4 | Loot/equip, bridging, safe combat and team tests |
| Bedwars | No accepted map/test recorded | None | Duos needs 4; quads needs 8 | Resources, shops, bed defence/destruction, bridging and respawns |
| Build Battle | No accepted map/test recorded | None | Yes | Theme-aware building in plots, phases and fair voting bots |
| Death Run | No accepted map/test recorded | None | Classic needs 3; double trouble 4 | Runner routes, Death roles and trap timing |
| Villager Defense | No accepted map/test recorded | None | Yes | Hostile-mob targeting, Mayor defence and co-op survival bots |
| ReadyPlayerZ | No accepted map/test recorded | None | Yes | Zombie targeting/kiting, shopping, revives and wave progression |
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

Potential modes, **not implemented or promised as ready**:

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
