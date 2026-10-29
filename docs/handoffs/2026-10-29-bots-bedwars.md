# Handoff: Bedwars bots (29 Oct 2026)

Branch `feat/bots-bedwars`, from `origin/main` at 693fca0. Review: `ops/readycraft/pulsegames-bot-quality-review-2026-10-29.md` section 3.3, items 1-6 and 8. Plugin version not bumped.

## What changed

1. **Pacing** (`core/bot/BotPace`, `BotService.navigate`, `SkywarsBotTerrain.run`). For Bedwars and Skywars a bot may re-issue its next step as soon as it has arrived (floor of one 5-tick think) instead of waiting `pathTicks + jitter`. Bedwars aims up to `pace.look-ahead-cells` cells ahead, each hop proven by `corridor`, and only recentres across its walking line, never along it. `BotPaceTest` simulates the gating: old policy under 1.2 blocks per 20 ticks, new above 3.5 (walking speed is 4.3). This confirms the inference about the gating, not a live measurement.
2. **Bridging** (`BedwarsBridge`, `BedwarsGame.moveToward`). Four-connected staircase along the line from where bridging began, height one block at a time, block placed every `bridge.place-ticks` while moving, sneaking when `bridge.sneak`. `BedwarsBridge.shortestGap` picks the island with the fewest void cells; used when choosing which bed to attack. Not done: choosing the best launch point on the home island (the line starts where the bot stands).
3. **Resource loop**. Attackers return to the team generator when they can afford something, top up on a timer when low, and from `roles.mid-game-seconds` take a diamond trip to the nearest `diamond-generators` point. There are no emerald generators in this game, so "emerald islands" does not exist; diamonds only.
4. **Shop** (`BedwarsShopPlan`). Priority table over `bedwars-shop.yml` ids, saving a currency when the first wanted item in it is unaffordable. Bots now buy pickaxe, shears, fireball, ender pearl, golden apple, water bucket, stronger blocks (guard), armour tiers to diamond, and team upgrades. Spending happens within `resources.spend-radius` of the generator (wool excepted). **I added `fireball` (iron 40) and `ender_pearl` (4 diamonds) to the bundled `bedwars-shop.yml`, and right-clicking a fire charge now launches a fireball for humans too.** The server's own copy of `bedwars-shop.yml` is copied on first run, so bots skip those two until the entries are added there. TNT is not bought or used by bots (nothing in the game primes it).
5. **Bed defence**. Inner ring first, then up to `defence.layers` rings, rebuilt when broken; the guard holds the generator/bed; any enemy within `defence.alert-radius` of the bed sends the guard, and teammates within 24 blocks, to it; guards build with the best block they hold.
6. **Bed attack**. `chooseEnemyBed` scores weighted cover, bridge gap, distance and teammate claims; re-chosen every `attack.reassess-seconds`. Pickaxe, axe or shears is held for the block being mined (`BedwarsBotTools`). Fireball (3-9 blocks, never when the target or the bot is near its own bed) and ender pearl escape (at or below 6 health) are used from `botCombat`.
8. **Team awareness** (`BedwarsBotTeams`). Shared per-team roles (one guard, rest gather then attack), targets and last threat.

Melee is unchanged: `botCombat` has a `TODO(BotMelee)` and still falls through to the shared bow and the generic melee in `BotService`. The releaser wires `BotMelee` in the bundle.

## Config

`bedwars.bots.*` in `config.yml`, all commented, all optional.

## Verify in game

- Time a Normal bot over 20 flat blocks: expect about 5 s, not 20 s or more.
- Watch a bridge-rush: staircase on diagonals, placing as it walks, no stand-still gaps.
- Attack a bed with a bot guard: it should rebuild, run to the bed, and not all bots should choose the same bed.
- No bot stuck on an island: watch `Waiting for materials or a safe route` in the bot diagnostics.
- Fireball beside a bed: confirm a bot does not blow up its own bed.

## Risks

- `run` sends Citizens a straight-line target up to three cells away. Each hop is checked, but a block placed or broken between thinks is only seen at the next think.
- Bridge placement uses `IslandBotPlacement`, which needs line of sight to the support block; slopes and corners may stall a bridge (status text says so).
