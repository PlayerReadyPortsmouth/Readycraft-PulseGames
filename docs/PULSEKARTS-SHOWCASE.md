# PulseKarts Copper Circuit showcase

Local PulseGames **0.11.0 candidate**, 31 October 2026. Default off; not deployed or accepted in Java or Bedrock clients. Attribution: **AurorasChaos (on behalf of Player Ready)**. This extends the configurable track-physics candidate; it does not establish that either candidate has shipped.

## Trial access

```yaml
karts:
  showcase:
    enabled: false
    broad: false
    canary-players: []
```

Enable only for an approved trial. With `broad: false`, **every human racer** must have their UUID in `canary-players`; an empty list admits nobody. Bots do not establish eligibility. This is a whole-race item ruleset, not an advantage granted individually to allowlisted players. The existing eight-item table remains the default. Without showcase access, optional prop settings are ignored and the ordinary race remains available (subject to its separate track-physics requirements). Losing access during an active showcase race cancels that race rather than switching its item rules midway.

The separate `karts.track-physics` gate still applies to configured jump/boost profiles and surface tracks. Showcase access alone does not authorise surface driving. Owner: Aurora / ReadyCraft. Review: **14 November 2026**. Remove the temporary showcase gate after mixed-client acceptance and a stable rollout, retaining authored prop data. No production flag changes or restart are authorised by this document.

## Items and feedback

| Item | Behaviour | Limits and counterplay |
|---|---|---|
| Repair kit | Clears current spin and slow; grants 40 ticks of hit protection. Can be used while spinning. | Does not teleport, repair terrain, restore lost speed or grant a boost. |
| Pulse wave | Marks its cast position with a cyan ring and sound; after 10 ticks, applies a 30-tick slow within 6 blocks. | Fixed origin gives opponents time to leave. Requires block line of sight. Excludes its owner and inactive racers. Existing shield, star, bullet and hit-protection rules apply. No terrain damage or lightning flash. |
| Magnet | Increases collectable-box reach from 1.2 to 3.5 blocks for 120 ticks, with a small coloured ring. | Requires line of sight; still claims only one shared box at a time and respects its 80-tick global respawn. Cannot collect while holding an item or rolling. Cannot refresh an active magnet. Excluded on maps that only use legacy item regions. |

At 20 ticks/second these are a 2-second repair grace, half-second pulse warning, 1.5-second pulse slow and 6-second magnet. Server lag can lengthen wall-clock durations. Reset cancels a pending pulse and magnet; departure removes their owner state. Ordinary bots hold repair for damage, cast pulse when an opponent is nearby and use magnet on collectable-box maps. Surface-route bot behaviour still needs played acceptance.

The showcase pool adds these three items while retaining position-based weighting. Vanilla mode keeps its original probabilities. Gameplay items are match inventory, not permanent possessions or paid upgrades.

All eleven hotbar/roulette icons request `readycraft:kart_<item>`: `shell`, `turbo`, `lightning`, `banana`, `triple_shell`, `star`, `bullet`, `shield`, `repair_kit`, `pulse_wave`, `magnet`. The optional PulseContent item API checks viewer readiness; unavailable content falls back to a vanilla item icon. A missing decorative model does not remove the underlying item mechanic.

## Authoring track props

Put entries in the arena's `settings` map. Each value is a quoted comma-separated string:

```text
kind,x,y,z,yaw[,binding]
```

Use consecutive `kart-prop1` through `kart-prop48`, with no gaps. Coordinates must be finite and between -30,000,000 and 30,000,000; actual display placement must be within world height and a loaded chunk. Yaw is -180..180 degrees. `kart-prop-range` defaults to 48 blocks and accepts 16..96.

| Kinds | Optional binding |
|---|---|
| `boost-panel`, `boost-panel-wall`, `boost-panel-ceiling` | Existing `boostN` region |
| `jump-marker` | Existing `jumpN` region |
| `checkpoint-pillar`, `checkpoint-beam` | Existing `checkpointN` region |
| `finish-pillar`, `finish-beam` | Existing `finish` region |
| `podium`, `trophy`, `marshal`, `spectator-drone` | None |

`boost-wall` and `boost-ceiling` are aliases. Names are case-insensitive; hyphens and underscores are accepted. Bindings must match the kind and name an existing region. A binding attaches activation feedback; **it creates no checkpoint, jump, boost or collision**. Race volumes and pad mechanics remain independently authored. Marshal and drone are scenery, not staff, moderation, player avatars or spectator cameras.

The runtime prioritises checkpoint/finish parts, then panels/jump markers, then decoration. It updates every 10 ticks with at most 12 active managed props and 32 nearby viewers per prop. All authored legacy item boxes reserve their share of the provider’s 32-instance budget first, including boxes in unloaded chunks; 32 boxes leaves zero managed prop slots. It never loads a chunk for a decoration. Missing models, pack readiness or exhausted visual capacity use small track-marker particles for functional props; purely decorative props may simply be absent. Provider retries are bounded, and handles are released when no longer visible and at match cleanup. Test actual density rather than assuming all 48 authored entries can render simultaneously.

## Reference assembly

Use the companion PulseContent source directory `plugins/pulse-content/examples/kart-showcase/` in ReadyCraft-PulseAddon. Its README and `manifest.json` define model dimensions and orientation. Merge its additive item/entity catalogue into a staging catalogue, preserving existing IDs; do not replace an installed catalogue with the example directory. Complete packs require the model providers' generated outputs.

At unit scale, 16 model pixels equal one block. For a road centred at **(0,64,20)**, two pillars and two beams form an eight-block clear opening with the beam underside four blocks above the road:

```yaml
settings:
  kart-prop-range: 48
  kart-prop1: "checkpoint-pillar,-4.3125,64,20,0,checkpoint1"
  kart-prop2: "checkpoint-pillar,4.3125,64,20,0,checkpoint1"
  kart-prop3: "checkpoint-beam,-2,68,20,0,checkpoint1"
  kart-prop4: "checkpoint-beam,2,68,20,0,checkpoint1"
  kart-prop5: "boost-panel,0,64,8,180,boost1"
  kart-prop6: "jump-marker,0,64,30,0,jump1"
  kart-prop7: "marshal,6,64,18,0"
  kart-prop8: "spectator-drone,6,69,22,0"
```

Create `checkpoint1`, `boost1` and `jump1` regions separately before using this snippet. For example, the checkpoint should cover the road passage at z=20; the props do not infer its bounds. Replace the four checkpoint kinds with their finish equivalents and bind them to `finish` to make the finish gantry. Keep all four yaws consistent. Rotate their offsets around the road centre as well as rotating each piece when orienting the whole gantry.

The floor boost panel is 2 × 3 blocks, less than 0.2 block high. Its local arrow faces **−Z** at yaw 0, so the example uses yaw 180 for a +Z road. The wall variant extends outward −Z and points down −Y; the ceiling variant extends down −Y and points +Z. Yaw rotates those local directions. Put their origins on the supporting face, with the model extending into the road. Arbitrary roll and sideways wall arrows are not supplied. Gameplay boost direction comes from the pad configuration, never the mesh.

Jump markers are 1.5 × 1.5 blocks and up to 0.625 block high. They are visual cues, not physical ramps. See [track physics](PULSEKARTS-TRACK-PHYSICS.md) for launch vectors, timing and safe landing areas. Cyan contrast is palette work, not an emissive shader or dynamic light. All animation slots in these example props are stationary; particles provide activation feedback.

## Acceptance still required

Targeted automated tests exercise item state rules, collection, eligibility, prop parsing and visual lifecycle. Their current result belongs in the handoff; this document makes no independent build-pass claim. Source previews are not client screenshots.

Before a wider trial, check Java and Geyser/Bedrock inventory legibility, item grip/use controls, model scale and arrows, wall/ceiling placement, gantry clearance, mixed-pack fallback, spectators, chunk/range entry and exit, reset/quit/reconnect, gate revocation, cleanup and provider loss. Drive pulse escapes/shields/walls and magnet pickups in real traffic, and assess particles and sounds for comfort. No human client QA or live rollout has been completed for this candidate.
