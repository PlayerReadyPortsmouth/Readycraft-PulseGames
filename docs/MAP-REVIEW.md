# Map reviews and Prop Hunt bot acceptance

Prepared extension to draft PR13, 14 October 2026. Not deployed. Attribution:
AurorasChaos (on behalf of Player Ready).

## Keep the stages separate

A build can be registered before it has an arena definition. Built, visual review,
Java playtest, Bedrock playtest and bot playtest are explicit evidence records.
Configured is calculated from the saved arena definition and captured template
folder. Configuration validation never means the map is visually approved or
playtested. These records do not publish, enable or block an arena automatically.

```text
/pulse maps list [game]
/pulse maps register <game> <map-id>
/pulse maps check <game> <map-id>
/pulse maps export <game> <map-id>
/pulse maps revision <game> <map-id> <build-label>
/pulse maps mark <game> <map-id> built pass <evidence note>
/pulse maps mark <game> <map-id> visual pass <reviewer and observations>
/pulse maps mark <game> <map-id> java pass <mode, players, result and match reference>
/pulse maps mark <game> <map-id> bedrock pass <mode, players, result and match reference>
/pulse maps mark <game> <map-id> bots pass <difficulty, roles, result and match reference>
```

Use `fail` or `pending` instead of `pass` to record problems or outstanding checks.
Notes are required and limited to 400 characters. All commands require
`pulsegames.admin`; visual evidence also requires `pulsegames.maps.approve`
(default op). Agents must record Aurora's actual visual decision, never infer it
from a successful configuration check. Historical tracker approvals are not
silently imported into the server ledger.

The report checks the canonical requirements for the selected game: spawns,
regions, objectives, supported modes, team configuration and navigation hints.
It does not load worlds or chunks. Physical clearance, reachability, gameplay,
visual review and client compatibility remain separate checks.

Reports are standalone HTML under `plugins/PulseGames/map-reports`; open one in a
browser to print or save as PDF. The durable ledger is `map-reviews.yml`; include
it in backups. Invalid ledgers are preserved and further review writes disabled
until repaired. Exported reports are dated snapshots, not live dashboards.

## Changes invalidate evidence

Evidence is bound to a build revision, arena configuration and capture marker.
Recapturing a map or changing its configuration makes earlier marks stale.
Old evidence remains visible until explicitly replaced. After editing terrain
outside the capture workflow, run `revision` before reusing approval evidence.
The plugin cannot detect arbitrary in-place terrain edits. A revision label gets
a unique suffix, so reusing a label cannot resurrect an old approval.

Capture the final build and finish configuration before recording acceptance.
If configuration changes after visual approval, recheck and record that approval
against the current revision. Missing records are never treated as passes.

## Prop Hunt bot checks

Bots choose a fitting disguise from the map palette and use existing hiding hints.
Hiders can place nearby decoys with independent disguises and sprint when they
see a threat. Seekers can sprint during visible pursuit and use the ordinary
coarse pulse signal; a strong signal prompts a short local search, with no hidden
bearing or target identity. Decoys can fool the pulse. Charges, placement rules,
cooldowns and cleanup use the same handlers as human players.

Test Classic and Infection on approved maps, taking both roles yourself. Check
small disguises and one-block gaps, wall-adjacent locking, valid/invalid decoy
placement, cooldowns, visibility behind walls, false pulse signals from decoys,
elimination and complete NPC/decoy/world cleanup. Repeat in Java and Bedrock.
Run two copies of the same map and confirm neither affects the other.

This is local random decoy placement, not elaborate scene planning. Actual
Citizens movement, hiding quality and client appearance need playtesting. Use
`/pulse practice` and record its match reference alongside observations.
