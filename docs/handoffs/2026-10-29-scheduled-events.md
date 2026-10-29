# Scheduled events (29 Oct 2026)

Hourly featured tournaments, new package `core/events/`. Draft PR; nothing played in-game yet.

## What it does
- `events.schedule` lines (`every: 60m` counted from London midnight, or `at: "HH:mm"`) and an `events.games` rotation
  (Duels classic bracket, Spleef points, Karts cup points, Bedwars duos bracket, Block Party points, TNT Run points,
  Dodgeball teams bracket, Quake points). The rotation index is derived from the clock, so a restart picks the same game.
  A game that is unavailable, still preview-only or has no map is skipped for the next in the rotation.
- Sign-up opens `events.announce-lead-minutes` (5) before the start: chat line with a `[Join]` click, a lobby title, a lobby
  sidebar countdown, and one extra row in `networkQueues()`. `/event join` or the queue link joins; `/event leave` leaves.
- At the start, fewer than `events.min-players` (2) humans cancels with a polite message. Otherwise:
  - Bracket: single elimination with byes (top seeds), optional third-place match. Team modes use fixed teams of the mode's
    team size (joiners paired in random order) and `GameInstance.presetTeams`. A draw is decided on best place, then score,
    then a coin toss.
  - Points: `rounds` (3) rounds with everyone; placement points from `events.rewards.points-table`; tie-breaks are points,
    round wins, total of places, last round; level players share a rank. Podium of top three.
- Rounds are private (practice-flagged) instances, so queues never fill them, no bots join and the engine pays nothing itself.
  The event pays instead through `EconomyService.addTokens` at `tokens-multiplier` (2.0), only when
  `CompetitiveEligibility.eligible(humans, 0, capacity)` says the roster counts (a walkover pays nothing). The personal
  token booster still applies on top (existing `addTokens` behaviour).
- `/event` (alias `/tournament`): menu with next three events, join/leave, bracket (live matches are watchable through
  `GameInstance.watch`, which uses the normal spectator tools), last results. Bedrock gets forms for all of it.
  Admin: `/pulse event start <game> <mode> [bracket|points]`, `cancel`, `list` (needs `pulsegames.admin`).

## Network
PulseNetwork already carries queue rows over the HMAC `Wire` and the proxy announces a new row with a Join link, so the
event is advertised as a row (`event-<minute>`, title "Tournament: Spleef at 18:00") while sign-up is open. A JOIN for that
id is routed to `EventService.join` in `PulseGamesPlugin.joinNetworkQueue`; `networkPlayerBusy` returns true for signed-up
players so the proxy does not send them home. Nothing moves a player: they click the link themselves.
TODO(PulseNetwork): a dedicated ANNOUNCE message kind (Wire + VelocityBridge) would allow the exact wording
"Tournament starting in 5 minutes on Minigames: Spleef. /queues to join" on other servers; today other servers see the
standard "Minigame queue: ..." line with the tournament title. No Velocity change is made here.

## Left out
- "Tournament winner" cosmetic: the cosmetics service has no title category and unlocks are permanent. Winners are
  recorded in `events.yml` (7 day window), shown in the menu as "Winners this week" and via `EventService.isChampion`.
- Heats for more than 16 entrants (sign-up is capped at 16).
- Results screens show the practice label for event rounds.

## Shared files touched (small, additive)
`GameInstance` (round-end listener, preset teams, `watch`), `PulseGamesPlugin`, `Commands`, `plugin.yml`, `config.yml`.
