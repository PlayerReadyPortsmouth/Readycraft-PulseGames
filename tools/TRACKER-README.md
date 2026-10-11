# Updating the shared minigames report

Edit `../MINIGAMES-TRACKER.md` as the canonical record, then regenerate the HTML and PDF together before handing the tracker back. Do not edit the generated HTML or PDF directly. Preserve the distinction between prepared and deployed work and Aurora’s recorded per-map Prop Hunt approval requirement.

From the PulseGames directory:

```sh
node tools/render-minigames-tracker.mjs --pdf
```

Requires Node.js, marked and Playwright with Chromium. `CODEX_NODE_MODULES` can select a bundled dependency directory; `TRACKER_CHROMIUM` can select an installed Chromium executable. Without `--pdf`, only HTML is generated and no browser is required. The HTML is standalone and uses no remote fonts, scripts or assets. Its Print / Save as PDF button prints every section in A4 landscape; enable background graphics for the designed colours.

Regeneration reads all sections and game/map rows from the Markdown. If the active map batch changes, also update the report cover’s current-priority text in the renderer. Recheck the PDF visually after changes, especially long tables. Both exports are dated snapshots, not live server status. Local documentation links require the repository; all tracker content itself is embedded.
