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
- The round-only size modifier is transient (not saved into player data). Original
  scale modifiers, visibility and collision are restored on found,
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
and PulseContent pack-readiness checks remain in force. Prop Hunt bots now use the same palette and role powers; see docs/QUEUE-BOTS.md for rollout and acceptance status.

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

## Map-specific disguise palettes (prepared update)

Each arena may save `settings.prop-palette` as a list of materials from the configured PulseContent block palette. The same resolved list drives starting disguises, the player selector, bots and decoys. A missing list preserves the global defaults; an explicitly empty, malformed or unsupported list blocks starting rather than silently widening choices. Custom model availability still goes through the normal Java/Bedrock content readiness checks.

During setup, select the playable map bounds with the wand and choose **Map disguise palette → Use wand selection as palette region**, then **Scan palette region**. The region excludes decorative scenery outside the play area. Toggle individual supported disguises in the same menu and save after reviewing.

Equivalent agent commands within an active Prop Hunt setup session:

```text
/pulse setup region palette <x1> <y1> <z1> <x2> <y2> <z2>
/pulse setup palette scan
/pulse setup palette list
/pulse setup palette add PLAYER_HEAD
/pulse setup palette remove STONE
/pulse setup palette global
```

The scan examines up to 4,096 blocks per tick, up to the existing one-million-block region limit. All selected chunks must already be loaded; it never generates terrain or changes blocks. An unloaded chunk, invalid height, cancelled session or unsupported-only region leaves the previous palette untouched. Review messages indicate failure instead of saving partial results. Heads are included automatically only when PLAYER_HEAD blocks occur in the region; add them manually if wanted. Only the existing supported materials/models can be selected—this does not invent crossplay models for every block in the map.

Saving a new Prop Hunt arena with a palette region but no palette triggers the scan and pauses for review; save again after reviewing. Explicit **Use global defaults** suppresses that automatic scan. Existing saved selections are preserved; explicitly scan again after rebuilding the map, then review and save. Matches never scan, so temporary blocks cannot change the available disguises.

For an already-configured arena, preserve its existing definition and template. An agent may back up and update only the palette settings directly, then reload arenas; do not start a blank setup session and overwrite the other arena fields. Example:

```yaml
settings:
  prop-palette: [BOOKSHELF, OAK_LOG, PLAYER_HEAD]
  prop-palette-source: reviewed
```

Per-arena choices are frozen when a match starts. Already running matches retain their selection.

Testing: compare two maps with different lists on Java/Bedrock; verify start disguise, choice menu, bot disguise and decoy menu all stay within each map's list. Verify global fallback on an old map, invalid/empty-list rejection, scan cancellation and failed-scan retention. No existing live arena is changed automatically by installing this update.

### Expanded block support

The supported ceiling is now 27 disguises (26 cubes plus a player head). The 17
additions are barrel, cobblestone, stone bricks, dark oak log/planks, spruce
log/planks, oak planks, smooth sandstone, polished andesite, loom, jukebox,
note block, smoker, smithing table, carved pumpkin and mossy cobblestone.
These were selected from the five approved maps' saved template block palettes.

The default configuration stays at ten until matching content is ready. Merge
`docs/prophunt-expanded-palette.yml` into the existing block-palette only after
building/installing the paired PulseContent models and Java/Bedrock packs and
allowlisting their visual IDs. The source generator is in the PulseAddon repository
under `plugins/pulse-content/examples/prophunt-blocks`; no new PulseContent runtime
binary is required. Never activate config ahead of its content release.

New props are static, full-sized cubes: closed barrel, unlit smoker, vertical logs.
They share existing snapping/body/decoy behaviour; orientation and block state are
not inferred. Leaves, chests and irregular shapes need separate model work.
