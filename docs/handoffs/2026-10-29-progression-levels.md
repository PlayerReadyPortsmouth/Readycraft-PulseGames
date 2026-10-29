# Progression levels (29 Oct 2026)

Branch `feat/progression-levels`. XP across every game, a `[12]` level prefix, an unlock every five levels, a 41-item cosmetics catalogue and a wardrobe. Nothing has been run on a server.

## What it does
- **XP per eligible round** (`XpRules`, only where `ProgressionService.eligible` passes, i.e. ranked per `CompetitiveEligibility`, a human participant, not forced/aborted): 1st 100, 2nd 60, 3rd 40, others 20; +2 per kill capped at 20; +25 the first time that day in that game. A win counts as 1st. Challenges also pay XP (`xp:` in `challenges.daily.*`).
- **Curve**: finishing level n costs 100 + 50*(n-1) XP; level 1 starts at 0, level 100 is the cap. "Level up!" title (after the result titles, 3 s), level-up sound, firework particles.
- **Display**: coloured `[12]` in the lobby nametag (main-scoreboard team `pgl<12 hex>`, skipped if another plugin already owns the entry) and tab list, the podium, the results menu, `/stats` (title and a level tile), the game menu and Bedrock game form ("My level").
- **Broadcast**: to lobby-world players only, at every fifth level (5, 10, ...) rather than every level, so a lobby is not spammed. `progression.announce-levels` (default true). Never anywhere else.
- **Unlocks**: levels 5, 10, ... 100 each own exactly one cosmetic (`level:` in `cosmetics.yml`; 20 items). Granted on level-up and re-checked on join (idempotent), so catalogue edits and migrated players are covered.
- **Catalogue**: 13 coded items stay in `CosmeticsService.BUILTIN`; 28 new ones are data in `cosmetics.yml` (copied to the data folder on first run, like `kits.yml`). 20 unlock by level, 8 cost tokens. Slots: trails, kill effects, win effects, and a new **Spectator Reactions** slot (an equipped, owned reaction replaces the particle and sound of the Cheer/Love/Wow/GG tools). All effects are one vanilla particle burst and/or one vanilla sound (`EffectSpec`); particles that need data are refused at load. A bad entry is skipped with a console warning.
- **`/level`** (aliases `/xp`, `/levels`): Bedrock form (level, bar, next unlock, recent XP, Wardrobe button) or a 3-row chest. **`/wardrobe`**: `TabbedShop` with Owned / Unlockable / Buy tabs and a slot filter in the quick row. Token shop: level-only items cannot be bought there.
- **Storage**: `levels` section of `engagement.yml` (`LevelStore`: xp, last six sources, first-of-day marker). Unlocks and equipped items stay in `economy.yml`. No new database.
- **Migration**: first time a player is seen (join or first lookup) they start with 10 XP per lifetime win across all games (`XpRules.migratedXp`); no announcement. Existing regulars get their level rewards on that join.

## Left out, and why
- **Hats**: no hat slot exists and a helmet slot clashes with Bedwars/Kit armour and inventory restore.
- **Kart liveries**: `PulseKartsGame` keeps its visual behind an `AutoCloseable`, no hook for a skin.
- **Bedrock form for `/wardrobe`**: the shared `TabbedShop` is a chest menu (works on Bedrock through Geyser like the game shops). `/level` has the form.
- The PulseContent shared SQL path is untouched (still `progression.enabled: false`); levels do not need it.

## Deploy notes
- `cosmetics.yml` is created on first run only. A server that already has one must replace it with the repo file to get later additions.
- Config additions: `progression.levels-enabled`, `progression.announce-levels`, `xp:` per daily challenge.

## In-game acceptance
See the PR body.
