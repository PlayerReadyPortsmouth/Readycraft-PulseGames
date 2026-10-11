# Classic PvP candidate

Not deployed. Enable only for the MinigamesTest acceptance trial after restart
approval. `combat.classic-pvp` defaults false. This is a permanent optional
integration switch owned by Aurora; trial review due 18 October 2026.

PulseGames selects `pulse-classic` for PvP participants in their match world and
`pulse-modern` elsewhere, on leaving and elimination. OCM stores modesets per
player/world; PulseGames does not mutate its internal configuration. Join/world
changes refresh the selection. Modeset lookup uses OCM's public Bukkit service
API, with reflection to avoid bundling a second plugin or requiring it at boot.

The tested candidate is **OldCombatMechanics 2.7.0-ready.1**, based on upstream tag
`v2.7.0`, commit `920d6e88449884c284a76921de7221f134b09841`.
Upstream binary SHA256:
`f68f52c956103e6081dbf2d737ddf70436042e77997c9ae20eb5aaf87b263603`.

Stock OCM rejects Citizens actors because they are not online network sessions.
`ocm-npc.patch` permits only valid NPCs with a true metadata marker owned by the
enabled PulseGames plugin. These actors use configured modesets, never OCM's
online-player-only temporary override store. PulseGames applies/removes the
marker with its match lifecycle. Other offline entities remain excluded. The
candidate changes only `OCMModule.class` and the plugin version in the pinned jar;
its matching source patch and reproducible build script are retained here.

Install the candidate jar and copy `ocm-config.yml` to
`plugins/OldCombatMechanics/config.yml`. Back up any existing OCM installation and
configuration first; never overwrite unrelated server combat settings. The profile
has modern as its default, with no always-enabled gameplay changes. Classic
selects legacy timing, damage, armour, knockback, regeneration, potion and blocking
rules. Attack invulnerability remains 20 ticks. Mace/trident retain the upstream
weapon-specific timing; new Mace/Crystal modes require a separate rules review.
The shield fallback is selected for modern Java/Bedrock interoperability; this is
not a claim that either client has a native 1.8 sword-block animation.

Validation:

- Focused service tests: match-world/participant/PvP scoping, spectators, leaving,
  unchanged modesets, missing presets and NPC ownership marker.
- Real Paper 26.2 + Citizens + pinned OCM: unmarked NPC excluded, classic modules
  enabled for owned NPC, attack speed base 40, then restored to modern base 4.
- Client feel, native damage/knockback/blocking parity and full-round acceptance
  remain pending. NPC potion/blocking AI is not implemented. This lab probe does
  not prove that every OCM mechanic is identical for Citizens and network players.
- Do not deploy an unpatched upstream binary under the candidate filename;
  PulseGames checks the tested candidate version before enabling integration.

Upstream: https://github.com/kernitus/BukkitOldCombatMechanics
Public API: https://github.com/kernitus/BukkitOldCombatMechanics/blob/v2.7.0/API.md
Source is MPL-2.0; upstream packaged binaries include GPLv3 PacketEvents. Retain
upstream notices and source/patch provenance with any binary distribution.
