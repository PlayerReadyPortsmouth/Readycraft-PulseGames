# Duels combat and personal kits

Prepared in PulseGames 0.3.6 / draft PR6. Not installed on MinigamesTest.

## Player flow

Open `/duels`, `/play duels` or Duels in the game menu. Choose Classic (1.8-style)
or Modern (1.9+), then Regular PvP / Sumo / Crystal / Mace, then 1v1–4v4.
Crystal and Mace are Modern-only. Classic requires the configured, pinned
OldCombatMechanics provider and both Pulse combat profiles allowed in the arena world.

**Each player chooses their own compatible kit.** The separate Your Kit button
saves a preference per combat style and discipline. Team size does not reset it.
Kits do not split queues: combat style, discipline and team size do. During a
match `/duels` opens the personal kit picker; changes apply next round without
refilling the current inventory. Tank/Archer still require their token-shop unlock.
KitPvP keeps its separate `/pulsekits` picker and next-respawn application.

## Configuring approved maps

Existing `classic`, `op` and `sumo` commands/maps remain supported. The new selector
uses `legacy-classic`, `legacy-sumo`, `modern-classic`, `modern-sumo`,
`modern-crystal` and `modern-mace`, with `-doubles`, `-triples`, `-squads` variants.
Duels always has two opposing sides; configure adequate spawn space and capacity.

Existing base mode permissions and per-mode capacities carry over to the corresponding
combat profiles. Editing modes in the setup menu expands inherited permissions into
explicit choices and sets `settings.combat-profiles-explicit: true`, allowing profiles
to be enabled independently. Explicit per-profile capacities override inherited limits.

Crystal maps additionally require `settings.crystal-enabled: true` and a `combat`
region. Mace maps require `settings.mace-enabled: true`. These are map capability
settings owned by the map maintainer, not automatic approvals of existing maps.
Enable only after visual review and checks for team spawn space, boundaries and the
discipline's needs. Crystal allows only player-placed obsidian to be broken, protects
original terrain from explosions, tracks crystal damage ownership, suppresses team
damage and resets placed blocks/crystals/projectiles between rounds. Held totems use
native resurrection. Mace uses modern timing, native wind charges and fall damage;
provide enough headroom and meaningful vertical space.

`duel-kits.yml` defines compatible choices and initial Crystal/Mace kits. Existing
`kits.yml` definitions take precedence for duplicate kit IDs. The initial loadouts
are candidates for balancing, not final competitive presets. Old explicit Classic/OP/
Sumo commands retain their fixed loadouts for compatibility; use the selector for the
new personal-kit flow.

## Acceptance still required

83 targeted regression tests and local Paper/Citizens probes cover independent round
kits, profile switching, real Crystal placement events and round cleanup, plus native
wind-charge jumping and native held-totem activation under lethal damage.
These are not full-match Java/Bedrock acceptance or exact 1.8 parity certification.

Test both clients choosing different kits; leave/rejoin; round changes; paid-kit locks;
all three existing maps and each enabled team size. Test Crystal chain explosions,
totem/offhand use, deaths and map reset, then Mace smash/fall damage and wind charges.
Do not enable new disciplines on all maps before those checks. Regular/Sumo reuse the
Duels controller; Crystal/Mace do not yet have discipline-specific bots. Prepared bot
coverage remains 11 of 23 games (four at the last live baseline).
