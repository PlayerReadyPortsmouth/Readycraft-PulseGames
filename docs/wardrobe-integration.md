# Shared wardrobe match context

PulseGames uses the optional PulseContent wardrobe API version 1 independently of the prop-disguise API. Older, missing or disabled providers remain compatible; a cosmetic provider failure cannot prevent player release or inventory restoration.

After a player reaches the arena waiting area, PulseGames assigns the actual game ID with phase `playing`. This deliberately suppresses ordinary lobby cosmetics while waiting inside a match world. Starting a match refreshes the same context. At final match completion each human receives `ended`; participants still present get one `finish_event` request, and only the winners supplied by the game logic get one `win_animation` request. These requests do not grant rewards. Bots are excluded. The PulseContent policy still decides whether either effect is available and permitted.

Leaving or match cleanup clears the owner-scoped context independently of combat, sidebar and inventory cleanup. Existing legacy cosmetics and fireworks are unchanged. No cosmetic is enabled on a game simply because it supplies context: explicit PulseContent policy allow rules and an equipped, owned entry are still required. The default policy permits competitive effects only in Duels and KitPvP, and denies Prop Hunt during play.

Integration requires a PulseContent build offering `wardrobeApiVersion() == 1`, `wardrobeContext(Plugin, Player, String, String)`, `clearWardrobeContext(Plugin, Player)` and `wardrobeCelebration(Plugin, Player, String)`. Deployment and Java/Bedrock acceptance remain separate from this source change.

## Authority and phase limitations

Only the owning PulseGames plugin supplies these contexts. The API calls do not trust player-provided game IDs or categories. Completion originates in `GameInstance.end`, not a chat command or client event. A participant who leaves before completion gets no celebration request. Repeated calls to end after the instance enters ENDING or RESETTING are ignored.

This integration reports waiting and active matches as `playing`; it does not expose a separate waiting phase or individual round wins. It reports final match completion as `ended`. It does not configure editing sessions: PulseContent must keep unknown worlds and editing denied independently. A server restart does not resume an in-progress match or replay cosmetic events. Changes to PulseContent policy may suppress requested effects without changing ownership or equipped selections.
