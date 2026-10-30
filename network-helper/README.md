# PulseNetwork

## Cross-server parties — prepared draft, not deployed

PulseNetwork **0.2.2** credits **AurorasChaos (on behalf of Player Ready)**. The current draft adds proxy-owned `/party` (also `/networkparty`) invitations, acceptance, list, leave, disband and private party chat. Invitations expire after 60 seconds; parties hold up to eight online players. Java has a clickable invitation; Bedrock can type `/party accept` or `/party deny`. Leaving the proxy removes a member and promotes another leader if needed. Backend transfers preserve membership; proxy restarts do not preserve parties yet.

The leader selects an advertised game through `/queues` or `/joinqueue <id>`. Every party member transfers with their **own** saved origin. The destination receives one authenticated group admission only after everyone arrives. Transfer failure or timeout cancels the group and returns transferred members where possible; late successful transfers also return. The destination rejects conflicting local parties rather than overwriting them. A leader acknowledgement completes the whole proxy request atomically. Game capacity, team packing, preview access and admission remain PulseGames decisions.

This requires matching draft PulseNetwork Velocity/Paper and PulseGames builds. Backend command forwarding uses `PulseNetwork.networkPartyCommand(Player,String[])`; membership snapshots call `PulseGames.networkPartyState(Player,UUID,List)`; admission calls `PulseGames.joinNetworkParty(Player,String,List)`. Signed snapshots refresh every five seconds and permit local menu guards while members are elsewhere. Use `/queues` for cross-server parties; creating a brand-new unadvertised queue directly from another server is not implemented in this increment. Local parties and network parties must not silently overwrite one another.

Reconnects on the destination restore the signed return ticket without re-queuing or forcing a transfer home. PulseGames can retain a waiting seat or allow a spectator rejoin; otherwise normal return polling sends the player home. Missing resume acknowledgement has a 15-second fallback.

Group transfers and source-position restoration still require live Java/Bedrock acceptance. No rollout or restart is authorised by this draft. Existing details below describe the original helper baseline and deployment history, not proof of current live configuration.

## Network tab (0.1.5) — prepared draft, not deployed

A proxy-owned player list, so it works on every backend, including ones without the Paper helper. It is **off by default** (`tab.enabled=false`). Requested by Aurora: current server, every player on the network, pings, and the current server's TPS.

### Player experience

- Everyone on the network appears in the tab list: the viewer's own server first, then other servers in the configured order, players alphabetically within each server. With a 1.21.2+ client this order is enforced through the entry list order; older clients ignore it and sort by name.
- Names are `[prefix] name` with the LuckPerms-Velocity prefix/suffix when LuckPerms is installed (soft dependency, read by reflection; without it, plain names). Bedrock players carry a configurable marker (default `[B]`, from Floodgate). `(AFK)` and `[In a game]` tags come from the backend status. Ping is the proxy's ping to each player, rounded to 25 ms so jitter does not cause constant updates.
- Optional per-server header rows (`— Survival (12) —`) are fake entries and are off by default (`tab.group-headers`). Check them on Bedrock before enabling.
- Header and footer are MiniMessage templates. Lines that render blank (for example TPS when the server has not reported) are dropped. Players with `pulsenetwork.tab.staff` get an extra footer line with their Java/Bedrock flag and version. Bedrock viewers get the same text with hover, click and obfuscation removed.
- Players with `pulsenetwork.tab.hidden` (or flagged hidden by their backend through Bukkit `vanished` metadata) are left out of the roster and counts. Velocity has no vanish of its own. Hidden players on the viewer's own backend are the backend's to hide: the proxy never removes entries it did not add.

### Config keys (proxy `network.properties`)

| Key | Default | Meaning |
|---|---|---|
| `tab.enabled` | `false` | Master switch (also needs `enabled=true` and a secret). |
| `tab.server-order` | empty | Comma list; then remaining servers alphabetically. |
| `tab.excluded-servers` | empty | Servers neither shown nor managed; their players keep the backend's own list. Only servers in `backends` ever take part. |
| `tab.server-names` | capitalised id | `survival=Survival,creative=Creative`. |
| `tab.group-headers` / `tab.group-header-format` | `false` / `<server> (<count>)` | Fake per-server header rows. |
| `tab.header`, `tab.footer`, `tab.staff-line` | built in | MiniMessage; `\n` separates lines. |
| `tab.tips`, `tab.tip-seconds` | empty, `30` | `a\|b\|c` tips rotate by time. |
| `tab.bedrock-marker` | `<aqua>[B]</aqua> ` | Prefix for Floodgate players. |
| `tab.list-order-higher-first` | `true` | Flip if a client sorts the other way round (see below). |
| `tab.min-interval-ms` | `1000` | Tick period and per-viewer minimum gap between updates (250 to 30000). Ping refreshes every 5 s; joins, leaves and server switches refresh immediately (with a 4 s settle after a switch). |

Placeholders: `<player>`, `<server>`, `<server_id>`, `<network_online>`, `<server_count>` (visible players on the viewer's server), `<server_online>`, `<tps>`, `<mspt>` (empty when unknown), `<counts>`, `<status_line>` (empty when unknown), `<tip>`, `<online_SERVERID>` (lower-case ids), `<client>`, `<version>`. Player-controlled text is inserted as plain text, never parsed.

Paper backend (`config.yml`): `tab-status.enabled` (default `false`) sends a signed STATUS message every 5 s: TPS (1 min), MSPT (5 s average), online count and flags only for players that have one (AFK from metadata keys in `tab-status.afk-metadata-keys`, default `afk`/`AFK`; hidden from `vanished` metadata; in-match from `PulseGames.networkPlayerBusy`, which gives no game name). It follows the transport rules above: HMAC, 30 s freshness, nonces, at most 12 fields, 1,024 characters per field, 8 KiB packet, reports capped at 168 flagged players (hidden first). The proxy accepts it only from the current backend connection of an allowlisted, non-excluded server and treats a report older than 20 s as unknown. The NeoForge bridge does not send status (its servers show blank TPS) and stays at 0.1.0.

Nothing logs player lists, names from the roster or secrets; a failed update logs one generic warning a minute.

### Tab columns (`tab.layout=columns`)

Opt-in, off by default. Prepared draft, not deployed, not tried on any real client. One 20-row column per server on Java 1.21.2+: row 1 is the server header, then its players (sorted as in the grouped list), then blank filler entries up to row 20. The viewer's own server is the first column, then `tab.server-order`. Sorting uses `TabListEntry#setListOrder` (`80 - slot`, flipped by `tab.list-order-higher-first`), so a join or leave only moves entries inside its own column.

| Key | Default | Meaning |
|---|---|---|
| `tab.layout` | `list` | `list` (grouped list as before) or `columns`. Anything else refuses to load. |
| `tab.columns.show-empty` | `false` | Give participating servers with nobody online an empty column (header plus fillers). |
| `tab.columns.filler` | empty | MiniMessage text for filler rows. Empty gives a single space (an absent name would fall back to the hidden profile name). |

`tab.group-header-format` still styles the column headers; `tab.group-headers` is ignored in columns mode (headers are always shown).

Fallback to the grouped list, per viewer, when any of these hold: Bedrock viewer; Java client below 1.21.2; more than 4 servers to show; or the viewer's own server would overflow its columns (the backend owns those entries, so the proxy cannot hide them). Overflow rule: a server with more than 19 players takes extra columns while fewer than 4 are in use in total; if none are spare, a server other than the viewer's shows its first players and a grey "+n more" in its last row.

Fillers have fixed ids per server and block position and profile names `~p<n>` (they sort after real names). They are added once and kept (cached per viewer through the same owned-entry diff as the other rows), never resent each tick.

Packet volume (3 servers, one viewer, `L` players on the viewer's own server, `R` on the others): first join or server switch sends about `60 - L` entry adds (3 headers, `R` remote players, the rest fillers) plus up to `L` list-order updates for the backend's own entries, at most 80 entries in all. Steady state sends nothing per tick (ping changes are rounded to 25 ms and still refresh every 5 s). A player joining a column sends one add, removes one filler and re-orders only the later players in that column (at most 19 updates); a leave is the mirror image. Other columns are untouched.

Known limits: filler and header rows show a ping bar and a default head; the 80-entry Java limit and column-major fill are assumptions from the design note, not yet confirmed on a client. Backend entries for hidden or non-participating players cannot be reordered and may appear in the first column.

Acceptance checklist:

- [ ] Java 1.21.2+: one column per server, own server first, header on top, filler rows invisible (blank), join and leave do not reshuffle other columns.
- [ ] Java 1.21.2+: a 20th player on a server with a spare column starts a second column; with no spare column the viewer's own server falls back to the grouped list without errors, others show "+n more".
- [ ] Java 1.20: grouped list, same as `tab.layout=list`.
- [ ] Bedrock via Geyser: grouped list, plain-text headers, no ghost or filler rows.
- [ ] Switching `tab.layout` back to `list` and reloading leaves no stray filler rows (they are proxy-owned and removed).

### Conflicts and limits

- A backend that sets player-list names itself (EssentialsX on Creative, `change-playerlist`) **must have that turned off when the proxy tab is enabled for it**, or the two fight over display names. The simplest route is `tab.excluded-servers` for such a server until it is changed.
- Entries the proxy adds alongside backend-sent entries need a **Bedrock-through-Geyser acceptance check**. It cannot be proven from code.
- The pinned Velocity API (3.4.0-SNAPSHOT) exposes `TabListEntry#setListOrder`. Whether the running proxy version, Geyser and the vanilla client sort higher-first is unverified: confirm on a 1.21.2+ client and flip `tab.list-order-higher-first` if reversed. Clients before 1.21.2, and possibly Bedrock, order by name.
- Velocity clears the list on every server switch; the proxy rebuilds it within about a second.

### Install plan (needs separate rollout and restart approval)

1. Back up proxy and backend configs and jars. Install Velocity and Paper 0.1.5 on the agreed canary only.
2. Turn off backend player-list naming on canary servers (or exclude them). Set `tab.enabled=true`, order, names and, on canary Paper backends, `tab-status.enabled: true`.
3. Restart under the approved window; widen only after the checklist passes. Rolling back means the previous jars/configs with another approved restart.

### Acceptance checklist

- [ ] Java: own server first, others in order, ping bars, header/footer, TPS shown and blank-safe when a backend has no helper.
- [ ] Java and staff: staff line only with `pulsenetwork.tab.staff`; hidden players absent.
- [ ] Join, leave and server switch update within about a second for everyone; no flicker or duplicate entries.
- [ ] Bedrock via Geyser: entries and names render, marker shows, no hover or garbled text, no ghost or duplicate rows, `tab.group-headers` acceptable (or left off).
- [ ] Ordering on a 1.21.2+ client matches expectations (flip `tab.list-order-higher-first` if not); older client sorts by name without errors.
- [ ] Excluded or non-allowlisted server leaves the backend's own list untouched; no EssentialsX conflict.
- [ ] Proxy load acceptable with a full roster; logs contain no player lists or secrets.

## Roles (`/role`) and hidden-from-tab

Proxy-owned `/role` (alias `/networkrole`), available only when LuckPerms is loaded on the proxy. LuckPerms stays the single source of truth: a role is membership of a LuckPerms group, named in config. The command cannot touch any other group or permission.

| Command | Effect |
|---|---|
| `/role set <player> <role>` | Adds the role's group |
| `/role remove <player> <role>` | Asks for `/role confirm` within 30 seconds, then removes the group |
| `/role who <player>` | Roles and whether the player is hidden from the tab |
| `/role list [role]` | Holders by username, capped at 50 per role |
| `/role venues` | The five venue roles with their group and account count |
| `/role hide <player>` / `/role unhide <player>` | Sets or clears LuckPerms user meta `pulse.tab.hidden` |

Roles: `dev`, `admin`, `staff`, `venue:ports`, `venue:plym`, `venue:tru`, `venue:tau`, `venue:exet`. Players need to have joined the network once; names must match exactly (offline players are fine). Nobody can change their own roles.

### Who may do what

Decided by the sender's **direct group membership**, never by permission (admins hold `*`, which would pass every check). The highest group wins: dev, then admin, then staff.

| Sender | set/remove dev, admin, staff | set/remove venue roles | who, list, venues | hide, unhide |
|---|---|---|---|---|
| Console | yes | yes | yes | yes |
| dev | yes | yes | yes | yes |
| admin | no | yes | yes | yes |
| staff | no | yes | yes | no |
| venue or none | no | no | no | no |

### Config keys (proxy `network.properties`)

- `roles.groups`: role=group pairs, comma separated, e.g. `staff=crew,venue:tru=venue-truro`. Unlisted roles keep the defaults `dev=dev,admin=admin,staff=staff,venue:ports=venue-ports,venue:plym=venue-plym,venue:tru=venue-tru,venue:tau=venue-tau,venue:exet=venue-exet`. Group names are lower case; two roles cannot share a group. A bad value disables `/role` and logs why.
- `tab.hidden-players`: comma-separated exact names always hidden from the network tab.

Every change logs one proxy line, `ROLE <actor> set|remove <role> for <target>` (hide/unhide log `set|remove tab-hidden`), writes an entry to the LuckPerms action log, and tells online dev and admin players in chat.

### Hidden from the tab: behaviour change

`pulsenetwork.tab.hidden` is no longer checked, because the `*` wildcard granted it to every admin. A player is hidden only by opt-in: LuckPerms user meta `pulse.tab.hidden=true` (use `/role hide`) or the `tab.hidden-players` list. Backend-reported hidden flags still apply. **Players holding `*` are visible in the tab again** until someone hides them.

### Sync delay

The proxy sees a change immediately because the write goes through its own LuckPerms. Backends learn of it through LuckPerms SQL messaging, normally within a few seconds (not yet measured on this network). The tab picks the change up on its next refresh.

## Names, tags and styles

One pure renderer (`NameStyle`, in `common`) draws `[TAG] [12] name` for the proxy tab, the Velocity global relay and Paper local system delivery (`chat.local-system-delivery`). Prepared draft, not deployed.

| Role (`pulse.role`) | Tag | Colour |
|---|---|---|
| `dev` | `DEV` | aqua |
| `admin` | `ADMIN` | red to gold |
| `staff` | `STAFF` | green |
| `venue` | `VENUE` | yellow (generic: never a town name) |

- **Meta read** (LuckPerms `CachedMetaData#getMetaValue`, reflective, so group weight decides): `pulse.role` (set on groups) and `pulse.namestyle` (user meta, a style id). `pulse.venue` is for admin use and is never read or rendered. Permissions never decide a tag: an admin holding `*` shows `ADMIN` only because their group sets `pulse.role=admin`. Unknown roles and style ids are ignored.
- **Styles** (code, stable ids): solid `red orange yellow green aqua blue purple pink`; gradients `sunset ocean forest candy`; animated `rainbow shimmer pulse sparkle`. Player text is never parsed.
- **Level badge:** grey `[12]` before the name, from PulseGames via the soft `networkPlayerLevel(Player)` method, carried in the STATUS message (chunks starting `L`, up to 2 chunks, about 38 players per backend report; extra players simply show no badge). It needs `tab-status.enabled` on the backend and `tab.enabled` on the proxy. A proxy older than this change rejects a STATUS report containing level chunks, so upgrade the proxy first.
- **32-character rule:** the whole row (tag, badge, name, and in the tab the Bedrock marker, `(AFK)` and `[In a game]`) fits 32 visible characters. The name is never truncated; the badge goes first, then the tag.
- **Tab:** the LuckPerms prefix/suffix is only a fallback for players with no role tag. Animated styles advance every `tab.animation-ms`; rows of players without an animated style are identical every frame, so nothing extra is sent for them.
- **Bedrock** (Floodgate UUIDs, most-significant half zero): one static named colour per style and per tag, no gradients or animation; tags stay readable as text. Chat is always the static frame.
- **Config:** proxy `network.properties`: `tags.<role>=<MiniMessage>` (text capped at 6 characters; blank removes the tag), `tab.animation-ms` (default `1000`, 500 to 10000). Paper `config.yml`: `tags:` section with the same role keys.
- **Remote chat on backends:** `REMOTECHAT` now carries optional `role`, `styleId` and `level` after the original four fields (7 in total; empty strings when absent). Paper renders `[Global · Lobby] [DEV] [12] name: message` per viewer (Java colours, one flat colour for Bedrock, static frame) through the same `RemoteChat.line` the proxy uses, so both match character for character. Four fields (an older proxy) print the plain name; unknown roles, styles and odd levels are dropped. **Upgrade every Paper backend before the proxy:** an older backend ignores a 7-field message, so global chat would stop reaching it.

## Name style picker

`/namestyle` (aliases `/namecolour`, `/namecolor`) on every backend running PulseNetwork. Needs LuckPerms; without it the command says so politely. Prepared draft, not deployed.

| Command | Effect |
|---|---|
| `/namestyle` | Opens a 27-slot menu (Java) or a form (Bedrock) |
| `/namestyle list` | Prints unlocked and locked styles |
| `/namestyle set <id>` | Picks an unlocked style |
| `/namestyle preview <id>` | Shows a style as a chat line without changing anything. Dev and admin (direct LuckPerms groups `dev` or `admin`) can preview any style; everyone else gets "locked" for styles they do not hold |
| `/namestyle clear` | Removes your style |

- **Unlocked** means the player holds the LuckPerms permission `pulse.namestyle.<id>` (so a `*` holder has every style). It is checked again on every change, so editing the command does not help.
- **Selection** writes LuckPerms user meta `pulse.namestyle=<id>` through the LuckPerms API (old value replaced). LuckPerms records it in its action log and syncs it; the tab and chat change within the LuckPerms sync delay, and the confirmation says so. One change per 5 seconds per player.
- **Java menu:** unlocked styles first as coloured items showing the player's own name in that style, locked ones as grey panes ("Unlock by levelling up"), the current style glows, a barrier clears. **Bedrock:** a form with one button per unlocked style (name and a one-word colour hint) plus "Clear my style"; locked styles are listed in the text below. Bedrock without the Floodgate plugin falls back to the chest menu.
- **Config (`config.yml`):** `namestyle.enabled` (default `true`).

## Identity debugging (`/identity`)

Proxy command, registered with `/role` (needs LuckPerms on the proxy). Read-only; it changes no data.

| Command | Does |
|---|---|
| `/identity` | Your own identity |
| `/identity <player>` | An online player's identity. Dev, admin (direct groups per `roles.groups`) and the console only |
| `/identity refresh` | Dev, admin or console: rebuilds every tab list within a few seconds |

Output (plain text, readable on Bedrock): direct LuckPerms groups; `pulse.role` and the tag it gives (unknown roles are flagged as ignored); `pulse.namestyle` and how many of the styles the player holds (a `*` holder counts all of them); level from the latest STATUS (unknown when stale); hidden-from-tab; Bedrock; current server; then two previews, "Java viewer sees" and "Bedrock viewer sees", written as `{colour:text}` runs of the static first frame, e.g. `{dark_gray:[}{aqua:DEV}{dark_gray:]} {gray:[12]} {red:Ann}`. Java gradients show hex colours; Bedrock shows one named colour.

## Role menu

`/rolemenu [player]` for players directly in the LuckPerms group `dev`, `admin` or `staff` (group membership, never permissions). Pick an online player (heads, or a form on Bedrock) or type `/rolemenu <name>` for an offline one, then give a role, take one away (with a yes/no step) or "View roles".

It does not change roles itself. The backend sends a signed `ROLECMD` plugin message and the proxy runs `/role set|remove|confirm|who ...` **as that player**, so the proxy's gating, audit line, LuckPerms action log entry and confirm step stay the single path. The result arrives as normal chat. A backend cannot run a proxy command with `performCommand`, which is why the message exists; the proxy only accepts the four forms above, the fixed role list and valid names (`RoleMenuCommands`).

- Needs the backend `enabled: true` with the bridge secret, and a proxy with this change (an older proxy ignores the message, so nothing happens).
- **Config (`config.yml`):** `rolemenu.enabled` (default `true`).

## Local chat delivery (`chat.local-system-delivery`)

Symptom: players on the same backend cannot see each other's ordinary chat, while messages relayed from another server show fine. Since 1.19 player chat is signed with the Microsoft account key. Behind Velocity (and for Floodgate/Bedrock players, who cannot sign) clients can drop or hide player-chat packets whose signature chain does not validate. The proxy's relayed global chat arrives as unsigned system messages, so it is unaffected.

Paper key (`config.yml`, default `false`; turn it on per backend):

| Key | Default | Meaning |
|---|---|---|
| `chat.local-system-delivery` | `false` | Deliver local chat to each viewer as an unsigned system message. |
| `chat.local-format` | `<name>: <message>` | Only used if the event's own renderer fails. `<name>` and `<message>` are literal substitutions; nothing is parsed as MiniMessage. |

What it changes: in the existing final (MONITOR, non-cancelled) chat handler, after the global relay decision has been taken from the viewer set, the helper renders the line for each original viewer with the event's own renderer (so AdvancedChat, Essentials and similar formatting is kept), sends it with `sendMessage(Component)`, then clears the event's viewers so the signed packet is not also sent. Mutes, filters and channel/radius plugins have already trimmed the viewers, and no viewer is ever added. The console is one of the viewers and receives its single line the same way, so the log shows each message once. The global relay does not depend on the viewers afterwards: it uses the decision recorded before they were cleared. Modifying the viewers at MONITOR is deliberate: this plugin is the final consumer and nothing else should act on the event after it. Events handled by AdvancedChat's own rebroadcast are skipped, as for the relay.

Known limitation: clients lose the per-message report option and hover, and the message has no "secure chat" indicator. Cancellation by a later MONITOR listener no longer stops delivery.

## Original baseline

Prepared network helper, not installed on any live server. Three server-side artifacts:

- `paper/build/libs/PulseNetwork-paper-0.1.0.jar`: Paper backends.
- `velocity/build/libs/PulseNetwork-velocity-0.1.0.jar`: the primary Velocity proxy.
- `neoforge/build/libs/PulseNetwork-neoforge-0.1.0.jar`: Create / `aoc`, NeoForge 21.1.248 (Minecraft 1.21.1). No additional client mod.

Build from the PulseGames repository with `./gradlew -p network-helper :common:test :paper:test :velocity:test :paper:jar :velocity:jar :neoforge:jar`.
Java 21; NeoForge's first build downloads/remaps Minecraft. This is a separate Gradle build so normal PulseGames builds stay small.

## Player experience

- `/g` or `/globalchat` toggles sending ordinary **public** chat globally. `/localchat` switches it off. `/g` is a toggle, not a text-command moderation bypass. The initial default is local; `global-by-default` can change it after Aurora chooses.
- Local formatting is retained. Remote messages appear as `[Global · survival] Player: message` system messages, with no signed-chat rewriting and no click/format parsing of player text.
- A waiting queue announces `Minigame queue: Duels / Doubles (2/4) [Join]  Bedrock: /queues`.
- Java: click **Join**, or run `/queues` to choose a queue.
- Bedrock: `/queues` opens a native Floodgate form. `/queues chat` explicitly shows plain commands if the form is unavailable. `/joinqueue <id>` works as a fallback.
- Queue links expire. Starting/closing a match withdraws its listing; the destination rechecks the exact instance, capacity, party leadership and preview access. A stale click never creates an unrelated match.
- Existing backend parties are admitted together by the existing PulseGames path. This is **not** a new cross-server party synchronisation service; a player on another server transfers individually.
- Optional maths and word-scramble rounds run locally, with a 30-second answer window and no economy rewards. Paper defers to enabled ChatGames/PulseAddon installations. Create can list any external chat-game mod IDs to suppress the fallback. Default off.

## Compatibility contract

Paper observes the completed `AsyncChatEvent` without cancelling it, replacing its renderer or replaying it elsewhere. Cancelled events, restricted recipient sets, vanished players, denied chat permission and local-mode messages are not relayed. Local chat-game listeners still receive the original event. Remote messages never enter another backend's chat-answer event pipeline.

This is a separate global channel: existing per-server formatting, local channels and local answers remain intact. It does **not** magically synchronise another plugin's `/ignore`, channel membership, mute database or private-message state across servers. Recipient-side global ignores and custom channel/moderation adapters need explicit integration where required. A formatter which cancels normal chat and rebroadcasts privately will cause the helper to skip it, not bypass it. A recipient set alone cannot prove a custom channel's privacy policy (for example, when everyone online belongs to a staff channel). **Do not enable global relay for such a channel until an adapter or an explicitly public-only backend event has been verified.** Never solve compatibility by ignoring cancelled events.

The NeoForge adapter similarly observes the final `ServerChatEvent` after cancellation/moderation and does not replace it. It supports the confirmed Create backend (no chat mods found during the read-only inventory); custom private-channel mods require their own compatibility check. System broadcasts are never treated as player answers.

Verified inventory, 11 October 2026:

| Backend | Existing chat integration | Acceptance still required |
|---|---|---|
| Survival | AdvancedChat 1.11.1; ChatGames Premium 1.9.9 | Public channel, private/staff channels, mute, recipient ignore, winning/losing answers |
| Creative | EssentialsXChat | Formatting, mute, ignore, normal public chat |
| SurvivalTest | PulseAddon chat games | Read-only inventory only; no testing or installation authorised here |
| Lobby / SkyBlock / Sessions / Minigames(Test) | Paper helper | Normal chat, local fallback if wanted, permissions |
| Create (`aoc`) | NeoForge 21.1.248; no chat mods in inventory | Modded-client chat, proxy queue transfer and reconnection |

Compatibility with these installed plugin combinations is **not yet player-accepted**. Keep each backend's relay disabled until its checks pass. SurvivalTest remains excluded from this task's test environment.

## Transport and limits

Paper uses a bounded HMAC-SHA256 authenticated plugin-message channel. Velocity consumes the channel before checking its source, rejects client-origin packets, checks the current backend connection/carrier UUID and applies configured backend/queue-origin allowlists. Packet timestamps and nonces prevent stale/replayed commands. Queue IDs are generated by the proxy, not supplied as arbitrary commands or backend addresses. Payloads are limited to 8 KiB, 12 fields and 1,024 characters per field; chat is capped at 512 characters and one message per second.

Create sends authenticated chat to a proxy HTTP listener bound **only to 127.0.0.1**. This avoids optional-mod payload negotiation and requires no client changes. The proxy also checks the signed backend name against its modded allowlist and the sender's current connection. The listener is off unless `local-ingress-port` is configured, uses bounded workers/body size and accepts chat only. All relevant ready-gamed Minecraft containers currently share host networking; do not expose this port publicly when changing that arrangement.

No payload, player message or shared secret is logged. Generate a fresh random secret of at least 32 characters privately during approved installation; never commit it. Use the same secret across these helpers and restrict configuration file access.

Snapshots refresh every five seconds and expire after twenty seconds. Withdrawals stop advertisements when games start/close. Announcements are limited to once per game/mode/server per minute. Bots do not create advertisements without a human. Preview/WIP games are not advertised publicly. Full rosters may still offer joining when humans can reclaim bot seats. Backend admission remains authoritative.

Join handoff retries for up to fifteen seconds after normal Velocity connection approval. Signed request IDs make retries idempotent on the destination. Server bans/whitelists/proxy connection restrictions still apply. An unavailable destination leaves the player on their current server; a destination which accepts the transfer but refuses the stale queue leaves them safely on that backend's lobby.

## Installation plan (requires separate rollout and restart approval)

1. Back up proxy/backend configs and jars. Choose an unused loopback ingress port if including Create. No live writes were made by this implementation task.
2. Install Velocity jar and Paper jar on the agreed canary backends; add the NeoForge jar to Create's `mods` only when that server's rollout is authorised.
3. Start once to generate disabled configuration, or prepare those exact configurations before the approved restart. Set `enabled=true` and the private shared secret. Keep individual Paper `chat.enabled`, `queues.enabled` and `chat-games.enabled` off until testing them.
4. Proxy: restrict `backends`, `queue-servers` (initially only `minigamestest`) and, for Create, `local-ingress-backends=aoc`, `local-ingress-port=25581` (verify unused first). NeoForge `proxy-port` must match. No public firewall opening is needed.
5. Paper: queue sharing needs the PulseGames `networkQueues`/`joinNetworkQueue` API from this change. Older versions disable queue publishing with a clear warning; chat remains independently configurable.
6. Run the matrix below with Java and Bedrock before widening. Roll back jars/configs with another explicitly approved restart if needed.

## Acceptance checklist

- [ ] Two Java users on separate canary backends: public global chat once, local-only chat local, normal formatting unchanged.
- [ ] Cancelled/muted messages and each private/staff/radius channel never leak globally. Verify recipient ignore policy explicitly.
- [ ] Existing chat games still recognise local answers; relayed answers cannot win remotely; fallback does not run alongside them.
- [ ] Queue creation announces once; refresh does not spam; start/leave/server stop expires the link.
- [ ] Java Join click transfers and joins the exact queue; bot seat reclaimed; party capacity and preview gates retained.
- [ ] Bedrock `/queues` form, selection, cancellation and `/queues chat` fallback work through Geyser/Floodgate.
- [ ] Full, stale, stopped, denied and unreachable destinations fail clearly; repeated clicks do not duplicate admission.
- [ ] Create adapter relays moderated normal chat through loopback; modded client can transfer to a minigame and return.
- [ ] Restart/disconnect during handoff does not resurrect an old queue request.

Unit tests cover packet authentication/replay/expiry/bounds; queue identity/withdrawal/expiry; proxy client-spoof/carrier/origin checks; actual loopback transport; read-only Paper public/restricted/cancelled chat handling; fallback provider suppression and one-winner rules. PulseGames tests cover exact-instance joins, whole parties, stale/full queues and preview permissions. These do not replace the live plugin/client matrix.

API references: [Paper chat events](https://docs.papermc.io/paper/dev/chat-events/), [Velocity plugin messaging](https://docs.papermc.io/velocity/dev/plugin-messaging/), [Floodgate forms](https://geysermc.org/wiki/geyser/forms/), [NeoForge 1.21.1 documentation](https://docs.neoforged.net/docs/1.21.1/).

Local validation on 11 October 2026: 17 helper tests and 46 targeted PulseGames tests passed. Paper 26.2 loaded the enabled helper alongside the bot runtime probe. A separate NeoForge 21.1.248 development server loaded the enabled adapter and reached ready state. Both local servers were stopped afterwards. No Java/Bedrock end-to-end network acceptance is claimed.


## Staging checkpoint — 11 October 2026

PR5 merged as `f51155181e2e595c9fcf21c78e39ada75a880756`. Paper, Velocity and
NeoForge 0.1.0 builds plus the merged PulseGames 0.3.4 jar are staged privately at
`readyapp-vps:/srv/ready-gamed/staging/pulsenetwork-20261011-f511551`.
Each of the 12 Minecraft services has an inactive payload and plan; ten are primary-network
targets and two are disabled spares. `manifest.json` and `INSTALL-NOTES.txt` describe the
holds and future installation. The bridge secret stays in private remote files.

No active jars were changed and no server was restarted. Existing chat relays on Survival,
Creative, SurvivalTest, SkyBlock and legacy Minigames remain held pending channel/privacy
compatibility checks. MinigamesTest is the prepared queue publisher; legacy Minigames needs
migration checks. Do not use SurvivalTest for minigames acceptance. The direct Create proxy
must not become a second coordinator without a topology decision.

Before activation: obtain Aurora's fresh restart approval, back up the target files, preserve
active ownership and arena/config data, recheck the loopback port, then perform Java/Bedrock
and existing-chat-plugin acceptance. A later bot candidate does not replace this staged bundle.

## Queue return trips (0.1.1)

The proxy remembers a player's origin when a network queue transfers them. The
MinigamesTest adapter waits until PulseGames has neither pending admission nor an
active match before requesting return. Leaving a queue, finishing cleanup or a
rejected join therefore returns the visitor. Same-server joins create no return trip.
Authenticated request IDs and current-backend checks prevent stale or forged returns.
Manual server changes/disconnection clear the trip; an unavailable origin leaves the
player safely in the minigames lobby with a message.

Paper helpers save each player's departure location locally in return-locations.yml.
After a successful return the proxy requests restoration, delayed one second after
connection so ordinary join handling can finish. The origin world must still be
loaded. Install the helper on source Paper backends even with chat/queue publishing
disabled. NeoForge uses its normal persisted player position. This is a session trip,
not an offline mailbox: proxy restart/disconnect does not preserve the pending trip.

`chat-backends` on the proxy separately restricts global-chat senders and receivers;
`backends` still controls queue announcements and joining. This permits queues on
Survival while its private-channel/mute/ignore chat integration remains held.

## Global public chat by default (0.1.2)

New configs default `chat.global-by-default: true`; `/localchat` is optional.
Current rollout scope is **Lobby, SurvivalTest, MinigamesTest and the primary proxy**.
Legacy Creative/Survival/SkyBlock/Minigames, Sessions and Create are excluded from
active proxy chat/queue allowlists following Aurora's latest scope decision.

Set `paper-backends` to the participating Paper backend names. Remote messages are
sent to the destination helper, which applies native AdvancedChat ignore-all/name
lists and Essentials UUID/name ignore lists before display. Reflection/API failures
fail closed. Essentials mutes/vanish also block outgoing relay. AdvancedChat uses
its explicit post-moderation public event; private/channel events, radius chat and
cancelled messages do not relay. Ordinary Paper events retain cancellation/viewer
checks and exclude PulseAddon city renderers even when all online players share a city.
These adapters preserve installed-plugin behaviour; no mute/ignore database is copied
between servers. Full human crossplay/moderation acceptance remains required.

Plugin authorship from this version: AurorasChaos, on behalf of Player Ready.
