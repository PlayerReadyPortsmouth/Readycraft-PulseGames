# Team sizes, map capacity and bot difficulty (prepared update)


Competitive combat games now expose solo plus doubles, triples and squads. Existing
mode IDs remain valid. Duels variants include `classic-doubles`, `op-doubles` and
`sumo-doubles`, with equivalent `-triples` and `-squads` variants. Duels always has
**two opposing teams** and best-of-rounds scoring; losing one teammate does not lose
the round. KitPvP team variants combine teammate kills. Skywars solo uses one-player
island teams; Bedwars adds `solo` and `triples` alongside `duos` and `quads`.
KOTH, Dodgeball and Lucky Pillars also expose team-size variants of their solo rules.
Prop Hunt keeps seeker/hider roles and Party Games keeps its rotating individual
competition; these are not interchangeable with symmetric combat teams.

In the Setup Book, **Player limits** now offers defaults plus separate minimum,
maximum and team count for each enabled mode. The map controls the available
opposing teams (2–32), limited by spawn count and required game features. Place one
spawn per team/base, not one per player. Bedwars needs a matching bed region per
team. Duels needs two sufficiently large team spawn areas. Build islands/pillars
large enough for the chosen team size.

Example: one six-base Bedwars map supporting solo, doubles and triples:

```yaml
modes: [solo, duos, triples]
min-players: 2
max-players: 18
settings:
  team-count: 6
  modes:
    solo:
      min-players: 2
      max-players: 6
    duos:
      min-players: 4
      max-players: 12
    triples:
      min-players: 6
      max-players: 18
```

The engine may start with fewer occupied teams than the map's maximum. To require
all six full teams, set minimum players to 6, 12 and 18 respectively. Queues skip
maps that cannot meet a mode's minimum/capacity; a forced start cannot invent
missing bases. Existing arena mode lists are preserved, so your three existing
Duels maps do not automatically opt into doubles. To use one, enable the new mode
and explicitly set its capacity, for example:

```
/pulse setup set modes.classic-doubles.min-players 4
/pulse setup set modes.classic-doubles.max-players 4
/pulse setup set modes.classic-doubles.team-count 2
```

These commands act on the active setup session; the book does not import existing
saved definitions. Preserve templates and use the existing recapture workflow.

Bot difficulty: `bots.difficulty: easy|normal|hard` sets the default; arena
`settings.bot-difficulty` overrides it. The Setup Book cycles the three levels.
Difficulty changes attack/throw timing, melee accuracy and path update cadence;
health, reach and damage are unchanged. Villager Defense Easy/Hard now have a
prepared co-op controller, using only tracked wave zombies and the Mayor's guard
position; existing combat controllers also recognise their team-size variants.
Bedwars, Skywars and other objective bots remain outstanding. Team modes do not
imply bot support. Global/arena enablement and existing canary permissions still
apply. Live configuration and server restarts require separate approval.
