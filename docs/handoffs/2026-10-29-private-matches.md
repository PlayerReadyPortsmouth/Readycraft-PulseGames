# Private matches (draft, 29 Oct 2026)

Party leader picks game, mode and map (or Random), toggles "fill with bots" and the bot difficulty, then starts a game only the party can join.

## How it works
- `/party private` or the **Private match** button in the party menu (Java chest menu; Bedrock simple forms, same steps). Leader only. A player with no party can use it alone with bots.
- New package `core/play`: `PrivateSetup` (the choices, reused by rematch), `PrivateDraft` (menu state), `PrivateRules` (pure rules), `PrivateMatchService`, `PrivateMatchMenu`.
- `GameInstance.configurePrivate`: admits only the setup's members (bots are always allowed in), uses `private.countdown-seconds`, relaxes the minimum (1 person if bots can fill, else 2, never above the mode's own minimum) and says "Private matches pay tokens only when most players are people." at the start.
- Never in Quick Play, public queue counts, `findOrCreate` or the PulseNetwork queue list; network joins to a private instance are refused.
- Tokens, stats, progression and challenges all already read `GameInstance.ranked()`, which is `CompetitiveEligibility`. No change there, so bot-stuffed private games pay nothing.
- Bots: the map's `bots-enabled` is respected unless the leader pressed the toggle (explicit). Difficulty "Map default" follows the map's `bot-difficulty` then `bots.difficulty`; Easy/Normal/Hard/Adaptive override both. Global `bots.enabled`, bot-supported games and tester access still apply.
- Busy maps: a map used by an unfinished match is swapped for a free arena on the same world template, else the leader is told it is busy. Random only picks a free arena.
- `/party spectate`: a member who missed the start joins the waiting lobby, or watches a running match.
- Rematch: "Play again" on a private result (leader only) starts the same game, mode, map and bot choices for the party as it is now.
- `/party invite|accept|deny` already existed (clickable in Java). Added a Bedrock Accept/Decline form for invitees.

## Config (`config.yml`)
`private.enabled: true`, `private.countdown-seconds: 10`, `private.max-party: 16`, `private.allow-bots: true`.
Note: the party itself is still capped by `party.max-size` (8), so the effective cap is the lower of the two. Raise `party.max-size` to reach 16.

## Left out
- Duels (needs per-player kit choice; has its own queue).
- No per-map exclusivity switch: busy means any unfinished match on that arena.

## In-game acceptance (Java and Bedrock)
1. Leader: `/party private` opens the list; pick game, mode, map; Start loads and admits the party.
2. A non-party player cannot find the match in Quick Play or the game menu counts, and `/play` does not put them in.
3. Solo leader with bots on a bot-supported game starts after 10 s once bots arrive; a game without bot support says it needs two people.
4. Toggle bots off: no bots join; two friends can start.
5. Map busy: start two private matches on the same map; second gets another copy or a "busy" message.
6. Late member: `/party spectate` watches the running match (waiting lobby: joins as a player).
7. End the match: results show; leader's Play again restarts the same setup; a non-leader is told to ask the leader.
8. Bot-heavy private match: no tokens or challenge progress; people-majority match pays normally.
9. Bedrock invitee sees the Accept/Decline form.
