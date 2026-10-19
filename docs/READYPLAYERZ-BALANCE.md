# ReadyPlayerZ balance and combat settings

Status: 0.3.27 candidate, not deployed. All keys are flat arena settings; unset keys use the defaults.

## Zombie health

`health = min(zombie-health-max, zombie-health-base + zombie-health-per-round * (round - 1))`, with the round capped at 50.
Variant multipliers: Walker 1.0, Runner 0.8, Brute 1.5 (no resistance effect), Venom 1.0, Boss = `boss-health-multiplier`
(absolute cap 300). Spawned zombies have vanilla random equipment cleared and armour, toughness and reinforcement chance zeroed.

| Setting | Default | Range |
|---|---|---|
| `zombie-health-base` | 10 | 1-100 |
| `zombie-health-per-round` | 1.25 | 0-10 |
| `zombie-health-max` | 60 | 1-300 |
| `boss-health-multiplier` | 3.5 | 1-10 |

Out-of-range or non-finite values fall back to the default or clamp. Previous behaviour was `min(70, 18 + 2 * round)` with Boss x4 and Brute x1.6 plus Resistance.

## Legacy combat

`legacy-combat` (default `true`, Classic and Frenzy). On start, each human player's `ATTACK_SPEED` base is saved and set to 1024
(no attack cooldown), and `ENTITY_SWEEP_ATTACK` damage is cancelled. The saved base is restored when the player goes down,
quits, is eliminated, or the match ends or is cleaned up, and re-applied on revival. Bots are unaffected. Players back in the lobby
have vanilla combat. Zombie invulnerability frames are unchanged, so the hit rate remains capped at about two hits a second.
A hard server crash mid-match would persist the 1024 base in that player's data until they next play and leave a match.

## Hits to kill (full-charge, no crits; wood 4, stone 5, iron 6, diamond 7 damage)

See the PR description for the before/after table.
