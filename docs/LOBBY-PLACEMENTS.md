# Lobby placements

Everything here is typed in-game by an admin (`pulsegames.admin`), works from Java and Bedrock, and is saved in
`plugins/PulseGames/lobby.yml`. Edit that file or run `/pulse reload` to rebuild what is placed. `/pulse lobby`
prints a help page. Stand where you want a thing to go, then run its command.

Needs: Citizens on the server (already installed on MinigamesTest) for the game NPCs. Boards, chest and parkour need nothing else.

## 1. Game selector NPCs

`/pulse lobby npc <game> [mode]` puts an NPC where you stand, turned to face back towards you, with a floating
name and a live "N in queue · M playing" line (refreshed every 2 seconds). Right-click (or tap on Bedrock) opens
that game's mode picker, or joins the queue straight away if you gave a mode.
Mistake? Stand next to it: `/pulse lobby npc remove` (nearest within 3 blocks). `/pulse lobby npc list` shows all.
Skins: `lobby.npcs.<game>.skin` in config.yml (an account name); each game has a sensible default.

Copy and paste one per spot, walking to a new spot between each:

```
/pulse lobby npc quake
/pulse lobby npc dodgeball
/pulse lobby npc dropper
/pulse lobby npc spleef
/pulse lobby npc tntrun
/pulse lobby npc blockparty
/pulse lobby npc avalanche
/pulse lobby npc volcano
/pulse lobby npc koth
/pulse lobby npc duels
/pulse lobby npc kitpvp
/pulse lobby npc parkour
/pulse lobby npc elytra
/pulse lobby npc karts
/pulse lobby npc skywars
/pulse lobby npc bedwars
/pulse lobby npc buildbattle
/pulse lobby npc deathrun
/pulse lobby npc villagerdefense
/pulse lobby npc readyplayerz
/pulse lobby npc luckypillars
/pulse lobby npc prophunt
/pulse lobby npc supermarket
/pulse lobby npc partygames
```

To send people straight to one mode, add it, for example `/pulse lobby npc bedwars solo`. Tab-complete lists the modes.

## 2. Leaderboard boards

`/pulse lobby board <weekly|alltime> <game|network> [wins|kills|tokens]` places a hologram at your position
(title and top 3, refreshed every minute). Weekly boards start again each Monday (ISO week). Token boards are
network only. Remove the nearest with `/pulse lobby board remove`; `/pulse lobby board list` lists them.

```
/pulse lobby board weekly network wins
/pulse lobby board alltime network wins
/pulse lobby board weekly network kills
/pulse lobby board alltime network tokens
/pulse lobby board weekly network tokens
/pulse lobby board weekly quake wins
/pulse lobby board weekly dodgeball wins
/pulse lobby board weekly dropper wins
/pulse lobby board weekly spleef wins
/pulse lobby board weekly tntrun wins
/pulse lobby board weekly blockparty wins
/pulse lobby board weekly avalanche wins
/pulse lobby board weekly volcano wins
/pulse lobby board weekly koth wins
/pulse lobby board weekly duels wins
/pulse lobby board weekly kitpvp wins
/pulse lobby board weekly parkour wins
/pulse lobby board weekly elytra wins
/pulse lobby board weekly karts wins
/pulse lobby board weekly skywars wins
/pulse lobby board weekly bedwars wins
/pulse lobby board weekly buildbattle wins
/pulse lobby board weekly deathrun wins
/pulse lobby board weekly villagerdefense wins
/pulse lobby board weekly readyplayerz wins
/pulse lobby board weekly luckypillars wins
/pulse lobby board weekly prophunt wins
/pulse lobby board weekly supermarket wins
/pulse lobby board weekly partygames wins
```

Kills boards only fill for games that record kills, e.g. `/pulse lobby board alltime kitpvp kills`.

Weekly and token counts start from the day this version goes live; older history only appears on all-time boards for wins and kills.

## 3. Daily reward chest

Look at a chest or barrel (within 6 blocks): `/pulse lobby chest`. Players open it once a day (resets at midnight server time)
for the daily bonus (`economy.daily-bonus`, 100) plus a random extra (`lobby.chest.extra-min` to `extra-max`, 5 to 25 tokens),
with the lid animation, particles and a sound. Opening it again shows the time left. `/pulse lobby chest remove` unsets it.
This is separate from the first-login bonus, so a player can collect both each day. There is no cosmetic shard in the
catalogue, so the extra is tokens only.

## 4. Lobby parkour

Look at the block where each plate should go (the plate is placed on top of it), in the lobby world:

```
/pulse lobby parkour start
/pulse lobby parkour checkpoint
/pulse lobby parkour end
/pulse lobby parkour board
```

Repeat `checkpoint` for each checkpoint, in course order. Stepping on the start plate starts a timer in the action bar;
checkpoints must be taken in order; falling below the lowest plate (minus 2 blocks, or `lobby.parkour.fall-y`) sends
the runner back to the last checkpoint; the end plate records the time, keeps a personal best, and feeds the top-5
board. Leaving the lobby world cancels a run. `/pulse lobby parkour status` checks the course;
`/pulse lobby parkour reset` clears the plates and the board's place in lobby.yml (placed plate blocks stay in the world).

## Suggested order

1. `/pulse lobby npc ...` for each game, along the walls players walk past.
2. The network boards near the spawn, then a weekly board next to the games it covers.
3. The chest, then the parkour (start, checkpoints, end, board).
4. `/pulse lobby npc list` and `/pulse lobby board list` to check nothing is doubled.
