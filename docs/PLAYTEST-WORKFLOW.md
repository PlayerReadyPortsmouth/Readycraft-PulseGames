# Parties, results, rejoining and bot practice

Prepared candidate, 14 October 2026. Not deployed. These instructions apply after
an approved rollout of PulseGames 0.3.12 and PulseNetwork 0.1.4 on the proxy and
participating Paper servers. Existing bot enablement, map opt-ins and trial access
still apply. Attribution: AurorasChaos (on behalf of Player Ready).

## 1. Bring friends from different servers

Use `/party invite <name>` and `/party accept` (or `/networkparty` if another plugin
owns `/party`). The proxy keeps one party across backend transfers. The leader
opens `/queues` and chooses an advertised queue; `/joinqueue <token>` is the typed
alternative for either client. Everyone must be online and the entire group must
fit. Parties have a maximum of eight players.

The destination admits the group only when everyone has arrived. A failed transfer
returns each moved player to their own previous server where possible. A party
already together on MinigamesTest can use the ordinary game menu. A party split
across servers uses advertised queues; this version does not create a new remote
queue from Survival. Parties are held in proxy memory and do not survive a proxy
restart. Disconnecting leaves the network party and promotes the next member when
the leader leaves. A pre-existing backend-only party must be left before importing
a conflicting network party.

Test with one account in Lobby and one in SurvivalTest, then repeat with both
already on MinigamesTest. Verify whole-party placement in doubles, refusal of an
oversized party, and each account's original server and safe position on return.
Also test a closed queue, a failed connection and a member disconnecting in transit.

## 2. Choose what happens after a round

Results show outcome, team score, your score/kills/deaths, actual end-of-round token
credit and competitive eligibility. `/results` opens the inventory menu; commands
`/results again`, `/results games` and `/results return` provide the same actions.
The ordinary inventory interface and typed commands work without Java chat clicks.

Play again queues the same mode through ordinary party admission. Change game
opens the selector. Return leaves for the saved source server or local lobby.
The return ticket is held briefly while choosing; abandoning the menu still allows
normal cleanup and eventual return. Reopening results never pays rewards again.
Practice results offer Repeat practice and pay no match rewards.

Verify dead members of a winning team receive the team outcome; spectators and
bots must receive no competitive rewards. Repeat the display/action commands and
confirm neither tokens nor records increase. Use an old result after joining a
new match and confirm it cannot remove you from that match.

## 3. Recover from a disconnect

A waiting/countdown seat is reserved for 30 seconds by default
(`rejoin.grace-seconds`, bounded 0-60). Countdown pauses during that reservation,
including forced-start requests. Reconnecting restores the waiting seat.

A live-round disconnect follows the game's existing departure/elimination rules.
A quick reconnect may watch the still-running match; it never resurrects a lost
life or restores reward eligibility. If the round ended, reservation expired or
server restarted, normal lobby/source-return handling applies. There is no durable
match recovery across restarts.

Check inventory restoration, a disconnect with a respawn pending, the last human
leaving, a late reconnect, and cloned-world/NPC cleanup. Check both direct backend
rejoins and joining through the primary proxy.

## 4. Run a private bot scenario

An admin with existing trial access can run:

```text
/pulse practice start <game> <mode> <map-id> <easy|normal|hard> [bots]
/pulse practice report
/pulse practice end
/pulse practice repeat
/pulse bots inspect
/pulse bots inspect <bot-name-or-UUID>
/pulse diagnose <match-reference>
```

This loads an isolated copy of the selected configured map for one tester. It
never enters public matchmaking or queue announcements. Choose a bot count that
meets the map minimum and the usual fill rule: at most half capacity on maps above
four slots, full-fill exception at four slots or fewer. Global bot caps still
apply. Large team modes may need a smaller test configuration to be startable by
one tester; practice does not bypass those rules.

Repeat reuses the game, mode, map, difficulty and requested bot count. It does not
seed Minecraft randomness or guarantee identical loot. Practice changes no
competitive records, match tokens or adaptive skill history.

Reports sample every five seconds and list observed objectives, stalled samples
and blocked-route samples. These are observations, not counts of distinct bugs or
proof of safe navigation. Match events and the practice summary are saved in the
existing bounded `plugins/PulseGames/match-reports` logs. Record the reference
alongside client observations in the tracker. A loading timeout, failed admission,
owner departure or the 30-minute practice limit cleans up the private instance.

## 5. Test the island-game bots

Skywars bots now attempt checked adjacent height changes, straight stairs/slabs
and ordinary jump physics, plus bows with real arrows, draw time, durability and
normal protection events. They still stop when they cannot establish a safe route.
No long void jumps, pearls or potion tactics are claimed.

Bedwars bots collect actual nearby resource drops, buy from the ordinary shop with
finite currency, build bounded defence around their own bed, bridge using inventory
blocks and approach public enemy-bed objectives. Their bow/melee actions retain
visibility and team checks. Bed attacks use timed ordinary block breaking; stopped
or cancelled actions must not destroy the bed's logical state.

Start with a small two-team map. Check generator pickup cancellation, full
inventories, finite purchases, armour upgrades, bed-cover placement cancellation,
bridge exhaustion, stair orientations, low ceilings, bow ammunition and allies in
combat. Confirm protected bed breaks are refused, NPC/world cleanup runs at the
end, and two concurrent copies never affect each other or the saved template.
Actual Citizens movement and Java/Bedrock acceptance remain required.
