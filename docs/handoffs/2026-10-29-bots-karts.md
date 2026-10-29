# Kart bots: pace, rubber-banding, racing lines, drift and items (29 Oct 2026)

Branch `feat/bots-karts`, draft PR against `main`. Plugin version not bumped. Review items 1 to 7 of
`ops/readycraft/pulsegames-bot-quality-review-2026-10-29.md` section 3.2.

## What changed
- `KartBotTuning` (new): all `karts.bots.*` config, clamped; defaults when absent. Holds the rubber-band `pace()` function.
- `KartBotDriver`: tier ceiling = fraction x `MAX_SPEED` x style speed (0.78/0.90/0.98); corner table 8/5.5/3 scaled
  0.85/1.0/1.1 by tier; `Context` gained `paceScale`, `lap`, `tuning`; per-bot `Personality` (cautious/steady/aggressive)
  and lane phase from the UUID; lane offset and overtake lane swap (`aimPoint`, probe-clamped, fades out near the waypoint);
  PD steering (`KP 1/32`, `KD 0.04`) with a gentle blend towards the next waypoint; drift changes (Easy rare mini-drift,
  tier caps 22/40/50 ticks, same-direction bends keep a drift alive, no drift into a chicane); turbo needs a real straight;
  `Decision.cornerAhead`.
- `PulseKartsGame`: reads config at `onStart`; `paceFor` (nearest human by `placingEntry`, gate spacing converts checkpoints to
  blocks, refreshed every 10 ticks); full boost scale; lightning only with a human ahead; banana held for a corner (or 15 s);
  personality sets the item re-check tempo; Star/Bullet for back-tier bots behind a flag.

## Not done
- Shell leading: `shell()` is hitscan, so aiming ahead of a moving target would make bots miss. Left as is.
- Map work (more waypoints, item 8).

## Needs a human in game
See the PR acceptance list. Nothing has been driven in a real server.
