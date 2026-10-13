# Prop Hunt: compact disguises and role tools

Candidate: PulseGames 0.3.8 with PulseContent Paper 0.3.3. Not deployed or human
crossplay-accepted. Aurora reports the preceding version works nicely on Bedrock
(13 October); map, mode and player count were not recorded. This update addresses
her self-visibility, low-gap and unstable-lock feedback.

## Disguise behaviour

The existing `content.prophunt.enabled` and `block-snapping` settings enable the
compact prop path. The palette and existing packs are reused; no new Geyser content
registration is introduced. Both candidate jars must be installed together on
MinigamesTest. An older content API gives a start-time explanation rather than
starting a partially working round. No production install or restart is authorised
by this document.

- Hiders remain invisible while moving and locked. Their full-size prop follows at
  their feet for other participants, through PulseContent/BetterModel/GeyserModelEngine.
- The hider sees a 40% preview at their own feet. A full cube would surround the
  reduced first-person camera; this separate audience avoids that obstruction.
- Full-block props use player scale 0.5 (0.9-block body); heads use 0.25
  (0.45-block body). The chosen prop still determines seeker hit geometry: 1-block
  cubes or half-block heads. Shrinking does not make a full cube harder to hit.
- Movement checks the prop footprint against solid block bounding boxes. Full
  blocks need a full footprint; heads fit smaller spaces. Bounding boxes are
  conservative around irregular stairs/fences; do not assume every visual notch
  is traversable. Liquids, unloaded chunks and outside-border positions are refused.
- Locking snaps X/Z to the block grid while retaining the supporting surface's Y.
  It does not place a solid block or lift the player onto one. A one-block-high
  gap is valid. A rejected snap preserves the existing moving disguise/location.
- A 350ms debounce prevents paired interaction packets immediately undoing a lock.
  Camera rotation remains available while locked. Unlocking leaves the player at
  the same position, without destroying/recreating the prop.
- Seekers hit a single nearest prop within three blocks. Real walls occlude hits;
  native player-hit and arm-swing packets share a cooldown. Decoys break without
  damaging their owner or awarding a player kill.
- Original scale modifiers, visibility and collision are restored on found,
  infection, elimination, quit and cleanup. Owned props/decoys are removed on all
  those paths. The legacy content-disabled mode is unchanged.

## Controls

| Role | Hotbar slot | Item | Action |
|---|---:|---|---|
| Hider | 1 | Book | Choose own disguise during hiding phase, while unlocked |
| Hider | 2 | Slime ball | Lock/unlock |
| Hider | 3 | Echo shard | Aim at a nearby floor/shelf, then choose a decoy disguise |
| Hider | 4 | Barrier | Clear all your decoys |
| Hider | 5 | Note block | Taunt |
| Hider | 6 | Feather | Four-second Speed II escape sprint; unlock first |
| Seeker | 1 | Iron sword | Find hiders or break decoys |
| Seeker | 2 | Compass | Proximity pulse, available after release |
| Seeker | 6 | Feather | Four-second Speed II search sprint |

Each decoy has its own selected disguise, independently of the player's disguise
and their other decoys. Place up to three to make a small scene. Aim at a surface
within four blocks; occupied or obstructed placements are refused without consuming
cooldown. Decoys are non-colliding models, not temporary map blocks. They expire or
break on a seeker hit. The pulse reports strong/faint/no nearby signal without an
exact location or direction. Decoys contribute to that signal.

## Map settings

Use the existing setup session, `/pulse setup set <key> <value>`, then save. Numeric
settings also appear in the Setup Book checklist. Do not recapture or overwrite an
approved map merely to change settings; preserve its bounds, spawns and template.

| Setting | Default | Allowed range |
|---|---:|---|
| `prop-powers` | true | true/false (command or arena YAML) |
| `decoy-limit` | 3 | 1-3 per hider |
| `decoy-seconds` | 60 | 10-120 seconds |
| `decoy-cooldown` | 12 | 2-60 seconds |
| `sprint-cooldown` | 25 | 5-120 seconds |
| `pulse-cooldown` | 25 | 5-120 seconds |
| `pulse-radius` | 12 | 4-24 blocks |

These are permanent per-map game rules, owned by Aurora, not temporary rollout
flags. Review balance after the first two-player test; `prop-powers: false` disables
role powers while retaining the compact disguise/lock fixes. Existing trial access
and PulseContent pack-readiness checks remain in force. Prop Hunt bots remain unsupported.

## Acceptance checklist

- Java and Bedrock, swapped seeker/hider roles: own preview follows walking,
  jumping, turning and locking; other client sees the correctly sized prop.
- One-block tunnel: enter, move, lock next to walls, rotate, unlock and leave.
  Repeat with a head under a half-block shelf; verify wall occlusion/reach.
- Place three different decoys; try invalid placement and a fourth; test expiry,
  clearing, hitting, cooldowns and pulse false positives. No map block changes.
- Sprint both roles, reject seeker powers before release and frozen/inactive users.
- Find/infect a hider in a low gap, leave mid-round, finish a round, disable/reload
  content in a local lab: no tiny/invisible players, stranded viewers or stray props.
- Map review: one-block hiding recesses must remain reachable by a seeker's
  three-block attack ray. Do not create long tunnels only hiders can enter with no
  alternative seeker angle; shrinking changes which hiding spots are fair.

Validation: focused game/content tests and a disposable Paper 26.2/BetterModel 3.5.0
runtime probe. Real player movement, first-person rendering, Bedrock camera behaviour
and power balance still require the acceptance checks above. No VPS restart performed.
