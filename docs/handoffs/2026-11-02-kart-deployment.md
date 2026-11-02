# PulseGames 0.11.0 deployment — 2 November 2026

Aurora explicitly authorised both GitHub push and MinigamesTest deployment/restart.

- Game source: a30f18c, draft PR https://github.com/PlayerReadyPortsmouth/Readycraft-PulseGames/pull/99 . Companion content: draft PR https://github.com/PlayerReadyPortsmouth/ReadyCraft-PulseAddon/pull/84 . Neither PR merged.
- Target: readyapp-vps, MinigamesTest `f542baf0-4ae2-41ed-840e-9b2fd7697cd7`.
- Replaced PulseGames 0.9.8 with 0.11.0. SHA256 `e916e86a88ad4c4197797c8cac852bab5a1d56aa2a046d099e8e747f745c18a5` verified before and after installation. Ownership crafty:crafty, mode 0644.
- Backup: `/root/pulsegames-backups/20261102T010041Z-kart-0.11.0` contains the former jar and config, byte-compared before replacement.
- Player list and online count checked empty twice before restart. Restart used ready-gamed with a 30-second warning. No unrelated server restarted.
- Startup 01:02:23 UTC: PulseGames 0.11.0 enabled. Loaded 82 arenas, 24 games. Server Done at 01:02:27 UTC (68.802s). API status running, online 0; no ERROR/Exception entries in inspected startup log. No in-client gameplay claimed.
- Runtime flags `karts.showcase` and `karts.track-physics` absent from config, therefore disabled by code defaults. No config or allowlist writes.
- PulseContent 0.5.8 and proxy adapters unchanged. New model/pose companion sources pushed only. Final resource packs, companion installation and mixed-client acceptance remain pending.
- Targeted kart/bridge tests: 248 passed, gate 20261102-005941-fb7. Tracker regenerated and changed pages visually inspected. Discord PR notifier ran for both PRs; disabled webhook sent nothing.

## Rollback

With a separately authorised restart and fresh player check, stop MinigamesTest, move PulseGames-0.11.0.jar outside plugins, restore PulseGames-0.9.8.jar from the backup with crafty:crafty ownership, and start. Restore config only if required; no config write was made by this deployment. Preserve any subsequent gameplay data.
