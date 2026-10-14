# Quake arena shooter

Status: implemented for testing; not deployed or client-approved. No map is bundled or automatically published. Uses native items, sounds and particles, so no new resource pack is required.

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
