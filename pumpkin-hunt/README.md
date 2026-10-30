# PumpkinHunt

Standalone Paper plugin for the ReadyCraft Lobby (Paper 1.21.4, Java 21). Thirteen hidden jack-o'-lanterns; players right-click each one, progress is kept per player, and the last find triggers a one-off celebration and reward. It never moves, breaks or places a block, and has no dependency on PulseAddon, WorldGuard, Floodgate or Cumulus. Output is plain text, so Java and Bedrock read the same.

Ported from the PulseAddon `pumpkinhunt` module (SurvivalTest), with its own JSON progress store and `messages.yml`.

## Build

    ready-gate exec -- bash ./gradlew -p pumpkin-hunt build test

Jar: `pumpkin-hunt/build/libs/PumpkinHunt-0.1.0.jar`.

## Install

1. Drop the jar in the Lobby `plugins/` folder and start the server (this writes `config.yml` and `messages.yml`).
2. Place the 13 jack-o'-lanterns at the configured positions (world `world`).
3. Set `enabled: true` in `plugins/PumpkinHunt/config.yml`, then run `/pumpkins reload`.

Registered positions that do not hold a jack-o'-lantern are skipped, with a console warning.

## Commands

`/pumpkins` (alias `/pumpkinhunt`)

- `/pumpkins`: your progress and hints for the ones you have not found (never coordinates).
- `/pumpkins top`: the first 10 to find them all.
- `/pumpkins admin`: summary; `admin stats|list|reset <player|all>|enable|disable|add|remove|reload` (the same verbs as below, plus `enable`/`disable`, which write `enabled` to config and reload). The network `/admin` hub reaches it through the hidden `/admin-route hunt ...` command (permission `pumpkinhunt.admin`).
- Admin: `add <id> [hint...]` (look at the lantern, within 6 blocks), `remove <id>`, `list`, `reload`, `reset <player|all>`, `stats`.

## Permissions

- `pumpkinhunt.play` (default true)
- `pumpkinhunt.admin` (op)

## Config keys (`config.yml`)

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | Master switch. |
| `start`, `end` | `""` | Optional ISO date (`2026-10-24`) or date-time (`2026-10-24T09:00`). A bare end date includes the whole day. Blank is no limit. |
| `timezone` | `Europe/London` | Zone for `start` and `end`. |
| `broadcast-completion` | `true` | One chat line when someone finds them all. |
| `reward-commands` | `[]` | Console commands run once per player on completion; `{player}` is the finder's name. |
| `lanterns` | 13 entries | `id`, `world`, `x`, `y`, `z`, `hint`. Ids: `house_1`..`house_8`, `field_1`, `field_2`, `field_extra_1`..`field_extra_3`. |

`/pumpkins add` and `remove` rewrite `config.yml` (comments are lost). Hand edits take effect with `/pumpkins reload`, which re-reads `config.yml` and `messages.yml`.

## Files

- `progress.json`: who found what. Written off the main thread (5 second debounce) and flushed on disable.
- `messages.yml`: all player wording; missing keys fall back to the jar defaults.

## Behaviour notes

- The click listener runs at MONITOR and does not ignore cancelled events, so a right-click that WorldGuard denies (`use: DENY`) still counts. A click only reads the block.
- One counted click per player per lantern per second.
- Completion is recorded once, then: title, one harmless tagged firework (damage cancelled), reward commands, optional broadcast.
- The total is the number of registered lanterns, not a fixed 13.
