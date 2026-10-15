# Queue-filling bots

Bots join normal waiting games after a configurable delay. They count towards the
minimum, receive teams and kits, and participate in the same damage, death, score
and round lifecycle as human contestants. The deployed 0.3.14 build names them `Bot_ReadyN`; prepared 0.3.15 uses playful names such as `WaffleFox_NPC` and `MangoDuck_NPC`.
They are ephemeral Citizens player NPCs, not extra network connections or recorded
online users. A human joining before the round starts takes a bot's seat. Running
rounds do not accept late replacements. Leaving the last human ends the instance
and destroys its bots; bots never create unattended matches.

## Prepared 0.3.15: names and less mechanical decisions

Every supported controller receives a playful, profile-safe name with an `_NPC`
marker. The first 256 combinations have no sequence number; later names use a
short unique suffix. Queue counts, human replacement and competitive eligibility
continue to use owned bot UUIDs, never the display name. Existing running bots
are not renamed mid-match.

Generic combat bots retain a visible opponent unless another is substantially
closer. New-target reactions vary by difficulty (5–20 ticks), without clearing
existing attack cooldowns or identifying hidden players. Generic route updates
have a small extra 0–5 tick variation. This covers ordinary melee, ranged and
co-op controllers; specialised movement games retain their time-critical logic.
Block Party uses stable per-bot preferences between similarly close correct-colour
tiles; Spleef actions add small nonnegative timing variation. Quake adds its own
target commitment, weapon-range strafing, close/wounded retreat and varied loadouts.
Prop Hunt retains its visible-clue memory rather than gaining role knowledge.

This is a shared improvement pass, not a claim that every controller now emulates
all human decisions. Hazard/bridge movement is deliberately not delayed by generic
hesitation. No fake chat, extra damage, increased reach or hidden-information
access is added. Individual maps still need navigation/play testing; unsupported
games do not become bot-supported through naming changes.

Acceptance: mixed human/bot queues, replacement, names/diagnostics and cleanup;
Duels/Kit PvP/King of the Hill/Dodgeball target switching; co-op target acquisition;
Skywars/Bedwars bridge safety; Prop Hunt clues; hazard games' urgent movement;
Spleef action cadence; Quake selection, retreat and safe spawn areas on both clients.

## Supported modes

| Game | Modes | Behaviour |
|---|---|---|
| Duels | classic, op, sumo | visible opponents, navigation, ordinary melee |
| Kit PvP | ffa, oitc | visible opponents, melee/respawn; OITC real arrows, bow draw, finite ammunition and sword fallback |
| Quake | classic and team-size variants | visible-target shooter bots, abilities; 0.3.15 adds selected loadouts, evasive movement and random safe spawning |
| Prop Hunt | classic, infection | supported-cell hiding and normal grid locking; seekers use visible motion, short memory, audible clues and ordinary reach inspections |
| Skywars | solo, doubles, insane and team-size variants | nearby chest looting, finite gear, checked height steps/bridges, melee and bows |
| Bedwars | duos, quads and team-size variants | real resource pickup, finite purchases, bounded bed defence/bridges, public bed objectives and combat |
| King of the Hill | solo, teams | capture hill, fight visible opponents, respawn |
| Dodgeball preview | solo, teams | approach/retreat and the same projectile throw as humans |
| Villager Defense | easy, hard | defend objective, fight wave enemies |
| ReadyPlayerZ | classic, frenzy | wave combat, earned-point purchases, revival |
| Block Party | classic, hardcore | navigate to publicly called colour |
| Avalanche | classic | evade visible falling blocks |
| Spleef | classic, decay, splegg | safe floor navigation, protected digging, earned ammunition and shared egg launcher |
| TNT Run | classic | continuous routes across surviving floor; no double-jump controller yet |
| Volcano | classic, eruption | climb reachable ground, avoid lava and visible falling rocks |

Other modes stay human-only. In particular, no racing, building, Crystal/Mace Duels,
parkour or TNT Run Double Jump bot controller is claimed. The table describes the
prepared source, not the deployed server; consult the tracker for live status. Bots do not discover hidden players,
attack through walls, teleport out of stuck paths, or receive custom damage.
Decision work runs four times per second, with difficulty-based navigation intervals
and faster replanning for TNT Run; attacks
have a delay and melee has a miss chance. These are basic controllers, not tuned
human-level opponents. Difficult terrain needs a playtest before map opt-in.

## Trial configuration

Install a compatible Citizens build separately. Missing or incompatible Citizens
leaves human queues available and reports a diagnostic via `/pulse bots`. No
plugin downloads happen from PulseGames. Restart after installing the provider.

In `plugins/PulseGames/config.yml`:

```yaml
features:
  broad: false
  canary-players: ["REPLACE-WITH-REAL-PLAYER-UUID"]
bots:
  enabled: true
  wait-seconds: 30
  max-per-match: 31
  max-total: 64
  difficulty: adaptive
```

For maps above four slots, bots fill up to **floor(capacity / 2)** alongside the
human players, bounded by free seats. For capacities of four or fewer, retain the
small-lobby exception and fill every remaining seat. Match/server caps still apply;
if they cannot satisfy the minimum, no bots are added. Joining humans reclaim bot
seats during waiting/countdown; there is no mid-round replacement.

| Capacity | Humans | Bots (if caps allow) | Competitive eligibility |
|---|---|---|---|
| 12 | 5 | 6 | No |
| 12 | 6 | 6 | Yes |
| 12 | 7 | 5 | Yes |
| 4 | 1 | 3 | No |
| 4 | 2 | 2 | No |
| 4 | 3 | 1 | Yes |
| 2 | 1 | 1 | No |

Difficulty can be overridden per arena with `bot-difficulty: adaptive|easy|normal|hard`.
The Setup Book cycles these choices. Existing explicit `normal` settings remain
fixed until changed; updating the jar does not silently rewrite arena configuration.

## Adaptive calibration

`adaptive` uses separate per-player/per-mode results in `bot-skill.yml`, from
completed human and bot-assisted rounds. Existing competitive totals mix variants,
so they are not imported. New profiles start at Normal. At least six completed
rounds are required, with an eight-result neutral prior and the latest 40 results
retained. Results are normalised by the expected chance of winning (team count or
FFA roster; co-op and Prop Hunt use an initial 50% baseline). This is a first-pass
calibration, not a measured matchmaking rating.

The group's lower median controls general navigation and objectives. A bot facing
a visible opponent uses that opponent's frozen round profile for attack cadence
and accuracy. No health, damage, reach, hidden-location access or preferential
victim selection is added. Fixed arena difficulty overrides adaptation.

Forced, drawn, zero-duration and abandoned rounds do not train profiles. Team
wins include eliminated teammates; co-op outcomes follow mission success; Infection
credits the original role rather than rewarding a caught hider's conversion. Bot
records are never persisted as players. Storage is local to the minigames backend;
shared SQL calibration is future work. Real client tests must tune the baseline,
difficulty thresholds before a broad rollout.

Custom block Prop Hunt bots require PulseContent Paper **0.5.4**'s explicit NPC prop
API, plus block snapping. The paired API verifies the owned Citizens actor while
preserving real viewers' resource-pack checks. Older renderers refuse custom bot
fills. Bots do not yet use Prop Hunt power-ups or authored hiding routes.

All humans in a match must be trial members. An empty canary list admits nobody.
`features.broad: true` is a deliberate wider rollout. In each approved arena's
`settings`, set `bots-enabled: true`. Unsupported modes are still refused.
New Dodgeball/Dropper previews additionally need their IDs in `games.preview`.
Use `/pulse reload` for configuration changes. Provider failure requires restart
after fixing its cause. Turning `bots.enabled` off ends bot rounds and removes
bots. Match and server caps are bounded; when there is insufficient capacity to
reach the normal minimum, no partial bot fill starts an undersized match.

Competitive eligibility freezes at round start from the **actual starting roster**.
Maps above four slots need humans to be at least half that roster; maps with four
or fewer need a strict human majority. Eligible rounds use the existing competitive
stats and match kill/win/participation rewards. Other rounds suppress those records
and rewards. Departures cannot change eligibility. Ordinary daily/playtime bonuses
are unaffected. Bots never receive persistent stats or rewards.

PulseNetwork 0.1.4 adds proxy-owned parties and group joins to advertised queues.
Without that helper, parties remain backend-local. The game menu exposes commands
and typed accept/deny instructions for Bedrock. Team modes reject parties larger than the
chosen team size; solo parties may play as opponents. Matchmaking skips queues
whose human groups cannot fit intact. Whole-party admission reclaims enough bot
seats before anyone moves, serialises reentrant admissions and rechecks membership
and connection state. Cancelled or failed joins roll back newly attempted members;
they do not restore a departed source match. Rounds cannot start inside admission.

Teams pack whole parties first, then use coarse calibrated strength to distribute
experienced players and bots. Fixed bot difficulty overrides are reflected in that
strength. The bounded allocator can use additional configured teams when necessary;
it waits rather than splitting an unplaceable party. This is a heuristic balance,
not a guarantee of equal teams. See `PLAYTEST-WORKFLOW.md` for network party limits.

Flag owner: Aurora. Review date: 2026-10-25. Remove the canary restriction only
after real human sessions cover every enabled mode, seat replacement, party/team
balance, elimination/respawn, provider loss, last-human departure and restart.
Keep the global off switch and map opt-in after rollout. No production install,
server upgrade, configuration write or wider rollout is part of this change.

## Validation limits

Unit tests exercise fill policy, waiting delay, human priority, unsupported modes,
canary/map checks and cleanup with an injected transport. The isolated local
Paper/Citizens probe checks real NPC actors, navigation, Bukkit melee and removal.
Neither substitutes for a human Java/Bedrock match or performance testing under
many simultaneous games. See the PR's verification record for exact builds and
results. The source still compiles against Paper 1.21.4; the modern local smoke
stack does not establish compatibility with every deployed Minecraft version.


## Next test session

Use accepted maps with `bots-enabled: true` and the normal `/play` queue. For TNT
Run, check sustained movement, layers, turns and holes before raising capacity;
Double Jump remains human-only. Volcano needs connected, climbable paths: the
controller does not teleport or invent bridges to disconnected ledges. Test both
Classic lava rises and Eruption rocks. Full human rounds and mixed Java/Bedrock
acceptance remain required even after local NPC probes pass.

KitPvP now has `/pulsekits` (alias `/kits`) plus a button in the game menu. Choose
the map default or an owned shop kit; locked kits remain locked. Selection is saved
for the next KitPvP spawn, never applied mid-fight. One in the Chamber keeps its
fixed loadout. On servers with another `/kits` command, use `/pulsekits` explicitly.
Check selecting, death/respawn, switching back to the default and reconnecting on
both clients. More Duels presets and shop inventory are separate follow-ups.

## Candidate acceptance checklist (14 October)

1. Verify the queue and eligibility examples above, including a human replacing a bot during countdown.
2. Check a party join, stale invite, membership change while queued, and a party too large for a team.
3. Use fixed Easy/Normal/Hard first to assess differences; then Adaptive with established profiles.
4. OITC: verify bow draw, arrow consumption, one-shot/arrow-return rules, melee fallback and respawn on both clients.
5. Prop Hunt: test both roles and Infection, visible motion versus stationary cover, heartbeat search, locking and viewer packs on both clients.
6. End/leave concurrent rounds and confirm NPCs, visuals and instance worlds are cleaned up.

This candidate is not deployed. Crystal/Mace, racing and building
controllers remain future work; maps and player acceptance are still needed for the
existing controllers too.

## Current bot diagnostics

Admins can use `/pulse bots inspect` for a bounded list, then
`/pulse bots inspect <name-or-UUID>` for the current objective, difficulty, visible
target or destination, route, state, reason and elapsed time since movement progress.
The view contains no player win/loss history. A stalled report means observed lack
of progress, not a claimed diagnosis of Citizens' internal pathfinder. Skywars
also reports missing blocks, placement refusal, unsafe terrain and bridge budget.

## Skywars map and acceptance requirements

Use level island walkways and give each starting island a chest visible within
4.5 blocks of its bot spawn. Loot is generated by the same ordinary chest rules;
bots receive no guaranteed equipment or free bridge blocks. Authored patrol/routes
can identify safe landing points on other islands. Without a route, bots use the
normal spawn patrol and actually visible opponents as goals. They inspect only
loaded nearby chest blocks and do not read hidden chests across the map.

A bot takes finite chest items through inventory opening/click permission checks,
equips better material-tier armour and swords, and spends planks/cobblestone on
one adjacent bridge cell at a time. Block placement is cancellable and rolled back
when refused. `settings.bot-bridge-limit` defaults to 64 placements per bot per round,
clamped 0–128. Existing map bot opt-ins and trial access remain required.

Movement now includes checked adjacent height changes, straight stairs/slabs and
normal jump physics, plus finite bow ammunition. Unsafe routes still stop. No
teleport rescue, long void jumps, food/potion tactics or pearl use is claimed.
Material tier selection does not compare enchantments.
Actual Citizens movement, fair combat, chest protection interoperability, refill,
bridge exhaustion, multiple teams and instance cleanup require Java/Bedrock tests.


The latest party, results, rejoin and private practice commands are documented in
[PLAYTEST-WORKFLOW.md](PLAYTEST-WORKFLOW.md). Its island-game checks also cover the
new Bedwars controller and expanded Skywars movement/ranged actions.
