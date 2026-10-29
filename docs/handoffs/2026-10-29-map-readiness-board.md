# Map readiness board, 29 October 2026

Status: draft PR from `origin/main` bde70d5 (0.5.0). Not played in a client.

`/pulse maps board` (needs `pulsegames.admin`, in-game only) opens a read-only chest dashboard built on `core/ui/Menu`.

- Game list: icon per game, lore "N of M maps fully checked" and a missing-evidence count. Paged.
- Game screen: one row per map (4 per page, worst first): five panes for BUILT/VISUAL/JAVA/BEDROCK/BOTS. Each pane is a
  coloured glass pane and carries a symbol and word in its name (check pass, cross fail, circle pending, arrows stale,
  dash missing), so colour is never the only cue. Lore shows the evidence age and the start of the note.
  "Stale" means the mark's revision or configuration fingerprint no longer matches (same rule as `/pulse maps check`).
- Filter button (slot 8) toggles "missing evidence only" (any check never recorded or stale).
- Clicking a pane closes the window and prints the exact `/pulse maps mark <game> <map> <check> pass <evidence note>`
  command; the line is click-to-suggest. Nothing is written by the board. Visual marks still need `pulsegames.maps.approve`.

Code: `core/maps/MapBoardModel` (pure: status, summary, age, ordering), `MapBoardMenu` (UI), `MapReviewService.board`
(wiring, fingerprint per map computed on each render of a game), `Commands` tab completion. Docs: `MAP-SETUP.md`, `MAP-REVIEW.md`.

Tests: `MapBoardModelTest`.

Visual checks needed on MinigamesTest: Java and Bedrock (Geyser) pane tooltips readable; symbols render on Bedrock;
click-to-suggest line works on both; paging and filter do not flash the window.
