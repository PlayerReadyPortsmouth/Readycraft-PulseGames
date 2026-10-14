# PvP bots, kits, diagnostics and progression

Prepared extension to PulseGames draft13 and PulseContent draft35, 14 October
2026. Not deployed. Attribution: AurorasChaos (on behalf of Player Ready).

## 1. Crystal and Mace Duels bots

Use `/duels` to choose modern combat, Crystal or Mace, the team size and your
personal compatible kit. Bots now support both rule sets. Each real player still
chooses their own kit; no opponent's selection overwrites yours.

Crystal bots use finite obsidian and crystals, normal placement/protection events,
registered crystals and real player attacks for detonation. They use proximity,
health and teammate checks before attempting a blast, with sword fallback and
finite off-hand totem swaps. These are conservative heuristics, not a guarantee
that an explosion cannot hurt the bot. Crystal maps still need a configured combat
region and suitable terrain. Check block/crystal removal at each round reset.

Mace bots select the mace, launch a real downward wind charge from finite inventory
on an open flat launch area, and attempt strikes while actually falling. No
teleport, fabricated fall distance or extra damage is applied. Ground attacks
provide a fallback. Check charge consumption, native lift, attack cooldown, ceilings,
ledges and fall damage. Citizens/Java/Bedrock physics still need live acceptance.
Advanced healing, pearls and deliberate aerial steering are not implemented.

Use `/pulse practice start <game> <mode-id> <map-id> easy` and repeat on normal/hard.
Copy exact mode/map IDs from command completion. Normal queue fill caps and map
bot opt-ins remain. Test classic/regular modes too to catch regressions, then
check team targets, friendly fire, protected placement and concurrent map copies.

## 2. Personal kit choices

Duels keeps the existing combat style -> discipline/team size -> personal kit
flow. Long lists now paginate, with equipment previews and compatibility labels.
An unavailable saved kit is explained before fallback. KitPvP also paginates and
shows the equipment before selection. Closed/replaced menus reject delayed clicks.

Choose different compatible kits on two accounts, leave/rejoin and restart during
an approved test window. Confirm each account retains its own selection, classic
and modern profiles remain separate, and team-size changes preserve the same
style/discipline preference. Choices use the existing backend economy store and
its autosave/shutdown behaviour; this pass does not migrate kit preferences to SQL.

## 3. Diagnose before changing difficulty

`/pulse bots inspect` reports current objectives, targets, navigation issues and
calibration explanations. Per-player/mode profiles retain their existing bounded
history and neutral starting period; difficulty is frozen for the round. Fixed
map overrides and fixed practice difficulty remain available. No damage, health,
reach or movement-stat advantage is granted.

Practice reports now identify repeated same-bot navigation trouble, observed
difficulty mix and early tester elimination. A blocked route is a map/controller
investigation, not evidence that a bot needs more damage. Early elimination is a
prompt to compare controlled repeats, not proof that novice balance is wrong.
Observed objectives do not prove successful completion; check scoring and end
conditions. Practice results never train adaptive profiles or earn competitive
rewards. No live balance measurements have been collected in this pass.

## 4. Shared progression and cosmetic rewards

PulseContent owns SQL counters and awards. PulseGames sends only eligible human
participants' completed results through a durable retry outbox. Replays, connection
failures and lost acknowledgements cannot double-count committed results. Practice,
NPCs, observers and forced/aborted endings are excluded. The frozen starting-roster
threshold remains: large maps permit equal humans/bots; four slots or fewer
require a human majority. In particular, 3 humans + 1 bot counts and 2 + 2 does not.

`/challenges` displays configured milestones and award dates. Initial configurable
examples unlock a subtle trail after three games, a weapon effect after five Duels
wins, and a crown after 25 wins. Named events can award their own cosmetic or
keepsake. Event keepsake ownership is supported; physical trophy models/display
remain planned. Survival still applies its existing category/item restrictions.

This feature defaults off in both plugins until the additive SQL migration and
configuration are explicitly applied. See PulseContent `docs/minigame-progression.md`
for migration, immutable goal IDs, event tags and recovery. No SQL/live configuration
has been changed. Back up `progression-outbox` and SQL receipts; do not delete them
to resolve an outage. Test duplicate results, two backends, a SQL outage and a
restart before acknowledgement, then verify one count/award and Survival ownership.
