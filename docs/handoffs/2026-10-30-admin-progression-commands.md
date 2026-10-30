# Admin progression commands (30 Oct 2026)

Branch `feat/admin-progression-commands` from `origin/main` 2a62d63 (0.9.5 bundle). Draft; no version bump, nothing deployed.

## Commands
All need `pulsegames.admin` (the `/pulse` permission) and work from the console. Each accepts an optional leading `admin` (`/pulse admin xp ...` = `/pulse xp ...`). The target must be online. Tab-complete covers verbs, players, actions and style ids.
- `/pulse xp <player> add <n>|set <n>` (0 to 1,000,000). Runs the unlock path only if the level rises.
- `/pulse level <player> set <1-100>|reset`. `set` writes the exact `XpRules.xpForLevel` XP, runs the real unlock path (cosmetics plus `pulse.namestyle.<id>` via `NameStyleGrants`), refreshes nametag/badge, and prints what unlocked. Earned rewards are kept when lowering a level. `reset` sets XP to 0 and removes level-reward cosmetic unlocks (and unequips them); LuckPerms permissions are not touched and the message says so.
- `/pulse namestyle <player> grant|revoke <id|all>`: direct LuckPerms grant/revoke (ignores `progression.namestyle-grants`); `all` = the 16 shipped ids.
- `/pulse progression <player>`: level, XP, next threshold, cosmetics unlocked, name styles held (`hasPermission`).
- Audit: `PROGRESSION <actor> <verb> <target> <value>` on the console log (verbs `xp-add`, `xp-set`, `level-set`, `level-reset`, `namestyle-grant`, `namestyle-revoke`).

## Code
- `ProgressionAdmin` (pure, tested) and `ProgressionAdminCommand` (Bukkit adapter).
- `NameStyleGrants.Granter` gains default `revoke`; `LuckPermsGranter` implements it; `NameStyleGrants.adminGrant/adminRevoke`.
- `LevelService`: unlock loop extracted to `unlockThrough` (same behaviour, returns names) plus small admin accessors. `EconomyService.lock/unlockedCount`.
- `Commands`: small additive routing and tab entries.

## Notes
- LuckPerms changes are async; `progression` straight after a grant may show stale `hasPermission` for a moment.
- `level reset` leaves the `levels` store entry at 0, so the player is not re-migrated from lifetime wins.
- Admin level set does not send the player the usual per-item chat or the title; the admin gets the summary.

## Acceptance (Java and Bedrock)
- `/pulse level <you> set 10`: nametag shows [10], summary lists unlocks, `/namestyle` offers the styles up to level 10.
- `level reset`: [1], wardrobe level rewards locked again, name styles still held until `namestyle revoke all`.
- `namestyle grant all` then `progression`: 16/16; `revoke all`: 0/16.
- Console log shows one PROGRESSION line per change. Not run on a live server.
