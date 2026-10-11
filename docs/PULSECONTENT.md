# PulseContent Prop Hunt trial

Custom Prop Hunt stays off by default. This draft adds a selectable native-block
palette, explicit grid lock/unlock and a small player-head target. PulseContent
owns both moving models and temporary snapped blocks. No second resource-pack
pipeline or client-only block protocol is implemented in PulseGames.

## Player flow

Hiders receive a book to choose their disguise during the hiding phase, plus a
slime ball to lock/unlock. The menu opens at the start; choices are fixed when
seekers are released. A locked player must unlock before changing their choice.
The note-block taunt remains available.

The default palette is hay bale, crafting table, bookshelf, melon, pumpkin,
oak log, stone, bricks, moss and player head. Maps can use a subset or change
names/model IDs. Unsafe/reactive blocks and duplicate material entries are
rejected. Native heads use the default face; arbitrary player skins/profile
lookups are not part of this change.

Locking snaps to integer block coordinates, including negative coordinates.
Full blocks occupy a real 1×1×1 Minecraft cell. The native player head is centred
in that cell and occupies 0.5×0.5×0.5. The player stands above their owned prop,
cannot move while locked, and can still turn the camera. Right-clicking the slime
ball restores the cell and the moving model. Finding, infection, quitting and
round cleanup also close the handle. PulseContent refuses occupied space, weak
support, unavailable content and conflicting ownership. Rejected lock positions
restore the moving disguise rather than leave an invisible unowned player.

## Hit rules and physical limits

The block palette uses six damage per accepted sword hit, at most once per
600 ms. Seekers must aim within three blocks. Swing, entity-hit and block-hit
paths share the same cooldown. A single attack selects only the nearest prop;
a wall blocks the ray. Moving models use a feet-centred cube matching the chosen
block/head dimensions, so the ordinary tall player damage box is rejected.
Locked props use Minecraft's actual native block raytrace, including the head's
smaller outline. Ordinary damage to the hidden locked player is cancelled;
validated prop hits enter the normal game damage/death pipeline.

This changes the target shape, not the player's walking collision or eye height.
A moving head cannot crawl through head-sized gaps. Locks deliberately require
two clear cells above a fully supported air cell, even for a small head, because
the hidden player still needs body space. The game owns its role potion effects:
snapped hiders get invisibility for local third-person view; unlock/end removes
it and ordinary game reset clears effects. Existing game snapshots do not
restore pre-game potion effects. Equipment/self-view and camera behaviour need
client acceptance, especially on Bedrock.

## Configuration and assets

The existing `features.canary-players` trial applies to every participant. Enable
`content.prophunt.enabled` and `content.prophunt.block-snapping` only in the trial.
The shipped `content.prophunt.block-palette` maps each material/name to a
PulseContent model ID such as `readycraft:prop_hay` or `readycraft:prop_head`.
Those IDs are a configuration template, not proof that models are installed.
Every configured model must exist and be allowlisted before the round starts.
If snapping is off, the previous `content.prophunt.props` model list remains in
use. With custom content off, the original random helmet game is unchanged.

In the companion PulseContent draft, both `minigames-visuals.enabled` and
`minigames-block-props.enabled` must be true; add the chosen model IDs to
`minigames-visuals.allowed-content`. Owner: Aurora; review: 2026-10-25. Keep the
switches off until the acceptance below passes. No production rollout is implied.

Moving model contract: a full prop is a single cube from [-8,0,-8] to [8,16,8]
in Blockbench units; a head uses [-4,0,-4] to [4,8,4]. Keep it centred at the
player's feet, with no animation that changes its bounds and no hitbox/seat bones.
The PulseContent compiler requires idle, walk, attack, hurt and death tracks.
Model textures/face directions should match the corresponding native block.
The companion's `examples/prophunt-blocks` utility builds these model sources
from a supplied Minecraft Java client archive; generated game textures are not
bundled in this repository. Compile and activate them through PulseContent.

## Runtime contract

Companion draft: [PulseAddon #27](https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/27),
stacked on runtime #26. Moving visuals use minigames API1; native locks use the
separate block-prop API1. Optional methods are cached per provider instance.
Paper26.2 / Java25 is required for the companion; PulseGames still compiles
against Paper1.21.4 / Java21. The deployed server version was not inspected.

PulseContent owns catalogue/pack readiness, model handles, native cell ownership,
player visibility and collision restoration. The fixed ready audience must
include everyone in the isolated instance world, since native blocks are globally
visible. New/departing viewers, changed sessions/worlds, content/provider loss or
altered prop/support blocks cancel custom play. Failed cleanup retains ownership
for retry and prevents replacing it with another appearance. Externally replaced
blocks are not overwritten. Crashed instance worlds must be discarded.

Native cells avoid using a display entity for the stationary prop; Geyser's
[documented display-entity limitations](https://geysermc.org/wiki/other/rainbow/)
are another reason to keep rendering in the shared content service. This design
is not a substitute for measuring actual client behaviour.

## Acceptance before enabling

Test Java hider/Bedrock seeker and the reverse: every palette model, selection
expiry, negative-grid locks, full/head outlines, moving-to-locked visibility,
first/third person, held-item/name hiding, head-height misses, blocked/reach hits,
duplicate attack packets, hit/death/infection, moving clearance, lock rejection,
pack rejection/reconnect, spectator changes, provider shutdown and world cleanup.

Unit and native Paper geometry checks are recorded in VERIFICATION-2026-10-11.md.
No mixed Java/Bedrock playtest or full match was performed. The old PulseContent
encounter trial does not prove player-disguise parity. Keep this draft disabled
until the moving asset pack and client acceptance pass.
