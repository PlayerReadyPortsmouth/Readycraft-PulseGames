# Token shop and menu ports onto the UI kit, 29 October 2026

Status: draft PR from `origin/main` bde70d5 (0.5.0). Not played in a client. Plan: migration steps 3 and 4 of
`ops/readycraft/pulsegames-gui-polish-review-2026-10-29.md`.

## What changed

- `core/ui/Confirm` + pure `ConfirmLayout`: 27 slots, Yes 11 (orange when `danger`), summary 13, No 15. Both answers run next tick;
  Yes fires once only. Closing with Escape does nothing.
- `TokenShopMenu` (no longer a Listener) on `Menu`:
  - Balance in every view (main slot 4; categories next to Back).
  - Confirm for the 2x booster always, and for any unowned item priced at or above `shop.confirm-at` (new in `config.yml`, 500 = booster price).
    The confirm shows price, balance and balance after. A stale confirm never charges twice (`buy` re-checks ownership).
  - Equipped items glow. Equip/unequip/buy re-render the same window (no reopen, so the cursor stays put).
  - Category window sizes itself (2-6 rows, last row is navigation) and pages at 45 items, so the 27th item is no longer dropped.
  - Lore wraps at 30 and is non-italic; soft sounds via `Theme.Cue`. Prices read `⛀ N tokens` (the word is there in case Bedrock draws a box).
- Ported to `Menu` with behaviour kept: `InfoMenu` (still `InfoMenu.open(...)`, plugin found via `getProvidingPlugin`), `MatchResults`
  (result text is now escaped and wrapped), `KitSelectionMenu` (pages in place now), `DuelMenu` (titles are now `Pulse · Duels · ...`),
  and the spectator watch menu in `SpectatorTools` (same ordering, same Follow the leader slot, redraws in place when someone goes out).
- Removed the private holders, listeners and click/drag handlers they carried, including the watch-menu branches in `GameListener`
  and the five `registerEvents` lines in `PulseGamesPlugin`.
- `DuelMenuSafetyTest`: kept the page-clamp test; dropped the stale-click test because it exercised the removed private holder
  (the same rule is covered by `MenuKitTest`).

## Not done

- `QuakeGame` loadout menu still reopens inside its click handler (`open(p)` after weapon/ability pick). It is a hand-rolled holder
  with raw slot maths, not a few lines, and Quake is being edited by the shop builder. Next builder: move it onto `Menu` and use `refresh()`.

## Visual checks (MinigamesTest, one Java client, one Bedrock phone via Geyser)

1. Rapid double-click on a buy: Yes in the confirm, and a plain cosmetic below the threshold. Tokens drop once, one "Unlocked" message.
   Also double-click the booster Yes. Try `shop.confirm-at: 1` to confirm everything asks.
2. Cursor: equip and unequip repeatedly in a category; the Java cursor must not jump to the centre and nothing flashes.
3. `⛀` on Bedrock: balance item name, price lines and confirm summary. The word "tokens" follows, so a box is cosmetic only.
4. Paging: temporarily register more than 45 cosmetics in one category (or lower `perPage`) and check the pager, page indicator and last page.
5. Equipped item glows, owned shows "Yours", unaffordable shows "Need N more tokens", sounds are soft.
6. Cancel paths: No, Escape, Back from a category, balance updates after buying in the main view.
7. Info (challenges, top 3), match results (Play again / Change game / Return), kit picker paging, Duels screens and Kits paging,
   spectator watch menu (order, Follow the leader, a player dying while it is open) all behave as before; shift-click and number keys do nothing.
