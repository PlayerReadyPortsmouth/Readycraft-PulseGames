# Lobby placements (29 Oct 2026)

Draft PR: game selector NPCs, hologram boards, daily reward chest, lobby parkour. Walkthrough: `docs/LOBBY-PLACEMENTS.md`.

- New in `core/lobby/`: `LobbyPlacements` (service, commands, listeners), `LobbyData` + `LobbySpot` (lobby.yml model), `LobbyNpcs` (Citizens by reflection, private in-memory registry), `DailyChestRules`, `ParkourRun`, `LobbyBoardText`.
- New in `core/stats/`: `WeeklyCounters` (weekly wins/kills/tokens per game, ISO-week reset, section `_weeklyStats` of stats.yml; also ranks the all-time counters). `StatsService` also records `_names.<uuid>` and `<uuid>.network.tokens` (from `EconomyService.addTokens`).
- Shared files touched additively: `PulseGamesPlugin`, `Commands` (`/pulse lobby`, reload), `GameMenu.pick` now package-private, `config.yml` (`lobby.npcs`, `lobby.chest`, `lobby.parkour`), `EconomyService`.
- NPC right-click is handled from `PlayerInteractEntityEvent` (not Citizens events) because the NPCs sit in an anonymous registry.
- Not done: cosmetic shard extra (no such item in the catalogue), per-game token boards, all-time tokens before this version.
