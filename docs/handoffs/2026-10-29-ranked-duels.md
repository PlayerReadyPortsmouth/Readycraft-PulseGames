# Ranked Duels (2026-10-29)

Elo per kit, five divisions, seasons, rating-aware matchmaking and a badge. Code is in `core/ranked/`.

## How it works
- `/ranked` (menu on Java chest and Bedrock form), `/ranked queue <kit>`, `/ranked leave`, `/ranked history`. Also a Ranked button in the Duels style menu, the games list (slot 52) and `/stats` (slot 7).
- Kits are the Duels discipline ids (`classic`, `sumo`, `crystal`, `mace`), config `ranked.kits`. Ranked games use modern combat. `op` is not a registered Duels mode, so it has no ladder.
- Pairing: `RankedQueue` pairs the longest waiter with the closest rating both accept (range 100, +50 every 10 s, cap 400). A pair gets its own arena (`InstanceManager.create`, reserved with `GameInstance.markRanked`; casual queues never see it) and is admitted through `PlayService.joinNetworkQueue`. Sidebar shows "Searching... (range +-N)". `/ranked leave`, `/lobby`, quitting or joining another game cancels.
- Rating: Elo, K 32 for the first 10 games then 20, start 1000, floor 0, 1v1 only. First 5 games show "Unranked" (rating hidden, still moves).
- Bots (`ranked.allow-bots`, default false): after `bot-after-seconds` with no opponent the player gets an arena and the existing queue bots fill it. Tier is the nearest anchor (900/1200/1500) to the player's rating via `BotService.difficultyFor` -> `GameInstance.rankedBotTier()`. Bot games never change a rating. Profile numbers are not interpolated between tiers (see below).
- Fair play: a disconnect gets `ranked.disconnect-grace-seconds` (30). The match still ends at once for the opponent, but the rating result waits for the grace period; if the leaver reconnects in time the match is voided for both. An explicit leave (/lobby) is an immediate forfeit. Same pair inside 10 minutes: change x0.5, then x0.25. Ended by `forced`/`shutdown`/`aborted`/`error` or server stop: no change (pending settlements are dropped on disable).
- Season: `ranked.season.id` and `ranked.season.ends`. Changing the id, or the end date passing, archives each placed player's peak division (`_ranked.archive.<id>.<uuid>.<kit>` in stats.yml), soft resets to 1000 + (old-1000)/2 and announces. After the end date passes the ladder pauses ("resting between seasons") until the config id changes.
- Badge: `[GLD]`-style tag as a scoreboard team prefix on each duellist's private board (every Duels match, per discipline), on the `/stats` and `/ranked` menus and the results screen line; metadata `pulsegames.ranked.division` (best division, or "Unranked") on the player. Nothing touches chat formatting.
- Storage: `_ranked` section of stats.yml via `StatsService.ranked()`, same dirty flag and flush.

## Left out / limits
- No bot profile interpolation: the bot profile is chosen per tier from `BotCombatSettings`; scaling numbers between tiers needs a new override path in BotService (shared, hot). Tier mapping only.
- A player returning within the grace window voids a loss, so a deliberate disconnect-reconnect dodges one. Needs a per-player void limit if it is abused.
- Ranked uses each player's personal Duels kit, as casual does.
- Bedrock games-list form has no Ranked button (FormLayout.gameList); Bedrock reaches it through `/ranked`, the Duels menu and `/stats`.
