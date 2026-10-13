# Editing worlds and concurrent matches

Prepared in PulseGames 0.3.9; not deployed.

Editing worlds opened through `/pulse world` (the existing world-load command),
worlds named `rc_build_*`, and PulseGames void-marked build worlds are saved and
unloaded 15 seconds after the last player leaves or disconnects. Re-entering
restarts the timer. Reopening uses the saved world; terrain is never deleted by
the editing-world reaper. In-memory setup selections reconnect to the reopened
world. Publishing a playable template still requires the Setup Book Save action.

The primary world, configured lobby, active matches and `build-worlds.keep-loaded`
are excluded. Template capture holds the editing world loaded until copying ends.
`build-worlds.idle-unload-seconds: 0` disables the reaper. Other plugins can cancel
unloading; retries respect the configured delay. Other plugins may also reload
worlds or hold their own references.

The same mode and map can run simultaneously: each match has separate game logic,
a full random UUID instance ID and a separate copy of terrain with fresh world
identity. Match changes do not alter the template or another match. Matchmaking
first fills joinable queues, then creates another instance up to
`instances.max-concurrent` (default 20; this is a cap, not a capacity guarantee).
Cancelled matches whose asynchronous clone finishes later immediately dispose of
that clone. Existing finished-match cleanup and abandoned-queue timeout remain.

Validation: focused lifecycle/storage/setup tests cover saved unload, occupancy,
keep-loaded exceptions, captures, refused unloads, distinct copies and late clone
cancellation. Live editing save/reopen and two simultaneous played matches remain
acceptance checks for the next approved rollout.

Separate known issue: PulseNetwork 0.1.2 eagerly deserialises a saved return Location
whose world may be unloaded, producing `unknown world` on startup. A helper update
must preserve coordinates independently of loaded world objects and resolve an
approved persistent destination at return time. This draft does not change that
helper or require restarting other servers.
