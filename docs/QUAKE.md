# Quake arena shooter

Status: deployed on MinigamesTest with PulseGames 0.3.14 (14 October 22:59 UTC / 15 October UK). Live bot support verified; client gameplay acceptance outstanding. No map is bundled or automatically published. Uses native items, sounds and particles, so no new resource pack is required.

## Playing

Choose Quake / Classic in the game menu, or `/play quake classic`. The opening eight-second freeze provides a weapon selector. Railgun is the default if no selection is made. Players keep their selection through respawns and can reopen the selector with the compass during spawn protection.

- **Railgun:** one-hit beam, 32-block range, 1.2-second cooldown.
- **Scattergun:** three horizontal rays, 40 damage each, 12-block range, 0.45-second cooldown. Three rays landing together defeat a full-health target.
- **Dash:** feather, six-second cooldown.
- **Jump boost:** rabbit foot, eight-second cooldown.

Right-click/use activates the held item on Java and Bedrock. Slots 1–3 hold the weapon and abilities; slot 5 holds the selector. Inventory movement is disabled during the round. Hit sounds, particles and action-bar HP provide feedback. Respawn takes two seconds, followed by two seconds of protection; firing ends your protection. HP is game-specific (100), displayed on the sidebar. Vanilla melee does not damage players. Falls below void-y respawn without awarding a kill.

First side to 20 kills wins, or the leading side after five minutes. A tied lead is a draw. Team friendly fire is excluded. Classic supports solo plus `classic-doubles`, `classic-triples` and `classic-squads`; map team count controls how many teams participate.

## Map setup

Use `/pulse setup start quake <arena-id>` in the built world. The Setup Book checklist and coordinate commands support the usual lobby, spectator, spawns, capacity, teams and settings. Minimum four spawn positions; provide at least one per supported team, preferably more for solo play. Solo respawns prefer the spawn farthest from active opponents; team respawns remain at their team's spawn.

Build an enclosed arena with intersecting routes, solid cover, vertical routes with ordinary stairs, and protected spawn sightlines. Avoid forced parkour as the only route: Citizens bots use ground navigation. Dash and jump must not allow escape over the boundary. No special regions or custom entities are required.

Settings:

```yaml
settings:
  void-y: 60
  bots-enabled: true
  target-kills: 20
  time-limit: 300
```

Set void-y below the lowest playable floor. The listed value is an example, not a universal height. Target is clamped to 1–100 and time limit to 60–900 seconds. Use the existing map-specific team-count/capacity configuration. Optional `bot-patrol-points` and `bot-recovery-points` work with the setup book and `/pulse setup addloc <key> <x> <y> <z>`. Review and validate before capturing/publishing a template.

Bots are supported in all Quake variants through the same weapon/ability actions and cooldowns. They choose weapons, use visible opponents only, patrol without a target, and adjust reaction/aim against each player's existing difficulty rating. Queue fill and competitive eligibility use the shared rules.

## Acceptance checklist

- Java and Bedrock: choose each weapon during opening freeze; default works if menu is closed.
- Rays stop at walls; allies, spectators and respawning/protected players are not damaged.
- Rail cooldown, scattergun spread and hit feedback feel fair with latency.
- Dash/jump stay inside arena; confirm no fall-damage interruptions.
- Death, void respawn, weapon persistence and short spawn protection.
- Solo target score, team combined score, timeout winner and tied draw.
- Bot joins, weapon choice, navigation, aim difficulty and shared cooldowns.
- Last player leaves: menu/cooldowns reset, inventory restored and world removed.

Quake stays under the existing test-access gate until these checks pass.

## Prepared 0.3.15: arena-wide spawning and evasive bots

This section describes the next build, not the deployed 0.3.14 behaviour above.
Opening spawns and respawns use the same safe random-placement policy for humans,
bots and all team sizes. Teams no longer respawn exclusively at a team anchor.

Select `spawn-area` in the Setup Book, or use an active setup session:

```text
/pulse setup region spawn-area <x1> <lowest-feet-y> <z1> <x2> <highest-head-y> <z2>
```

Choose only the playable volume, including two-block headroom at the highest floor.
Exclude roofs, sealed decorative rooms, spectator decks and the waiting lobby.
Safe random placement cannot establish whether an enclosed room connects to the
rest of the arena; builders must keep selected floors reachable. Keep four or more
reviewed spawn anchors as fallback positions. For an existing saved map, back up
and add only `regions.spawn-area` to its arena definition, preserving its other
fields/template; do not overwrite it with a blank setup session.

Without that region, sampling uses the horizontal envelope of existing anchors and
only their lowest-to-highest feet levels. This upgrades existing single-floor maps
without guessing their outer bounds; multilevel maps should define the region.
Sampling uses loaded chunks only, at most 1,024 floor checks, accepts full occluding
non-magma support with two air blocks, and avoids players within two blocks.
It randomly chooses among candidates at least eight blocks from opponents where
possible, otherwise prefers the farthest safe candidate. If sampling finds none,
it falls back to reviewed anchors. Keep those anchors safe. Thin floors such as
slabs/stairs and transparent support are not random-spawn surfaces in this pass.

Bots strafe around their weapon's preferred range, back away from close opponents,
and retreat at 40 HP or less. Railgun bots keep more distance than scattergun bots.
Evasion checks the route for clear headroom, solid ground and arena bounds before
requesting movement; blocked retreat holds position instead of charging. Citizens
still handles walking/pathfinding. Forward dash is reserved for healthy bots
closing a long distance, so aiming at an enemy does not reverse an intended retreat.

Acceptance: repeated opening/death/void spawns on both configured maps, solo and
teams, with humans and bots. Check all intended levels, separation, no roof/lobby
spawns, and fallback if chunks or sampled floors are unavailable. Verify close-range
and low-health retreat, lateral movement, walls/edges and both weapon ranges on
Java/Bedrock. Existing spawn protection and weapon cooldowns remain unchanged.

Activation prerequisite for Quake: keep `quake` in `games.preview` and retain the
existing test-account access gate. A binary being deployed does not enable that
preview setting. Reload configuration using `/pulse reload` in-game after a
configuration-only change.

## Prepared 0.3.15: selectable loadouts

Choose one weapon and two different abilities during a 15-second opening freeze.
The native inventory menu has one weapon row and separate rows for ability slots
1 and 2; selecting an ability already in the other slot swaps the slots. Ready
closes the menu; the shared opening timer still runs. Defaults are Railgun, Dash
and Jump. The compass reopens selection during spawn protection; firing or using
an ability ends that protection and locks selection. Selection survives respawns,
and reopening/changing choices never resets health or cooldowns.

| Weapon | Damage | Range | Fire cooldown |
|---|---|---|---|
| Railgun | 100 | 32 blocks | 1.2 seconds |
| Scattergun | 40 per ray, 3 rays | 12 blocks | 0.45 seconds |
| Pulse Blaster | 34 | 24 blocks | 0.35 seconds |

| Ability | Effect | Cooldown |
|---|---|---|
| Dash | Forward burst | 6 seconds |
| Jump | Upward burst | 8 seconds |
| Shield | Halves incoming Quake damage for 2 seconds | 12 seconds |
| Sprint | 1.5x walking speed for 3 seconds, capped | 10 seconds |

The blaster uses the same wall-limited server hit checks as the other weapons.
Shield is damage reduction, not invulnerability. Sprint restores prior walking
speed on expiry, leaving, death, elimination or cleanup. Ability effects and
cooldowns are validated against the equipped choices for bots and humans alike.
Bots independently choose a weapon and two distinct abilities and use the same
cooldowns; their visible-target commitment reduces constant target switching.
All UI/items/effects use native Minecraft content, so this does not add custom
resource-pack registrations or require a proxy update.

Test every weapon and ability pair, duplicate swapping, protection ending after
acting, cooldown retention across selection, sprint cleanup and shield expiry.
Human/Bedrock menu usability, weapon balance and bot movement still need play tests.
