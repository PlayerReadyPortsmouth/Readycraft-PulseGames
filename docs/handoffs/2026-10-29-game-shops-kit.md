# Game shops on the UI kit, handoff 29 Oct 2026

Author: AurorasChaos (on behalf of Player Ready). Draft PR against `main`, from 0.5.0 (`bde70d5`).
Scope: step 6 of `ops/readycraft/pulsegames-gui-polish-review-2026-10-29.md`. Nothing was run on a server.

## What changed
- New `core/ui/shop/` on top of `core/ui/Menu`:
  - `ShopRules`: `afford` (exact shortfall), `needMore` text, tier chains (`tiered`, `ownedTier`, `lowerTiers`).
  - `ShopLayout`: tab row 0-8, quick row 9-16, page 27-44 (or spaced 28..40), hint 31, footer 49, Close 53.
  - `ShopTiles`: priced tile (`offer`), pick-one tile (`choice`), tab, info tile. Status is words first
    ("Need 3 more points", "You already have this", "Active now", "Selected"), colour second; names and lore are
    non-italic and wrapped at `Lore.WIDTH`.
  - `ShopFeedback`: one soft sound and one short sentence per result.
  - `TabbedShop`: the 54-slot tabbed window. A game supplies a `Source` (tabs, quick row, entries, footer, `live`).
    Clicks run next tick via `MenuListener`; a click re-renders the same window (no reopen); every click and drag
    is cancelled by the kit.
- Bedwars (`games/bedwars/shop/`): `BedwarsShopMenu` is now a `Source`; `ShopRules` delegates to core (same
  `Status`/`Verdict`, now the core types); `ShopCatalogue` tier maths delegates to core; `ShopLayout` removed.
  `BedwarsGame` no longer forwards inventory click/drag (the kit does). Catalogue, prices, purchase code, bots: unchanged.
  Only test edit: `ShopRulesTest` imports `Status` from core.
- ReadyPlayerZ: `ZShopMenu` + `ZShelf`. Tabs Weapons, Gear, Perks, Specials (Mystery Box / Pack-a-Punch, only when the
  map has no physical one, as before). Every click calls the existing `purchase(slot)`, so points, prices, bag-space
  check and Pack-a-Punch rules are unchanged. Tiles show affordability, "Need N more points" and a short plain
  description; perks the child already has show "Active now" and "Click to renew" (re-buying still works as it did).
  Footer shows points. `upgrades` is now the static `UPGRADES` (with a description and perk effect per line).
- Quake: `QuakeLoadoutMenu`. Tabs Weapon, First ability, Second ability; the chosen one shows "Selected" and glows;
  choosing an ability already in the other slot swaps them (as before) and says so. "Ready" closes. Loadout still
  only works during spawn protection and closes itself when it ends.

## Behaviour changes to know about
- RPZ and Quake shop windows are 54 slots with tabs instead of 27/36 flat chests. RPZ chat messages for a failed buy
  are now specific ("Need 40 more points", "Your bag is full").
- Bedwars item names, lore wording and sounds are slightly different (wrapped lore, quiet kit sounds); logic is the same.
- Menu sounds now come from the kit (`Theme.sound`), so they are quieter than before.

## Visual checks needed on MinigamesTest (one Java client, one Bedrock phone through Geyser)
1. RPZ round, Java: right-click the emerald. Tabs readable; names/lore upright and wrapped; with low points the tile
   says "Need N more points" in red; buy a sword (points drop, bag gets it, window stays put, cursor does not jump).
2. RPZ, Java: buy Speed Perk then reopen: "Active now" glint and "Click to renew"; Tough Skin and Regen likewise.
3. RPZ, Bedrock: the same round; tabs and the "Weapons" first tab fit the phone; tap targets (tabs row, page row) are
   easy to hit; text-only status readable without colour; no window flash on tab change.
4. RPZ: with no Mystery Box/Pack-a-Punch on the map the Specials tab appears and works (5000 points for Pack-a-Punch
   with a weapon in hand); with physical ones placed the tab is absent.
5. RPZ: shift-click, number keys, offhand swap and drag over the window do nothing; downed player's clicks do nothing.
6. Quake round, Java and Bedrock: compass during spawn protection opens the loadout; pick weapon, pick abilities,
   swap an ability with the other slot, press Ready; the hotbar updates; the window closes when protection ends.
7. Bedwars still works (regression): buy in all tabs, team upgrades tab, armour, tier replacement.

## Tests
`ShopKitTest` (affordability, tier replacement, layout, feedback text), `ZShopCatalogueTest` (every line on one shelf,
prices kept, perks have effects), `QuakeLoadoutCatalogueTest`; existing Bedwars `ShopCatalogueTest`, `ShopRulesTest`,
`BedwarsPurchaseTest` and the RPZ purchase tests stay green. Tile rendering needs a server (no ItemStack in unit tests).
