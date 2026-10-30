# Name style unlocks (30 Oct 2026)

Branch `feat/namestyle-unlocks` from `origin/main` 1bbde2d. Draft; no version bump; nothing deployed.

## What changed
- New cosmetic category `NAME_STYLE` (slot `name_style`) and 16 entries in `cosmetics.yml`, ids `ns-<style>`. Levels: red 3, orange 6, yellow 9, green 12, aqua 15, blue 18, purple 21, pink 24, sunset 30, ocean 40, forest 50, candy 60, rainbow 70, shimmer 80, pulse 90, sparkle 100. Name styles have their own level set (any level 1-100, one per level); other items still use multiples of 5.
- `NameStyleGrants` (pure, tested) + `LuckPermsGranter` (LuckPerms API, `compileOnly net.luckperms:api:5.4`, soft-depend). `LevelService.grantUnlocks` calls it on level-up and on first-sight catch-up. It adds `pulse.namestyle.<id>` via `UserManager.modifyUser` (async, persists, syncs). Never removes. It skips styles the player already holds (`player.hasPermission`), so a failed or missed grant retries on the next join. LuckPerms missing: one info line, no-op.
- Unlock message for a name style says "Set it with /namestyle."
- `/wardrobe`: name styles appear under the Name Styles filter, lore says "Set it with /namestyle"; clicking an unlocked one runs `/namestyle set <id>` as the player (no equip state kept here).
- `/level`: chest button (slot 22) and Bedrock form button "Choose a name style" (runs `/namestyle`); form body shows "Name styles: n/16".
- Token shop shows only the first five categories, so name styles are not purchasable. `/stats` skips the category.
- An already-saved server `cosmetics.yml` without name styles gets the bundled ones merged in at load (nothing written to the file).

## Config
- `progression.namestyle-grants: true`.

## Left out
- Item 4 (event/season rewards): `EventRewards` pays tokens only; there is no cosmetic or permission reward type, so no `namestyle:<id>` hook was added.
- Existing players keep their old unlocks; they get the grants on next join (catch-up).
- Not verified on a live server, LuckPerms, or either client.

## Acceptance (Java and Bedrock)
- With LuckPerms and PulseNetwork `/namestyle`: reach level 3, see "New name style unlocked", `lp user <name> permission check pulse.namestyle.red` is true, visible on another backend.
- A player at level 40 joining for the first time gets red to ocean-equivalents (red..sunset) granted.
- `/wardrobe` > Name Styles: locked shows level needed; unlocked click applies the style. Bedrock: `/level` form has the name style button.
- `namestyle-grants: false`: unlock message still appears, no permission is added.
- LuckPerms absent: one log line, no errors.
