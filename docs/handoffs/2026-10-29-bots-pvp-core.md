# PvP bots: humanlike combat core, 29 October 2026

Status: draft PR from `origin/main` 693fca0 (plugin version unchanged at 0.6.0; a bundle bumps it). Not played in a
client. Plan: `ops/readycraft/pulsegames-bot-quality-review-2026-10-29.md` section 3.1 (all except #7 shield).

## What exists

- `core/bot/BotMelee` (one per bot, pure): strafe goal, aim error, miss point, sprint/w-tap, jump-crit/jump-reset, retreat
  with hysteresis, sumo edge logic, KOTH spread point. Random and tick clock are injected; block checks are predicates.
- `core/bot/BotKitUse`: golden apple, bow, ender pearl, from the kit actually held. Each fires the normal Bukkit event.
- `core/bot/BotCombatSettings`: reads `bots.combat.*`. `BotDifficulty.profile()` holds the per-tier numbers.
- `BotPersonality`: `aggression`, `strafeBias`, `jumpiness` (0..1, stable per UUID).
- `BotService.pvpCombat` replaces the generic melee branch for **duels, kitpvp and koth** when the target is a player.
  Skywars, Bedwars, co-op, dodgeball and everything else keep the old branch untouched (Bedwars/Skywars movement is
  owned elsewhere; their `navigate` was not edited).
- `KothGame.botHill()`: active rotating `hillN`, else `hill`, else a lone `hill1`.
- `AdaptiveBotSkillStore`: cold start is 3 results (was 6) and cold-start tier is EASY (was NORMAL).

## How a PvP think behaves (4 Hz)

1. Look at the target with a yaw/pitch error (re-sampled every 1.5-3 s); Easy aims at the torso. Reaction time on every
   new engagement is tier base + 0..jitter ticks.
2. Kit (if `kit-use`): below 8 hearts and (backing off or target over 5 blocks) eat a golden apple; non-Easy bots with a
   pearl throw it when the target is over 16 blocks away or the bot is cornered while retreating; with bow and arrows
   at 8-24 blocks it draws one second, fires, and strafes while shooting. OITC keeps its own bow code (excluded here);
   crystal/mace keep `DuelBotCombat` (the facing they get is the new aimed look).
3. Otherwise melee: holds a sword (re-selects only if the held item is not one), jump-crit (swing held back 10 ticks so
   the hit lands on the way down) or jump-reset after being hit, sprint while closing and on the hit then released for
   one think. A failed hit roll still swings, at a point beside the target. Real attacks need cooldown 0.9 in modern combat.
4. Movement: retreat (health at or below the tier threshold and holding an apple, max 6 s) > sumo edge logic > chase over
   3.5 blocks > strafe 2-3 blocks sideways (side flips every 20-40 ticks, per-bot phase, destination and midpoint checked
   with `GroundNavigation.walkable` at the bot's own level so it never steps off a ledge) > hold.
5. KOTH: bots hold a per-bot spot inside the active hill; they fight a rival on the hill or within 4 blocks. In team games
   bots with aggression >= 0.5 hunt (engage up to 12 blocks) while the others hold.

Cost per bot per think: strafe 2 probes, retreat up to 6, sumo 2 x 16 edge probes (sumo only), plus the existing pathing.
Not done: a 1-2 tick combat sub-loop. Attack cadence is still quantised to the 5-tick think (see "left out").

## Config (`bots.combat.*`, all optional, defaults shown in `config.yml`)

`sprint: true`, `strafe: true`, `kit-use: true`, `retreat: true`, and per tier (`easy|normal|hard`): `melee-delay-ticks`
28/14/10, `hit-chance` 45/80/95, `projectile-delay-ticks` 40/20/12, `path-interval-ticks` 30/20/10, `aim-error-degrees`
6.0/3.0/0.5, `strafe-bias` 0.35/0.70/0.90, `sprint-chance` 0.40/0.75/1.0, `jump-crit-chance` 0/0.25/0.60,
`reaction-jitter-ticks` 5/4/2, `retreat-health` 0.20/0.30/0.30. `melee-delay`, `hit-chance` and `path-interval` only affect
the shared PvP core; other games still read the enum's original fields.

## Needs a human to check in-game

- Sprint knockback: does `Player#setSprinting(true)` on a Citizens NPC give sprint knockback, and does it stay set between
  thinks? If not, set `bots.combat.sprint: false`. Sumo depends on this most.
- Strafing near ledges, stairs, slabs and fences (`walkable` is conservative: a one-block step down is refused, so bots
  hold instead of circling there).
- Sumo edge: bot stays 3+ blocks inside, circles to put the target between itself and the void. Check on the real platform.
- KOTH on a rotating-hill map and a `hill`-only map; team mode hunter/holder split looks sensible.
- `Player#setJumping` gives a real hop on an NPC (Skywars relies on it) and the delayed swing actually crits.
- Golden apple: effects and absorption applied, stack shrinks; bow arrows and pearl behave; pearl in KOTH/FFA is not silly.
- First-round feel: new players now get EASY for three results per mode, Easy aim error 6 degrees and no jumps.

## Left out, and why

- Shield/block-hit (#7): out of scope as briefed.
- 1-2 tick combat sub-loop: needs a scheduler per bot; kept the 4 Hz think. Hard bots are limited by the 5-tick cadence.
- Melee aim error is cosmetic: `Player#attack` ignores facing, so the hit roll remains the real miss mechanism.
- Movement/strafe for Skywars, Bedwars, co-op: not applied (another builder owns Bedwars `navigate`; edge risk on islands).
- Team tactics beyond the KOTH hunt/hold split; potions; DuelBotCombat sharing strafe (it only inherits the aimed look).
