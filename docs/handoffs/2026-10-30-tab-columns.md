# Tab columns (30 Oct 2026)

Branch `feat/tab-columns` from `origin/main` 1bbde2d (PulseNetwork 0.2.0). Draft; nothing deployed or enabled; no versions bumped.

## What changed
- Velocity only: new pure `TabLayout` (plan, ids, order), `TabRender.rows(Plan, ...)`, three `TabConfig` keys, `TabService` picks columns or the grouped list per viewer. `TabSync` is unchanged (fillers are ordinary proxy-owned rows).
- Keys: `tab.layout` (`list`|`columns`, default `list`), `tab.columns.show-empty` (`false`), `tab.columns.filler` (empty).
- README "Tab columns" with the acceptance list and packet counts. `TabLayoutTest` (planning table, overflow, ids, order, fallback, config).

## Decisions and left out
- Fixed 4 columns and 20 rows (design's `tab.columns.max/rows/overflow` keys not added; one overflow rule only).
- Overflow on the viewer's own server falls back to grouped, because its entries belong to the backend. Other servers get "+n more" when no column is spare.
- More than 4 populated servers falls back (no shared last column).
- Not included: per-server row templates, per-column header keys, identity or role changes.
- Unverified on a real client: sort order versus teams/gamemodes, column-major fill, filler appearance, `higherFirst` direction (stream 0 test still needed).
- The TabService wiring has no mocked end-to-end test; the planning and rendering are covered.
