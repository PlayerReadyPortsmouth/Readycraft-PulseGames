# Bedwars bots: buy and use the full shop, decoys and defenders, handoff 29 Oct 2026

Author: AurorasChaos (on behalf of Player Ready). Draft PR against `main`, based on `60ea026` (0.8.0). Plugin version not bumped.
Aurora, 29 Oct: "let the bots use those items as well".

## What changed
- `BedwarsShopPlan`: gadget rules added after armour, weapons and tools, each leaving a currency reserve (iron 24, gold 6,
  diamond 4; Dream Defender 30 iron, Knockback Stick 8 gold). A gadget the bot cannot afford never makes it "save", so it
  cannot block armour or tools. Team upgrades follow the role. `Loadout` gained `tier` and `potions` (old constructor kept).
- `BedwarsBotFunky` (new, pure): the per-item tier table and the use rules. `BedwarsBotDecoys` (new, pure): decoy fall-for timer.
- `BedwarsCustomItems.botUse`: now goes to the same path a child uses. The item moves to the hand, the bot faces the aim point
  and a real `PlayerInteractEvent` is fired (a `BlockPlaceEvent` for the Bounce Pad), so `onInteract`, cooldowns, the engine
  rules and protection plugins apply unchanged. Bridge egg and pop-up tower use it too. New helpers `botReady`, `foesOf`,
  `defenderActive`.
- `BedwarsGame`: wires purchase tier, combat gadgets (`useGadget`), between-fight gadgets (`travelGadget`), `botFoe`, and the alarm.
- `BotService` (core): two Bedwars-only edits. A foe from `BedwarsGame.botFoe` joins target selection when closer than the
  best player; and a bot holding the Knockback Stick is not switched back to a sword by the melee code. `selectSword` is now public.
- `BedwarsBotPlan` / `BedwarsBotTeams`: our own Alarm Trap sets a 20 s team alarm; every bot not already fighting heads for the bed.

## Purchase table (tier 0 Easy, 1 Normal, 2 Hard; seconds are game time; reserve is left behind)
| Item | Roles | From | Tier | Condition |
|---|---|---|---|---|
| Knockback Stick (5 gold) | guard, attack | 60 s | 0 | reserve 8 gold |
| Shield Wall (8 gold) | guard, attack | 150 s | 0 | none held |
| Honey Trap (6 gold) | guard | 120 s | 0 | none held |
| Bounce Pad (24 iron) | guard | 180 s | 1 | none held |
| Dream Defender (120 iron) | guard | 240 s | 2 | reserve 30 iron, none held |
| Decoy Dummy (32 iron) | guard, attack | 300 s | 2 | none held |
| Grappling Hook (10 gold) | attack | 90 s | 1 | |
| Ice Road (4 gold) | attack | 90 s | 1 | none held |
| Smoke Bomb (5 gold) | attack | 120 s | 0 | none held |
| Bed Bug (30 iron) | attack | 150 s | 1 | none held |
| Freeze Ball (3 gold) | guard, attack | 90 s | 0 | fewer than 2 held |
| Rocket Boots (12 gold) | attack | 180 s | 1 | none held |
| Invisibility (6 gold) | attack | 240 s | 1 | no invisibility potion held |
| Magic Milk (4 gold) | gather, attack | 120 s | 0 | none held |
| Recall Totem (2 diamond) | gather, attack | 200 s | 1 | reserve 4 diamonds; bought last |

Team upgrades: guard Protection, Forge, Alarm trap, Slowness trap, Heal Pool, Sharpness, Haste; attacker Sharpness, Protection,
Haste, Forge; gatherer Protection, Sharpness, Forge, Haste. Traps need tier 1 and 90 s. Existing armour/sword/tool rules are unchanged and first.

## Use rules (chance per roll, Easy / Normal / Hard; one roll per 60 / 30 / 15 ticks, only when a rule's conditions hold)
| Item | Rule | Chance |
|---|---|---|
| Freeze Ball | enemy 6-12 blocks away | 25 / 50 / 80 |
| Smoke Bomb | enemy 6-12 blocks away (after Freeze Ball) | 15 / 40 / 75 |
| Knockback Stick | enemy within 3.5 and next to a drop; sword restored afterwards | 40 / 70 / 100 |
| Shield Wall | health 50% or less, enemy within 8 | 30 / 60 / 90 |
| Rocket Boots | health 6 or less, enemy in view and bot boxed in on 2+ sides | 0 / 50 / 85 |
| Bed Bug | enemy 5-16 away and within 12 of its own team's bed | 0 / 40 / 80 |
| Grappling Hook | enemy 10-24 away (chase), or a 3-18 block gap ahead on the way to the target | 0 / 35 / 70 |
| Ice Road | a 2-8 block gap ahead, floor beyond | 0 / 40 / 70 |
| Recall Totem | health 8 or less, 40+ blocks from base, no enemy within 8, bed alive | 0 / 60 / 90 |
| Dream Defender | near own bed, bed threatened, none active | 0 / 0 / 70 |
| Honey Trap | within 7 blocks of the approach cell (5 blocks from own bed towards the nearest enemy bed) | 30 / 60 / 90 |
| Bounce Pad | same cell | 0 / 50 / 80 |
| Decoy Dummy | same cell | 0 / 0 / 60 |
| Magic Milk | attacker 9-22 blocks from an enemy bed, not already immune | 30 / 70 / 100 |
| Invisibility | same moment, after Milk | 0 / 40 / 80 |

An item on cooldown is not in the ready set (`botReady`) and `botUse` refuses it before touching the inventory.

## Targeting and traps
- `BotService` asks `BedwarsGame.botFoe` only when the game logic is Bedwars and not a co-op objectives game. It returns the
  nearest visible enemy-team Decoy Dummy the bot has fallen for, or a Dream Defender / Bed Bug whose target is this bot.
- Decoy: each bot decides once per decoy, on first sight. Fall-for 70 / 40 / 10 percent; a bot that falls for it chases for
  6 / 4 / 3 seconds, then ignores that decoy for good. A decoy is only fought in melee (no items, no arrows).
- Traps: bots already receive team traps like humans (`guardBeds` loops every living non-team player, bots included; Magic
  Milk skips them as for humans), so no change was needed there. Own alarm: see above.

## Tests
1105 tests (1092 before, +13): `BedwarsBotGadgetsTest` (purchase per role/phase/budget/tier, reserve, upgrades, use table,
tier chances with fixed dice, cooldown, decoy timer, foes, alarm) and `BotServiceBedwarsFoesTest` (decoy attacked in Bedwars,
nothing but players targeted elsewhere).

## Left out, on purpose
- Bots do not buy or use Obsidian, Ladders, Sponge, TNT, Jump potion (not in the brief's role lists). Water Bucket is unchanged.
- Grappling Hook: the bot does not cast a hook; it applies the same pull and cooldown (`BedwarsCustomItems.pull`) toward the landing point.
- "Bridge approach" is approximated as the ground cell five blocks out from the bed towards the nearest living enemy bed.
- Rocket Boots "cornered" means walls on two or more sides; there is no pathfinding check for escape routes.
- Bed Bug "near their bed" is read as the enemy being near its own team's bed.
- Citizens may re-aim a bot on the next tick; item use sets the rotation and fires in the same tick, so this should not matter.

## In-game acceptance
1. Start a bot Bedwars match (Easy, Normal, Hard in turn) and watch the shop: no bot ever ends at zero iron/gold after a gadget; armour first.
2. Hard guard: honey, bounce pad and decoy appear on the approach to its bed; a Dream Defender appears when it has 150+ iron and the bed is threatened.
3. Hard attacker: Freeze Ball and Smoke Bomb fly at 6-12 blocks; Magic Milk before an enemy island, then walk through its traps with no effect.
4. Ice Road or Grappling Hook used at a short gap; Rocket Boots at low health in a corner; Recall Totem far from base at low health.
5. Place a Decoy Dummy as a human: an Easy bot hits it for about 6 seconds then ignores it; a Hard bot mostly ignores it.
6. A Dream Defender or Bed Bug hunting a bot: the bot fights it.
7. Trigger a team Alarm Trap: every bot on that team returns to the bed.
8. Every other game (Skywars, Duels, Spleef) with bots: unchanged targeting.
9. Check that no bot repeats the same gadget faster than its cooldown and that match cleanup leaves no decoys, golems or bugs.
