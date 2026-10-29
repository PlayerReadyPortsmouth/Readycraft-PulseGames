# Comeback and mercy rules (29 Oct 2026)

Draft PR: "Comeback and mercy: end decided matches early, help trailing sides". No version bump.

## Engine
- `core/game/Mercy` (pure): decision rules, the hold/countdown/cancel `Timer`, draw-stat rule, headline, config readers.
- `core/game/Comeback` (pure): Bedwars team rule, kills-behind boost, smaller-side rule, config readers.
- `MiniGame` gains three additive hooks: `mercyWinners()` (null = not decided), `mercyHoldSeconds()`, `comebackLine(Player)`.
- `GameInstance` polls `mercyWinners()` once a second while running. When it first holds (after the game's hold) a 10 s
  "Game over: Red win by a mile" title countdown starts; if the signal clears the countdown is cancelled ("Back in the game!");
  at zero the match ends with reason `mercy` (full win rewards to winners, participation and placement bonus for the rest, usual podium).
- Results screen and chat show "Ended early: clearly decided" (new `note` component on `MatchResults.Result`; old constructors unchanged).
- Sidebar shows `Comeback: ...` under the match timer when a game's `comebackLine` returns text.

## Per game
| Game | Mercy | Comeback |
|---|---|---|
| Bedwars | all other teams bedless and leader has 2x their combined living players for 60 s | bedless team outnumbered by the biggest opposing team: free forge tier (capped at top tier) and a half-price Comeback shop tab (replaces Funky while on). Only-bed-left team gets nothing |
| KOTH, Kit PvP, Quake | gap >= 60% of target with under 40% of the clock left | Kit PvP and Quake: 5+ kills behind the leader, 3 s speed II on respawn |
| Dodgeball, Avalanche (rounds) | runner-up cannot catch up even winning every remaining round | Dodgeball: the smaller team gets one extra throw (ignores the cooldown once) at each round reset |
| Karts | a human finished, only bots racing: 5 s hold + 10 s countdown = 15 s grace (existing 30 s finish grace after the first finisher is unchanged) | none (item tiers already by placing) |
| Villager Defense, ReadyPlayerZ, race games, Block Party, TNT Run, Spleef | none | none |

## Draws
`GameInstance` already recorded a no-winner round as a draw (the review's "draw counts as a loss" was already fixed on main). The rule is now the
pure `Mercy.statForNonWinner` so it is tested: forced = nothing, winner or co-op = loss, no winner = draw. A mercy end always has winners, so it
never records a draw. A timed-out tie keeps paying both sides the second-place token bonus (`Placements.bonus`, now tested).

## Config (all default on)
`mercy.enabled`, `mercy.countdown-seconds` (10), `mercy.<game>.enabled`, `mercy.bedwars.ratio` (2.0) and `hold-seconds` (60),
`mercy.<koth|kitpvp|quake>.gap-percent` (60) and `time-left-percent` (40), `mercy.karts.hold-seconds` (5);
`comeback.enabled`, `comeback.<bedwars|kitpvp|quake|dodgeball>.enabled`, `comeback.<kitpvp|quake>.kills-behind` (5) and `boost-seconds` (3).
`bedwars-shop.yml`: new `comeback` category with `cb_*` items. A server's already-copied file lacks them: the forge perk still works but the
Comeback tab stays hidden until the file is merged (copy the `cb_*` lines in, or delete the file to re-seed it).

## Left out / notes
- Bedwars bot purchase classes untouched (another builder). Bots shop by item id so they ignore the comeback shelf.
- Dodgeball has no ball inventory, so "extra ball" is one cooldown-free throw per reset.
- Rounds mercy rarely fires with the default 3 rounds (needs a lead larger than the points left); it matters at 5 rounds.
- A comeback item bought just as the bonus ends is refused with a message.

## In-game acceptance (Java and Bedrock)
1. Bedwars 4 teams: break three beds, kill them; leader with 2x players for 60 s shows the title countdown; kill/revive does not matter, a
   returning player on the losing side cancels it ("Back in the game!").
2. Bedwars: bedless 1v3 team sees "Comeback: faster forge" in the sidebar, a Comeback tab in the shop (Bedrock chest-menu too), half-price wool/sword/armour; tab gone once they are no longer outnumbered.
3. KOTH/Kit PvP/Quake: big lead late triggers the countdown; a kill by the trailer cancels it.
4. Kit PvP and Quake: 5 kills behind, respawn shows the speed line for 3 s.
5. Dodgeball rounds 2v1: the single player can throw twice quickly at the round start.
6. Karts with bots: after the last human finishes the race closes in about 15 s.
7. Results screen shows "Ended early: clearly decided" after a mercy end; none after a normal end; record shows a draw only for a true tie.
