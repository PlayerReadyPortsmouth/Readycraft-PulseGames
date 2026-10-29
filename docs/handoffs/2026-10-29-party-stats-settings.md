# Party menu, stats profile and comfort settings, 29 October 2026

Status: draft PR from `origin/main` bde70d5 (0.5.0). Not played in a client. Plan: GUI polish review Part 2 ideas 1, 2, 6.

## What changed

- **Party menu** (`core/party/PartyMenu`, pure ordering/wording in `PartyView`): 4-row `Menu`. Member heads (leader first, gold star, glint),
  queue line from the leader's instance, Invite (leader only; opens a paged picker of lobby players who are not in a party and
  accept invites), invites on/off, Leave (members) or Disband (leader), Back to the game list. Every action runs the player's own
  `/party ...` via `performCommand`, so `Commands#party` and `plugin.networkPartyCommand` route exactly as typed commands do.
  The menu redraws 4 ticks after a command to let a network reply land.
- **GameMenu: one line only.** The Party button action (`GameMenu.PARTY_SLOT`) now opens `PartyMenu`. Its lore still says
  "Click for party help" and `partyHelp(...)` is now unused; left alone on purpose to avoid conflicts with the Bedrock/refresh builder. Reword the hint in a follow-up.
- **Stats menu** (`core/stats/StatsMenu`, `StatsFormat`): `/stats` opens it for players. Tokens (own only), streak and best,
  weekly wins, equipped cosmetics, per-game played/wins/win rate with the game icon (played games only, so unreleased games never show). Paged.
  `/stats --text` keeps the chat list; console: `/stats <player>` prints it. `/stats settings` opens settings.
  Added `EngagementService.currentStreak/bestStreak`.
- **Settings** (`core/settings/{Comfort,PlayerSettings,SettingsMenu}`): party invites (existing PDC flag), hide spectator reactions,
  fewer flashes, Block Party colour patterns. Stored as BYTE PDC keys `pulsegames:hide-reactions|reduce-flashes|colour-patterns`, all default off.
  Reached from the Settings button in the stats menu (slot 6) or `/stats settings`; no hub item or GameMenu change.
- **Colour patterns**: `BlockPartyGame.colors()` also uses patterns when any human participant opted in (whole round).
- **Fewer flashes**: skipped when anyone in the world opted in, replaced by quiet particles, for `Fireworks.burst`,
  Karts lightning and the lightning kill effect (`CosmeticsService`). Lightning/fireworks are world-wide, so one opt-in covers the round.

## Not done, needs a decision

- **Hide spectator reactions is stored but not yet applied.** Reactions are played in `SpectatorTools.emote`, which another builder owns.
  Follow-up: skip the particle and sound for viewers where `PlayerSettings.enabled(viewer, Comfort.HIDE_REACTIONS)`
  (the sound loop is per player; the particle is world-wide so send it with `Player.spawnParticle` per viewer). Until then the toggle does nothing; consider hiding it from the menu until wired.
- ReadyPlayerZ lightning (`ReadyPlayerZGame:553`) not gated (area is owned by another builder).
- Picker lists players on this backend only (parties spanning servers are driven by the proxy via the typed command).

## Visual checks (MinigamesTest, one Java client, one Bedrock phone)

1. Game list Party button opens the party menu; Back returns to the game list.
2. Solo: "No party yet", Invite shows lobby players, the heads render, invited player gets the chat accept/deny, opted-out players are absent.
3. In a party: leader has a gold star and glint and is first; non-leader sees Leave, leader sees Disband; both update after the click.
4. Queue line changes when the leader joins a game queue.
5. With PulseNetwork active: invite/leave/disband still work and the menu redraws.
6. `/stats` opens the profile; icons, counts and plurals read right; tokens show only your own; paging works with more than 21 played games.
7. `/stats --text` and console `/stats <player>` print text.
8. Settings toggles flip in place with no window flash; Bedrock tap size is fine.
9. Colour patterns: opt in alone in a Block Party round on an arena without the setting and see glazed terracotta; opt out and see wool.
10. Fewer flashes: no lightning for Karts/kill effect or firework burst in a round with an opted-in player.
