# Experimental PulseContent Prop Hunt

Both `content.prophunt.enabled` in PulseGames and `minigames-visuals.enabled`
in PulseContent default to false. PulseGames `content.prophunt.props` must name
allowlisted PulseContent catalogue IDs. Every participant must be in the feature
trial and must have loaded the active content revision. Configured custom mode
fails clearly when these conditions are absent; it never substitutes helmet props.
With custom mode disabled, the existing vanilla helmet game remains available.

Companion source: `/mnt/data/worktrees/ReadyCraft-pulse-content-minigames`, based on
PulseContent PR26 head `0e33e5ce3245c4ec99dbe75bfd61c6d2e6d9cba9`. It supplies
minigames API version 1 and requires Paper 26.2 / Java 25. A server on the existing PulseGames Paper
1.21.4 / Java 21 source baseline cannot load that companion. The deployed
Minigames server version was not inspected. Neither this change nor the
companion source authorises a server upgrade or production installation.

PulseContent owns content compilation, Java/Bedrock resource packs, session
readiness and visual lifetime. PulseGames calls its public versioned API, attaches
a visual to each human hider and closes the handle on death, infection, quitting,
round end or cleanup. No Mythic encounters, AI, loot, locally invented resource
packs or private reflection are used. PulseContent receives the owning plugin
and a fixed list of viewers. New or departing viewers cancel the custom round;
joining spectators are not silently allowed to watch an incomplete disguise.
Readiness/provider loss cancels the round instead of continuing with an invisible
or partly visible hider. Rendering checks run each second in the game and every
server tick in the companion; client event timing remains an acceptance check.

This first slice supports **moving player-attached models with the ordinary
player hitbox**. It does not implement settled block locking, compact shape
hitboxes or NPC props. All accepted models must have no BetterModel hitboxes.
The companion rejects existing BetterModel ownership. A small model does not
shrink a player's server collision or damage target.

Model authoring stays in PulseContent. Its current entity catalogue requires
`idle`, `walk`, `attack`, `hurt` and `death` animation entries, even for a static
visual; an ordinary Blockbench cube export alone is insufficient. Supply an
original player-sized barrel prop with those tracks and no hitbox/seat bones,
compile it through the existing compiler, and trial its namespaced catalogue ID.
No sample is labelled compatible until it passes that actual compiler.

Required acceptance before enabling: Java hider/Bedrock seeker and reverse;
movement, crouch, first/third person, body/equipment/name visibility; equal combat
reach and interaction routing; pack rejection and reconnect; mid-round spectator
or departure; infection, elimination and reset cleanup; provider shutdown and
reload. Current unit tests cover readiness/rejection/ownership boundaries, not
rendering. The existing PulseContent encounter trial does not establish player
disguise parity. This source prototype is not deployed or declared client-ready.
