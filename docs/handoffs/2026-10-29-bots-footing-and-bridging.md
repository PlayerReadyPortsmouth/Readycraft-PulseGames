# Bots: footing, island hopping, sidestep (29 Oct 2026)

Fixes "the Bedwars and Skywars bots never bridge, attack or buy". Root cause (ops diagnosis 29 Oct): bots would only
stand on a hard-coded list of materials, so they could not stand on the iron/gold generator pads, cut copper rims,
calcite or smooth basalt. No currency, no wool, no bridge, no fight.

## What changed
1. **Footing by shape.** `core/bot/BotFloorSafety`: the block's collision shape decides (a box spanning the cell
   reaching the top). Without a shape (unit tests, mocks) the material is judged by exclusion: not a hazard and not a
   partial shape (stairs, slabs, fences, doors, carpets, chests...). Hazards: lava, fire, magma, cactus, campfires,
   powder snow, pointed dripstone, sweet berries, wither rose, cobweb, TNT. Used by `SkywarsBotTerrain` (both games)
   and `BotNavigationHints`.
2. **Island hopping.** `BedwarsHopPlanner` (pure): cheapest route of bridges over other teams' bases and diamond
   islands, each hop at most `bedwars.bots.bridge.max-gap` void cells (hard limit). `BedwarsGame.hopGoal` walks it.
   Before leaving base a bot buys wool (16 for 4 iron) until it holds the whole route plus 25% (cap 128); it leaves
   with at least the first hop plus 25%. Short of blocks away from base: back to its generator ("Collecting resources
   for the next bridge"). No route fits: it defends/gathers at its generator ("No bridge short enough, defending
   instead"). `bridge.place-ticks` and `bridge.sneak` are unchanged.
3. **Sidestep.** `core/bot/BotSidestep`: when the next cell holds a solid obstacle (not a gap) the bot steps to the
   free side cell and remembers the side for 3 s. A one-block-high obstacle is still jumped first (existing traversal).
   Bedwars and Skywars.
4. **Aggression.** No change needed: a bridged bot lands on the enemy island, where enemies are inside the existing
   24-block, line-of-sight target range, and BotMelee already works at that range. No new config key.
5. **Skywars.** `SkywarsBotOrder`: chests first, then bridge only if the bot holds blocks, else stay and hunt on its
   own island. `blockSlot` now falls back to any solid full block (not sand/gravel/ore/leaves) when there are no planks,
   cobble, end stone or wool.

## Not done
- "Top up at the next island's floor": islands have no shop, so a bot that runs short goes home and buys instead.
- `BedwarsBridge.shortestGap` is still unused: the planner routes over `gap` itself (it needs whole routes, not one hop).
- Arena yml untouched (selene_fracture stays as configured).

## In-game acceptance (Java and Bedrock look the same; bots have no menu)
Start each with 2 bots per side and one human watching. Then `/pulse bots inspect`.
- **nimbus_mills (Bedwars):** within 20 s every bot stands ON the iron/gold pad and the iron count rises. Within
  about 60 s a bot buys wool. Expect a hop via the diamond island if the straight gap is over `max-gap`. Bots reach
  an enemy island, break the bed defence and fight. Status never sticks on "Waiting for materials or a safe route".
- **foundry_crown (Bedwars):** bots cross the cut copper rim without stopping at the edge; the 5x5 pad works; the
  yellow concrete cube at (31,101,45) is walked round, not stood at.
- **selene_fracture (Skywars):** bots walk on calcite and smooth basalt, loot their chests, then bridge to a rival.
  A bot with no planks or stone bridges with any other block it picked up; with none it paces and fights on its island.
- **aurora_sanctum (Skywars):** bots go round the quartz pillars and chests instead of freezing; loot comes before bridging.
- Regression: no bot steps onto magma, a campfire or cactus; no bot stands on a stair edge or open trapdoor.
