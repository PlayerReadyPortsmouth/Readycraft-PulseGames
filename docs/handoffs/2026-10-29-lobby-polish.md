# Lobby polish handoff (29 Oct 2026)

Part 2 ideas 3, 4, 8 and 9 of the GUI polish review. Author metadata already credits `AurorasChaos (on behalf of Player Ready)`.

## What changed
- **Idea 3, wording.** `core/util/PlatformDetector` (Floodgate is `compileOnly` from `repo.opencollab.dev`, `softdepend: floodgate`; every call is guarded, so the plugin loads and treats all as Java without it). Item names and hints now say "right-click" on Java and "tap" on Bedrock (lobby items, ready item, leave bed, Item Shop, Supply Shop, Splegg launcher, karts, Prop Hunt, Quake). Shared broadcasts and the static game cards cannot know the platform, so they say "use". Not touched: `core/setup` (owned elsewhere). Item matching never used the names (it uses tags/types), except the legacy-name cleanup, which still matches the old text on purpose.
- **Idea 4, watch menu.** `SpectatorTools.openWatchMenu`: party members and teammates first, then highest score, then name; lore shows "On your team", score and hearts; "Follow the leader" star (hidden until someone has scored); redraws in place when anyone is eliminated (`GameInstance.eliminate`); eliminated players are never listed. Plain Bukkit inventory: actions run next tick, all click types and drags are cancelled, names are non-italic. Pure ordering is in `WatchList`.
- **Idea 9, join action bar.** `EngagementService.onJoin` shows one line two seconds after join (e.g. "Today: Spleef pays x1.5. Quick Play?") when a daily challenge or first-win bonus is waiting or a featured multiplier is on. There was no existing per-player opt-out, so the switch is the server key `engagement.join-hint` (default true). Weekly-reward notices still work and the rollover path no longer triggers the hint.
- **Idea 8, sidebar diffing.** `SidebarService.show` caches the last drawn entries per player (and the title, and which scoreboard they were drawn on) and only resets/sets entries that changed. If the board or objective is not the cached one it redraws everything as before. Lines are identical on screen when they change. Pure logic in `SidebarDiff`.

## Measuring the sidebar change (MinigamesTest)
The counter compares both methods inside the same run, so no second jar is needed.
1. Set `debug.sidebar: true` in `plugins/PulseGames/config.yml` and restart (or reload config + restart; the timer starts at enable).
2. Fill a lobby (bots are fine: `/play <game>` with queue bots, or several clients) and let a round run for a minute.
3. The console logs once a second: `sidebar: N refreshes, line operations X (old method: Y)`. Y is what the previous reset-all-and-rewrite would have done (entries cleared plus lines written); X is what is done now. Average X/Y over the round is the saving; expect a large drop in the waiting room (static lines) and a smaller one in a running round where timer and score lines change each second.
4. For a client-side check, watch a Bedrock phone on a full board for flicker before and after. Real packet counts need a proxy/packet logger: line operations are a proxy for them (each set or reset is one score packet).
5. Set `debug.sidebar` back to false afterwards.

## Not verified live
Unit tests cover wording, watch order, sidebar diff and hint text. Floodgate detection, the menu on Bedrock, and the packet gain need a run on MinigamesTest.

## Tests
`ready-gate exec -- bash ./gradlew build test` passes: `PlatformDetectorTest`, `WatchListTest`, `SidebarDiffTest`, `JoinHintTest`.
