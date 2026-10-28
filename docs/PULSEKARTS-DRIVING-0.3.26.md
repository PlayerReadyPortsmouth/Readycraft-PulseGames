# PulseKarts driving and pickup candidate

Prepared locally, not merged or deployed. PulseGames 0.3.26 requires PulseContent Paper 0.5.8 and the new pickup catalogue/model/allowlist entry on tracks with itembox regions. Existing core/Geyser plugin binaries remain compatible; both generated resource packs need the new content release before client testing. Live MinigamesTest remains 0.3.25.

## Agreed direction

Mario Kart Wii-inspired arcade handling, forgiving turns and drift boosts. Driving feel first; items should offer moderate comeback chances without dominating skill. Unlocks are cosmetic karts, paint, trails and celebrations. Balanced gameplay styles stay separate from cosmetic rewards. Approved pickup art: turquoise cube, gold frame, white question mark.

## Implemented

- Shallow wall scrapes preserve a safe movement component with speed loss. Frontal collisions, blocked corners, unloaded terrain and tall obstacles still stop the kart. Existing slab step-up remains.
- Hold jump while driving forward and steering at speed to drift. Release after 12 ticks (0.6 seconds) for a 16-tick boost, or 25 ticks (1.25 seconds) for a 30-tick boost. *(Superseded in the 0.4.3 arcade candidate by three tiers at 20 / 50 / 80 ticks; see `PULSEKARTS-ARCADE-DESIGN.md`.)* Audible cues mark both charge stages and release. Rear slip changes the travel heading while the chassis continues turning.
- Reverse/braking, opposite steering, airborne driving, spins/slows, resets and getting stuck cancel drift charge. A held key cannot re-arm a cancelled drift until released. Boost timers extend by maximum, not addition; turbo and drift multipliers do not stack.
- World-owned pickup boxes are noncolliding and independent of any single racer. Slow spin/bob, six question marks, original artwork. Personal five-second collection cooldown remains; no global disappearance. *(Legacy `itemboxN` regions only: 0.4.3 `itemboxN` locations are collectable and respawn globally after 4 s.)*
- At most 32 authored pickup regions per map. Boxes only spawn in already loaded chunks and refresh for content-ready humans, including spectators after pack load. Match cleanup, failed starts and audience changes release handles. Failed close retains ownership for retry and prevents duplicate replacement.
- Resetting preserves held forward/back/steering input, so a held accelerator does not require release/repress. Jump held through reset cannot immediately re-arm drift.
- Track preflight rejects missing checkpoints/finish and overlapping first checkpoint/finish cuboids. Adjacent gate faces are allowed.
- Shell/lightning slows grant shared hit protection until the slow ends plus two seconds. Rear bumps also respect protection. Lightning requires an unprotected racer at a later checkpoint; if none qualifies, the item is retained. Comeback item weighting does not grant extra inventory slots or bypass the personal pickup cooldown.
- Java kart display interpolation explicitly uses one tick. Existing immediate kart yaw remains. Live proxy configuration already permits the exact kart model attachment; this does not prove individual Bedrock link acceptance or eliminate network latency.

## Race completion follow-up

The first finisher starts a 30-second finish grace period instead of ending all racers immediately. Maps can set `finish-grace-seconds` (5–60). Finishers lose their kart and become spectators while retaining participant reward eligibility. The round closes when every remaining racer finishes/leaves, the grace expires, or the overall time limit expires. Empty-human cleanup remains unchanged.

Finish order uses the simulation tick plus swept finish-line entry fraction, so iteration order does not decide same-tick arrivals. Exact first-place ties share victory; lower finishers are not winners. Recorded positions survive departures. Existing reward rules still require the winner to remain a participant: a first finisher leaving forfeits end rewards, without promoting someone else. Positions are match-local, not a new persistent leaderboard.

Pickup refresh now preserves unaffected boxes when another track chunk loads/unloads. Audience changes still recreate visuals to apply the provider's viewer contract.

## Limitations

This is not client-side prediction. Java and Bedrock responsiveness needs comparison testing. The candidate adds deliberate bot corner drifting using the same physics and 12/25-tick charge thresholds as players; 117 focused checks pass, including earned-boost and complete-lap simulations; actual complete-map bot laps are still pending. Cosmetic progression and additional item types are future work. Items were shells, turbo and lightning (0.4.3 adds more); comeback weighting uses completed checkpoint progress, so racers within the same segment are not geometrically ranked. Source model preview is not in-game acceptance. For exact finish ties, the finish announcement shares the placing correctly; the sidebar list may still show consecutive row numbers (1, 2).

## Test runbook

1. Install the coordinated plugin/content candidate through the usual backed-up deployment after approval. Merge the pickup example into the existing catalogue; never replace the catalogue wholesale. Enable readycraft:kart_pickup_box in the visual allowlist and deliver rebuilt Java/Bedrock packs.
2. Join one Java and one Bedrock client. Check kart rotation and visual alignment while accelerating, turning, braking and viewing the other racer.
3. Brush a wall at a shallow angle: retain reduced forward movement. Hit it head-on and attempt a corner shortcut: stop without clipping. Cross slabs and recover from a void reset.
4. Hold jump through a clear corner, hear the first/second charge cue and release for a short boost. Confirm quick taps, reversing, repeated wall-stalling, spins and resets cannot farm boosts.
5. Check visible boxes along the track. Both players should collect independently. A player leaving must not remove remaining players' boxes; a spectator completing pack load should see them. Distant boxes should appear when their chunks load.
6. Finish/leave, start another race and inspect for leftover boxes, seats or errors. Check checkpoints and Cup laps still require ordered crossings.
7. Compare bot races at each difficulty. Check deliberate corner drift, charge/release boosts and scrape recovery; record complete-map results by difficulty and kart style. Prepared code is not evidence of successful live bot laps.

8. Keep Forward held while falling and through the checkpoint reset: accelerate again without releasing Forward. Hold Jump too: no drift charge until Jump is released and pressed again.
9. In a disposable test map, overlap first checkpoint and finish: start must refuse with the track-validation message. Move them to adjacent non-overlapping cells: that specific refusal clears.
10. Hit one racer repeatedly with shells/lightning and a rear bump: protection prevents chained slows/spins. Wait for the slow and two-second grace to expire before a new item hit can apply. Use lightning with no unprotected racer at a later checkpoint: retain the item. Verify same-segment racers do not qualify merely by being visually ahead.


11. With two racers, finish first and confirm the other gets the remaining grace countdown. Finished racers should spectate without steering, collecting or colliding. Finish the second racer and confirm immediate round completion.
12. Repeat with an unfinished racer until grace expiry; then repeat with the first finisher leaving. Preserve finish order and never award the departed winner's place to a slower racer. Exact first-place ties may share victory.
13. Watch an existing box while driving into range of another chunk: the first should remain stable. Test spectator audience changes separately.

No restart, pack rollout or live map changes are included in this prepared batch.
