# Bedrock forms and live refresh handoff (29 Oct 2026)

GUI polish review steps 7 and 8.

## What changed
- **`core/ui/forms/`**: `FormLayout` (pure button order and routing rule), `Forms` (entry points, safe without Floodgate), `CumulusForms` (the only class touching Cumulus/Floodgate, loaded only inside a guard), `RefreshDiff` (pure).
- **Game list form** (`GameMenu.openGames`): Quick Play first, then each game with "N waiting, M playing" (or "Quiet right now"/"Coming soon!"), then party help, daily challenges, weekly top 3 and KitPvP loadout, so Bedrock loses nothing the chest has. **Mode picker form**: modes with counts, Back last. Counts are read when the form opens (forms are static).
- **`Forms.confirm(plugin, player, title, body, yesLabel, noLabel, onYes, onNo)`** returns false when no form was shown (Java player, switch off, Floodgate missing or failing); the caller then opens its chest confirm. Closing the form counts as "no". `core/ui/Confirm` is not created here.
- **Switch**: `ui.bedrock-forms` (default true). `ui.refresh-ticks` (default 40, minimum 10).
- **Live refresh (step 7)**: `GameMenu` starts one repeating task when the first chest list opens and cancels it when none is open. Each tick it compares the counts a list shows with what it last drew and calls `Menu.refresh()` (same window, only changed stacks) when they differ. Closed or replaced windows are dropped.
- Without Floodgate nothing changes: the plugin loads and everyone gets the chest.

## Tests
`ready-gate exec -- bash ./gradlew build test` passes. `FormsLogicTest`: button order (Quick Play first, Back last), target lookup, routing rule, refresh diff.

## Visual checks (MinigamesTest, not done yet)
Floodgate is installed on the backend. Use one Bedrock phone and one Java client.
1. Bedrock: compass opens a form, Quick Play is the top button, game buttons read "Spleef / 3 waiting, 0 playing", text fits on a phone. Tap a multi-mode game: mode form, Back returns to the list. Tap Quick Play, party, challenges, top 3 and KitPvP loadout.
2. Java: compass still opens the chest list.
3. Counts: with both open on the game list (chest on Java), have a third player or bot join a queue; the Java chest count and stack size change within about 2 seconds with no window flash or cursor jump. Repeat in a mode picker. The Bedrock form stays as opened; reopen to see the new count.
4. Set `ui.bedrock-forms: false`, restart: Bedrock gets the chest again.
5. Check `Forms.confirm` once the token shop uses it (no caller yet).
