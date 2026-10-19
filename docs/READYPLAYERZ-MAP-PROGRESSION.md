# ReadyPlayerZ: doors, wall buys and unlocked spawn areas

Prepared implementation; no map edits or deployment are implied. Existing maps keep the emerald Mystery Shop. Doors are bought by walking up and sneaking (Java and Bedrock alike); wall buys use ordinary right-clicked map blocks. No new inventory interface or custom model is needed.

## Authoring contract

All coordinates refer to the captured template; effects occur only in its running cloned instance. Define a `bounds` region enclosing the playable map. Doors are consecutively numbered `z-door1` to `z-door16`, each a region of at most 512 blocks entirely inside bounds. The door is removed when purchased and opens for the entire team. Door regions must not overlap. Use supported inert blocks such as stone/stone bricks, cobblestone, deepslate variants, brick, quartz, obsidian, bedrock, barrier, solid iron/gold/diamond/netherite blocks, glass, coloured concrete or terracotta. The runtime checks the barrier before the match starts and again at purchase. Wooden/iron doors, gates, trapdoors, redstone devices, gravity blocks and burnable blocks are refused; closed-region interactions and piston movement are blocked. Additional safeguards: containers/block entities and liquids are rejected at purchase, as are empty doors, unloaded chunks and blocks outside the world border/build height.

Each door takes these settings:

| Key | Value |
| --- | --- |
| `z-door1-cost` | Integer points, 1–100000 (required) |
| `z-door1-round` | First purchasable round, 1–50; default 1 |
| `z-door1-name` | Hologram title, up to 24 characters; default `Door 1` |
| `z-door1-anchor` | Optional `x,y,z` block players may right-click instead of sneaking |

### Barrier flow (what players see)

At match start every door gets a floating text hologram at its centre showing the name, the price in points and either `Unlocked` or `Locked until round N`. The price is green when someone on the team can afford it, gold when nobody can yet, and red while locked by round. Within 3 blocks of a closed door, a player's action bar reads `Sneak to open – N pts` (or `need N pts` / `locked until round N`) and sneaking buys it. No anchor is needed: the door region itself is the target. If an anchor is set, right-clicking it within four blocks still works as a fallback. The hologram is removed when the door opens and at cleanup. Darkness pulses are suppressed (and any active one cleared) while a player is at a door prompt.

The sidebar shows `Doors: opened/total next <price>` (cheapest closed door). Bots walk to the cheapest unlocked door once one of them holds its price plus 75 points, and buy it by standing next to it.

Anchors, when used, must be inside bounds and unique across doors and wall buys. Another door must not remove a control. Players must be active, in the running instance and within three blocks of a door (four of an anchor); offhand duplicate events do not charge. An already-open team door does not charge again. Purchase deducts only that purchaser's points.

To withhold a zombie spawn until a team door opens, set `mobspawn2-door: 1`. This requires both `mobspawn2` and door 1 to exist. At least one mobspawn region must remain ungated so the first round is playable. Keep consecutive numbered mobspawn regions, as required by the normal arena validator. Spawn gating affects the next spawn selection; opening a door does not teleport already spawned enemies.

## Wall buys

Wall buys use consecutive settings groups `z-wall1` through `z-wall32`, without a region:

| Key | Value |
| --- | --- |
| `z-wall1-anchor` | Actual block to right-click, inside bounds |
| `z-wall1-type` | One supported offering below |
| `z-wall1-cost` | Integer points, 1–100000 |
| `z-wall1-round` | First purchasable round, default 1 |

Supported types: `stone-sword`, `iron-sword`, `diamond-sword`, `netherite-sword`, `bow`, `crossbow`, `trident`, `arrows`, `heal`, `speed`, `strength`, `resistance`.

Weapons occupy an empty storage slot; arrows grant exactly 16 into an empty slot. Full inventories refuse the purchase without replacing equipment or deducting points, even if an existing arrow stack has room. Heal restores current maximum health and refuses a full-health purchase. Perks apply level-one effects directly for two minutes and refuse purchase while that effect is already active; no potion bottles or free item stacks are created. These are ordinary Minecraft weapons, not custom firearms. Repeated weapon/ammo purchases cost points each time.

## Command-only example

Run in an existing ReadyPlayerZ setup session. Substitute real reviewed map coordinates; this example assumes bounds already exist and mobspawn1 is ungated.

```text
/pulse setup pos1 10 64 5
/pulse setup pos2 12 66 5
/pulse setup region z-door1
/pulse setup set z-door1-cost 500
/pulse setup set z-door1-round 2
/pulse setup set mobspawn2-door 1
/pulse setup setloc z-wall1-anchor 6 65 3
/pulse setup set z-wall1-type crossbow
/pulse setup set z-wall1-cost 300
/pulse setup set z-wall1-round 3
/pulse setup validate
```

The saved arena YAML contains region `z-door1` and flat keys under `settings`. Do not use dotted/nested setting names in these commands. The setter accepts the existing location format and numeric/string values. Capture/save through the normal reviewed-map workflow after validating; no wand is required for these coordinates.

## Validation and testing

Both setup validation and saved-map validation check the same progression schema. Unknown progression keys/types, missing or duplicate anchors, nonfinite coordinates, missing bounds/regions, invalid prices/rounds, nonconsecutive numbering, overlapping doors and undefined spawn gates fail closed. Configuration validation does not certify line of sight, reachable controls, safe pathfinding or readable signage.

Test purchases with insufficient points, full inventory, before/after the unlock round, and with two teammates clicking the same door. Confirm gated zombies do not spawn early and at least one starting area works. Verify cleanup restores removed block data in loaded empty door cells; instance disposal remains responsible for unloaded chunks. Check two concurrent copies of the same map independently. The source template and editing worlds must remain unchanged. Label controls with offering, price and unlock round using ordinary map signage; the code does not create decorative signs for builders.

## Darkness pulses

Waves no longer hold constant darkness. Each human player gets a short pulse (default 2.5 s) at a randomised time, 30–45 s apart (Frenzy: about 22–34 s). Settings: `darkness` (`false` turns it off), `darkness-interval` (minimum seconds, 10–120, default 30; the gap is randomised between 1x and 1.5x) and `darkness-duration` (seconds, 1–6, default 2.5). Bots are unaffected.
