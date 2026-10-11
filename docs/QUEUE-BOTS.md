# Queue-filling bots

Bots join normal waiting games after a configurable delay. They count towards the
minimum, receive teams and kits, and participate in the same damage, death, score
and round lifecycle as human contestants. They are visibly named `Bot_ReadyN`.
They are ephemeral Citizens player NPCs, not extra network connections or recorded
online users. A human joining before the round starts takes a bot's seat. Running
rounds do not accept late replacements. Leaving the last human ends the instance
and destroys its bots; bots never create unattended matches.

## Supported modes

| Game | Modes | Behaviour |
|---|---|---|
| Duels | classic, op, sumo | visible opponents, navigation, ordinary melee |
| Kit PvP | ffa | visible opponents, melee, normal respawn |
| King of the Hill | solo, teams | capture hill, fight visible opponents, respawn |
| Dodgeball preview | solo, teams | approach/retreat and the same projectile throw as humans |
| Villager Defense | easy, hard | defend objective, fight wave enemies |
| ReadyPlayerZ | classic, frenzy | wave combat, earned-point purchases, revival |
| Block Party | classic, hardcore | navigate to publicly called colour |
| Avalanche | classic | evade visible falling blocks |
| Spleef | classic, decay, splegg | safe floor navigation, protected digging, earned ammunition and shared egg launcher |
| TNT Run | classic | continuous routes across surviving floor; no double-jump controller yet |
| Volcano | classic, eruption | climb reachable ground, avoid lava and visible falling rocks |

Other modes stay human-only. In particular, no Prop Hunt, racing, building, OITC,
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
  difficulty: normal
```

The fill target is half the mode/map capacity, rounded up, or the full capacity
when its maximum is four or fewer. The playable minimum can raise that target.
Configured match/server caps still apply; existing installation caps are not
silently increased by an update. Difficulty may be overridden per arena with
`bot-difficulty: easy|normal|hard`.

All humans in a match must be trial members. An empty canary list admits nobody.
`features.broad: true` is a deliberate wider rollout. In each approved arena's
`settings`, set `bots-enabled: true`. Unsupported modes are still refused.
New Dodgeball/Dropper previews additionally need their IDs in `games.preview`.
Use `/pulse reload` for configuration changes. Provider failure requires restart
after fixing its cause. Turning `bots.enabled` off ends bot rounds and removes
bots. Match and server caps are bounded; when there is insufficient capacity to
reach the normal minimum, no partial bot fill starts an undersized match.

A match that starts with bots stays unranked even if they are later removed.
Competitive records and match kill/win/participation tokens are suppressed for
all contestants. Human daily and playtime bonuses remain available. Bots receive
no persistent economy, stats, inventory snapshot or lobby daily bonus.

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
