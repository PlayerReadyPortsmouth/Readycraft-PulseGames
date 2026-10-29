# Bedwars shop rework, handoff 29 Oct 2026

Author: AurorasChaos (on behalf of Player Ready). Draft PR against `main`, from 0.4.3 (`16e8eed`).
Scope: steps 5a, 5b, 5c and Part 2 idea 5 of `ops/readycraft/pulsegames-gui-polish-review-2026-10-29.md`.

## What changed
- `games/bedwars/shop/`: `ShopCatalogue` (data model + YAML parser), `ShopRules` (affordability,
  tiers, armour sets), `BedwarsShopMenu` (54-slot plain Bukkit chest), `ShopLayout`, `ShopBackend`.
  Nothing depends on `core/ui/`; the menu can be ported to the kit later.
- `src/main/resources/bedwars-shop.yml`: the catalogue. On a server it is copied to
  `plugins/PulseGames/bedwars-shop.yml` and re-read at the start of each match. A bad file logs a
  warning and the built-in copy is used.
- Menu safety: every click type and drag cancelled, owner check, actions next tick with stale-view
  check, in-place re-render (no reopen). `onInventoryDrag` now cancels for the shop.
- Armour bought is worn at once (chestplate, leggings, boots) and re-worn after respawn.
  Swords, pickaxes and axes are tier chains: buying a higher tier replaces the lower one in the
  same all-or-nothing purchase; a lower or same tier shows "You already have ...".
- Every item has a non-italic name and lore. Status is text first: "Click to buy" (green) or
  "Need 3 more iron" (red), also in the name; grey for owned.
- Team upgrades (existing concept) are now tiered: Sharp Swords I, Strong Armour I-II, Maniac Miner I-II,
  diamond costs in the YAML. Arena setting `team-upgrades: false` hides the tab (default on).
- Sidebar: "Next upgrade: 2:10" and "Diamonds in 0:14" (`BedwarsTimers`). On maps with more than
  6 teams it is one combined line so team lines keep their room (engine caps at 15 lines).
- Bots: they now call the same purchase code directly instead of opening the menu and firing a
  fake click. Same picks as before.

## Behaviour changes to know about
- Pickaxe: Wooden 10 iron, Iron 25 iron (unchanged), Diamond 12 gold (new). Axes and diamond armour
  are new. Potions: Speed and Jump, 2 gold each. Water bucket 4 gold. All tunable in the YAML.
- Old team upgrades were one level at 8/8/4 diamonds; protection and haste now have a second level.
- Shop item names changed ("use to open" wording on the emerald).

## Needs a visual check (not done: nothing was run on a server)
A real 2-team match on MinigamesTest with bots off, one Java client and one Bedrock phone:
1. Italics gone, tooltips short, no vanilla attribute lines on swords and armour.
2. Tabs, quick-buy row, selected-tab glint, balance and Close all readable on a phone.
3. Tapping around quickly: no ghost window, no cursor jump, no item can be dragged out or shift-clicked.
4. Buy armour: worn immediately, survives death and respawn, protection upgrade enchants it.
5. Buy swords/pickaxe/axe up the chain: lower tier disappears; full bag gives the "bag is full" message.
6. Team upgrade tab: tiers, diamonds charged per level, teammates updated, tab hidden with `team-upgrades: false`.
7. Sidebar timers tick, switch at 5:00 and 10:00, and a 6+ team map still shows team lines.
8. Wool shows the team colour; potions give the right effect.
9. Glyph check on Bedrock: the shop uses plain words only (the sidebar's existing tick/cross are unchanged).

## Tests
`BedwarsPurchaseTest` (replacement, charge), `ShopCatalogueTest`, `ShopRulesTest`, `BedwarsTimersTest`.
The menu itself and the BedwarsGame wiring are untested by unit tests.
