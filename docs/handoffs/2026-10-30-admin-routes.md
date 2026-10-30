# /admin routes (command standard stream A, 30 Oct 2026)

Branch `feat/admin-routes` from `origin/main` 59bce23 (PulseNetwork 0.2.4). Draft; no version bump, nothing deployed, not run in game. Touches only `network-helper/`.

## What
- `/admin <area> [args]` is the single Paper entry. Areas: `players roles nick styles identity tab whitelist announce games survival hunt server`. Bare `/admin` is the menu. Reference table: `network-helper/README.md`, section "Admin".
- `AdminHubModel.Area` replaces the old `Section` enum. `visible(env, may, minGroups)` gives the areas a viewer sees; `resolve` answers hidden and unknown areas identically ("Unknown admin area").
- Min group per area from `admin.areas.<id>.min-group`. D2 (Aurora, 30 Oct): `games survival hunt server` are staff; `styles tab announce` stay admin; whitelist remove/on/off stay admin.
- Owner areas run the owner's hidden route command through `AdminRoutes` (constants class): `pulsegames:admin-route games|server`, `pulseaddon:admin-route survival`, `pumpkinhunt:admin-route hunt`, with the rest of the line passed through. An area is hidden when its plugin is not enabled or does not register `admin-route` (`Env.routeAvailable`). `/admin server` picks the first owner present (games, survival, hunt).
- Fixes the hub's Pumpkin Hunt button (was `pumpkins admin`; now area `hunt`).
- `/admin roles` calls `RoleMenuCommand.open` directly (no `rolemenu` dispatch). `/adminmenu`, `/am`, `/rolemenu` are hidden aliases (removed from the client command list by `MenuCommands.HiddenAliases`) and print a hint.
- Tab-complete for areas, whitelist verbs (by gate), player names, `nick ... reset`, `tab hide|show`.
- Velocity unchanged: `/role /identity /announce /nick` stay (D3).

## Tests (`AdminHubModelTest`)
Every area resolves; owner areas hidden when plugin or route absent; gating per group and config override; namespaced route lines; no `performCommand`/`dispatchCommand`/`run_command` string literal outside `AdminRoutes`; tab-complete; alias hints.

## Not done / check in game
- Depends on the owner PRs for the route commands: PulseGames #96 (`games`, `server`), PulseAddon #82 (`survival`), PumpkinHunt #94 (`hunt`). Until they ship, those areas are hidden.
- Staff need LuckPerms nodes `pulsegames.admin`, `pulseaddon.admin`/`pulse.admin`, `pumpkinhunt.admin` (coordinator is granting them) or the owner refuses. The hub does not add the `pulsenetwork.admin` node synonym from the standard; group membership only.
- The `pulsenetwork:`-prefixed alias forms are also hidden. Alias precedence and the namespaced `performCommand` dispatch are untested on a live server.
- Whitelist by text uses the hub's usual gates (add/list/status staff, remove/on/off admin). `/admin tab refresh` and `/admin identity refresh` are not routed (no wire kind for them).
- Remove the three aliases next release.
