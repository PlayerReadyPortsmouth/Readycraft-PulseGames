# PulseKarts readiness — 15 October 2026

Source review only; not a driving acceptance test. Sprint and three-lap cup exist, with ordered checkpoints, vehicle movement routing and item boxes. Cup currently means laps of one track, not a multi-track championship. No kart bot controller exists yet.

## Recommended first implementation pass

1. Owned karts: track each player's boat; clean up only owned boats on quit, end and aborted games. Prevent on-foot progress and provide dismount/remount/reset handling. Verify grid freeze while mounted.
2. Reliable gates: test the movement segment against checkpoints and item boxes so fast boats cannot skip thin regions. Add command-configurable safe reset positions and headings; occupied vehicles alone do not prove safe ground.
3. Working power-ups: apply timed boost/slowdown to the boat, recognise shell hits on its hull, use kart heading for boosts, limit players to one held item and require leaving/re-entering boxes before collecting again.
4. Race results: rank racers by lap, checkpoint and progress along the current segment. Add lap/total time, a finish grace window and complete standings instead of ending on the first finisher.

## Then driving bots

Add command-editable racing-line waypoints with corner speeds, braking, overtaking variation, stuck recovery and item decisions. Ordinary Citizens walking is not a driving controller. Keep kart bots unavailable until a dedicated controller is tested.

## First acceptance map and client checks

Use one broad ice circuit with barriers, numbered checkpoints, finish, item boxes, grid slots and deliberate reset zones. Test Java and Bedrock steering, mounted item use, dismount/remount, boosted movement, hull hits and checkpoint crossings. Preserve vanilla boats initially; PulseContent cosmetic bodies can follow reliable driving.

## Avalanche companion change

Prepared shrinking arena: default first collapse after 30 seconds, one block inward per side every 15 seconds, five-second warning, minimum dimension 7. The outer supporting columns are removed in bounded batches from the temporary match world down to void-y (or one block under the region). Falling blocks follow the remaining footprint, and bots favour movement inward. Players outside the current footprint have five seconds after each contraction before elimination. Template maps remain unchanged. Settings: shrinking, shrink-start, shrink-interval, minimum-size and void-y.
