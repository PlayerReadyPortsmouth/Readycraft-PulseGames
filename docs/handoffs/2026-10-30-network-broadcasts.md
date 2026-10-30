# Network presence lines and announcements (30 Oct 2026)

Branch `feat/network-broadcasts`, draft PR. Versions are not bumped; not deployed. Full key reference: `network-helper/README.md`, section "Presence and announcements".

## What changed

- Proxy (Velocity) sends one styled line for join, hop and leave, with a per-player hop cooldown, a minimum stay before a leave line, quiet rejoins, hidden-player silence and a once-a-day "welcome back" to the player only.
- Paper `presence.suppress-local` (default true) blanks the vanilla join and quit lines.
- `/announce [-title|-bar] <message>` on the proxy, signed `ANNOUNCE` wire kind, and `PaperBridge.announce(String text, String style)` for plugins.

## Rollout notes

- Needs the proxy and each Paper backend updated together. If the proxy updates first, players see both lines until backends update; if a backend updates first, its join lines vanish with nothing replacing them.
- Set `presence.suppress-local: false` on any backend whose proxy runs with `presence.enabled=false`.
- `presence.hidden-roles` is empty by default. Hidden staff already covered by `tab.hidden-players` or `pulse.tab.hidden` are silent without it.

## Acceptance list (Java and Bedrock)

Use two ordinary accounts, one staff account and one hidden account. For Bedrock repeat with a Floodgate client as both subject and viewer.

1. Join the network: others see `→ name joined the network` (Bedrock viewers `>> ...`); the joiner does not see their own line; no vanilla "joined the game" line appears.
2. Within the same UK day the first join shows "Welcome back, name!" to the joiner only, about 2 seconds after joining. A second join that day does not.
3. Hop to another server: others see `⇄ name hopped to Server` (Bedrock `<>`). The name carries tag, style and level badge as in chat.
4. Quick hopping: hop three times inside 60 seconds. Only the first hop shows a line. After 60 seconds the next hop shows the plain "hopped to" line with no count.
5. Leave after more than 20 seconds: `← name left` (Bedrock `<<`), and no vanilla "left the game" line.
6. Leave within 20 seconds of joining: no leave line.
7. Leave and rejoin within 2 minutes: no join line the second time. After 2 minutes the join line returns.
8. Hidden account (tab-hidden, or a role in `presence.hidden-roles`): join, hop and leave all silent for everyone.
9. `presence.scope=servers`: a viewer on an unrelated server sees nothing; viewers on the origin and destination servers see the hop.
10. `/announce hello`: everyone sees `[Network] hello`. `/announce -title Big news | Details`: chat line plus title and subtitle. `/announce -bar hello`: chat line plus action bar. Both flags together are refused.
11. `/announce <red>hot</red> <click:run_command:/op>x</click>`: red text; the click tag appears as literal text and does nothing.
12. A second `/announce` within 5 seconds is refused with a polite message; the proxy log has one ANNOUNCE line per sent announcement.
13. A player without the admin or dev group gets "You do not have permission"; console works.
14. Backend call (console on a Paper server with a player online): reflection call to `announce("test", "bar")` shows an action bar network-wide.
