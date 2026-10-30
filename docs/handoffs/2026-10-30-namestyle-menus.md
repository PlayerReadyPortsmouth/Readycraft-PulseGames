# Handoff: name style picker and staff role menu (30 Oct 2026)

Branch `feat/namestyle-menus`, draft PR against `main`. Versions not bumped. Nothing deployed.

## What changed
- Paper: `/namestyle` (`NameStyleCommand`, pure `NameStyleModel`), `/rolemenu` (`RoleMenuCommand`, pure `RoleMenuModel`), `ClickMenu` (small chest menu), `LuckPermsLink` (only LuckPerms API user), `BedrockForms` (only Floodgate/Cumulus user, guarded by `available()`), `MenuCommands` (registration; `PaperBridge` gets one line). Paper build gains `compileOnly` LuckPerms API 5.4 and Floodgate API (same as the proxy module).
- Common: `RoleMenuCommands` (composes and validates `/role` lines) and `ROLECMD` added to the `Wire` kinds.
- Velocity: one `case "ROLECMD"` line in `VelocityBridge` that runs the validated command as the carrier player.
- README sections "Name style picker" and "Role menu"; config keys `namestyle.enabled`, `rolemenu.enabled`.

## Deviation from the brief
A backend `player.performCommand("role ...")` never reaches the proxy (Velocity only intercepts commands the client sends), so a plain dispatch would say "unknown command". The menu therefore sends a signed `ROLECMD` message (same pattern as `PARTYCMD`) and the proxy executes it as the player. Gating, audit and confirm are unchanged. This touches `Wire`, `VelocityBridge` (one line) and needs the proxy upgraded first.

## Left out
- Tags (`pulse.tag`), the unlock grants (PulseGames/PulseAddon) and Survival/AdvancedChat.
- No "type a name" inside the menu: `/rolemenu <name>` covers offline players.
- Rate limit is per backend process (5 s per player), not network-wide.
- Not run on a real server, Java or Bedrock client; sync delay unmeasured.

## Rollout
Proxy jar first, then backends (restart gate: nobody online).

## Acceptance (Java)
1. Player with some `pulse.namestyle.*` nodes: `/namestyle` shows them first with their own name in each style; locked ones are grey panes; current glows. Click selects; chat message shows the new name.
2. `/namestyle set <locked id>` is refused; `/namestyle list`, `clear` work; two changes inside 5 s: second refused.
3. `lp user X meta info` shows `pulse.namestyle`; tab and chat match within a few seconds; `lp log recent` shows it.
4. Staff in `staff`: `/rolemenu` shows online heads; pick one, "Give Venue: Portsmouth": chat shows the proxy's reply, proxy log has the ROLE line naming the staff member. Staff giving `dev` is refused by the proxy text.
5. "Take away" asks yes/no, then removal completes without typing `/role confirm`. "View roles" prints `/role who`.
6. A non-staff player running `/rolemenu` is refused. `/rolemenu SomeOfflineName` works.
7. Items cannot be taken out of any menu.

## Acceptance (Bedrock)
1. `/namestyle` opens a form: unlocked buttons with a colour hint, "Clear my style", locked styles listed in the text; selection confirms in chat.
2. `/rolemenu` form lists players, then the action list; "Take away" shows a yes/no form first.
3. Names with a leading `.` (Floodgate prefix) work as targets.
4. Without the Floodgate plugin the chest menu is used instead.
