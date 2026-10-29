# Bedwars shop: full standard kit, team upgrades and funky custom items, handoff 29 Oct 2026

Author: AurorasChaos (on behalf of Player Ready). Draft PR against `main`, based on `42775fa` (bundle 0.7.0 merged).
Aurora, 29 Oct: "can we expand what's buyable in the bedwars shop, maybe some funky custom items". Plugin version not bumped.

## What changed
- `src/main/resources/bedwars-shop.yml`: the catalogue, now 45 items and 8 team upgrades. New optional keys per item:
  `custom` (behaviour id), `new` (lore says NEW), `cooldown` (seconds), `seconds` (effect or object lifetime).
  A `custom` id the code does not know is rejected at load, so a typo cannot ship a dead item.
- `games/bedwars/BedwarsCustomItems` implements every `custom:` behaviour. Items are plain vanilla materials with a
  name, lore and a hidden tag, so Bedrock (Geyser) sees them properly; no model data, no resource pack.
  Helpers: `BedwarsKitShapes` (pure block shapes), `TempBlocks` (things that revert), `Cooldowns`, `ForgeRates`.
- `BedwarsGame` forwards the hooks (interact, block placed, projectile hit, move, damage, once-a-second tick, end, cleanup),
  runs forge, heal pool and traps, and primes TNT on placement.
- Shop UI: 8 shelves plus Team Upgrades fill the 9-slot tab row, so the placeholder Quick Buy tab is gone (the quick-buy
  row is always on screen anyway). Spaced pages (Team Upgrades) now hold 9 entries (was 7), `ShopLayout.SPACED_CAPACITY`.
- Bedrock gets the same window through the existing menu kit path; nothing Bedwars-specific was added for forms.
- `shop.confirm-at` (config.yml, default 500) is honoured: an item priced at or above it asks "are you sure?" first.
  No Bedwars item reaches 500 by default, so nobody sees it unless an admin lowers the value.
- Bots: `BedwarsShopPlan` buys a bridge egg (attackers, after 60 s), a pop-up tower (non-gatherers, after 120 s) and a Speed II
  potion (attackers, after 45 s), once each. `BedwarsBotItems.choose` can drink the potion when a fight starts, raise the
  tower when hurt and close, and throw the egg at range 8-20. Existing choices keep priority.

## Deploy note
`bedwars-shop.yml` is copied to `plugins/PulseGames/bedwars-shop.yml` on first run only. The live server already has the old
file, so the deploy must copy the new catalogue over it (or delete the old one before the first match). Until then the old
shop is used and none of this appears.

## Items
| Item | Cost | Tab | Effect |
|---|---|---|---|
| Wool, Wood Planks, End Stone | existing | Blocks | unchanged |
| Obsidian x4 | 4 diamond | Blocks | toughest block |
| Ladders x16 | 8 iron | Blocks | climbing |
| Knockback Stick | 5 gold | Melee | stick with Knockback I |
| Speed II Potion | 2 gold | Potions | 45 s (unchanged) |
| Jump V Potion | 2 gold | Potions | 30 s (was Jump IV, 45 s) |
| Invisibility Potion | 6 gold | Potions | 30 s; vanilla hides worn armour too (enchant glint from Strong Armour may still shimmer) |
| Magic Milk | 4 gold | Potions | right-click: team traps ignore you for 30 s |
| TNT | 8 gold (existing) | Utility | primes on placement, 3 s fuse; never breaks a bed (0.7.0 guard) |
| Water Bucket | 4 gold (existing) | Utility | |
| Sponge | 3 gold | Utility | soaks water |
| Bridge Egg | 6 gold | Utility | wool bridge in your team colour laid behind the egg, max 40 blocks, 2 s cooldown |
| Pop-up Tower | 24 iron | Utility | hollow 3x3 wool tower, 5 high, doorway and ladder, placed in front; only into empty space, never in a bed region; 5 s cooldown |
| Dream Defender | 120 iron | Utility | iron golem for the team, 4 min, one per team, attack damage lowered to 4; 30 s cooldown |
| Bed Bug | 30 iron | Utility | thrown; silverfish hatches and hunts enemies for 15 s; 4 s cooldown |
| Grappling Hook (NEW) | 10 gold | Funky | fishing rod: cast, then reel to be pulled to the hook; 5 s cooldown; unbreakable |
| Bounce Pad (NEW) | 24 iron | Funky | placed slime block launches whoever steps on it; 60 s; 3 s cooldown |
| Ice Road (NEW) | 4 gold | Funky | 8 packed-ice blocks the way you face, melts after 45 s; 8 s cooldown |
| Smoke Bomb (NEW) | 5 gold | Funky | thrown; 5 s cloud, Blindness I for enemies within 3 blocks; 10 s cooldown |
| Freeze Ball (NEW) | 3 gold | Funky | snowball; Slowness II for 3 s on an enemy hit; 6 s cooldown |
| Rocket Boots (NEW) | 12 gold | Funky | vertical launch plus Slow Falling 5 s, 3 uses (shown in the name); 10 s cooldown |
| Recall Totem (NEW) | 2 diamond | Funky | 5 s channel, then teleport above your bed if it still stands; any damage cancels; 30 s cooldown |
| Shield Wall (NEW) | 8 gold | Funky | 5x3 wall of team wool two blocks ahead, 30 s; 15 s cooldown |
| Honey Trap (NEW) | 6 gold | Funky | right-click ground: 3x3 honey patch, enemies slowed, 60 s, ground restored; 15 s cooldown |
| Decoy Dummy (NEW) | 32 iron | Funky | armour stand in team colours, soaks 5 hits, 30 s; 20 s cooldown |

Currencies on this server are iron, gold and diamond; there is no emerald, so the dearest items use diamonds.

| Team upgrade (diamonds per level) | Levels |
|---|---|
| Sharp Swords | 8 |
| Strong Armour (Protection I-IV) | 5, 10, 20, 30 (was 8, 16) |
| Maniac Miner (Haste I-II) | 4, 8 |
| Iron Forge (I-IV) | 4, 8, 12, 16; each level adds half as much again to iron and gold at the team generator |
| Heal Pool | 3; Regeneration I near your bed (14 blocks) |
| Alarm Trap | 1; first enemy within 8 blocks of your bed glows 10 s and your team is warned |
| Slowness Trap | 2; Slowness I for 8 s |
| Tired Hands Trap | 2; Mining Fatigue I for 10 s |

Traps work once, then can be bought again. Buying one tells only the team. Magic Milk skips them.

## Cleanup
`BedwarsCustomItems.clear()` runs from `onEnd` and `onCleanup`: reverts every temporary block, removes defenders, bugs and
decoys, cancels bridge, smoke and recall tasks and forgets cooldowns. Game-level tasks are also cancelled by the engine on cleanup.

## Left out, on purpose
- Bots do not target Decoy Dummies or use the funky items: the shared bot targeting only considers players (`BotService`),
  and changing it is a core-bot change. Players can still be fooled into hitting it.
- No emerald currency and no custom models.
- Drinkable potions leave an empty bottle (vanilla). Bridge Egg can still hatch a chicken now and then (vanilla egg).
- Bots do not use Dream Defender, Bed Bug or the Funky shelf.

## Tests
24 new tests (see PR). Suite: 1083 tests, 0 failures. Nothing was run on a server.

## Needs a visual check (not done)
Two-team match on MinigamesTest, one Java client and one Bedrock phone, bots on for part of it:
1. Funky tab shows 10 items with NEW in the lore; Team Upgrades tab shows 8 spaced entries; all 9 tabs readable on a phone.
2. TNT primes at once, 3 s fuse, a bed beside it survives. Water bucket, ladders, obsidian, end stone, sponge buy and place.
3. Bridge Egg lays team wool and stops at the end; Pop-up Tower beside a bed/chest does not overwrite them; ladder climbs.
4. Magic Milk then walk into a trapped base: no trap. Without it the trap fires once and the team is told.
5. Invisibility hides body and armour (Java and Bedrock). Jump V 30 s, Speed II 45 s.
6. Knockback Stick shoves; Sponge soaks; Bed Bug hatches a silverfish for 15 s; Dream Defender appears, fights, vanishes at 4 min, drops nothing.
7. Grappling Hook: cast, reel, pulled in, cooldown message; Bounce Pad launches Java and Bedrock players, gone at 60 s.
8. Ice Road appears and melts; Smoke Bomb cloud blinds enemies only; Freeze Ball slows on hit.
9. Rocket Boots show "(2 left)" then "(1 left)" then vanish; Recall Totem channels 5 s, cancelled by a hit, works with the bed, fizzles without.
10. Shield Wall and Honey Trap appear and revert; Decoy Dummy wears the team wool and pops after 5 hits.
11. Forge levels visibly speed the team generator; Heal Pool heals near the bed.
12. End a match mid-effect: nothing left behind in the next match (world is deleted, but check no errors in the log).
