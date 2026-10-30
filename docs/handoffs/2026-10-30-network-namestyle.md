# Network name styles (30 Oct 2026)

Branch `feat/network-namestyle` from `origin/main` e8a0acf. Draft; nothing deployed or enabled.

## What changed
- `network-helper/common`: `NameStyle` (tags, 16 styles, 32-char guard, Bedrock flattening), `IdentityResolver`, `LuckPermsMeta` (reflective `getMetaValue`), `ServerStatus` gains `levels` (`L` chunks, backward compatible for parsing). Common now compiles against adventure (provided by Paper and Velocity at runtime).
- Velocity: `TabRender.displayName` uses `NameStyle`; `TabService` resolves role/style via LuckPerms meta and level from STATUS, refreshes animated rows each `tab.animation-ms`; relay line in `VelocityBridge.relayLine`. Command registration and the hidden check are untouched.
- Paper: `StatusReporter` sends per-player level via reflective `PulseGames.networkPlayerLevel`; `PaperBridge` styles the sender in local system delivery, per viewer (Bedrock flat).
- PulseGames plugin: additive `PulseGamesPlugin.networkPlayerLevel(Player)` (0 when levels are off).
- README section "Names, tags and styles".

## Rollout order and cautions
- Upgrade the proxy before any backend sends level chunks: an older proxy rejects the whole STATUS report.
- Roles show only once the groups carry `pulse.role` (being seeded). `admin` holders with `*` may still be hidden from the tab by `pulsenetwork.tab.hidden`; that fix is the other builder's.
- Not verified on any client (Java or Bedrock) and no versions were bumped.

## Left out
- Styled names for `REMOTECHAT` on Paper backends (needs extra wire fields).
- Picker, unlock hooks, `/role` commands, Survival/AdvancedChat, PlaceholderAPI.
- No test for `networkPlayerLevel` itself (needs a live Bukkit server).

## Acceptance (Java and Bedrock)
- Tab, global relay and local chat show `[DEV]`, `[ADMIN]`, `[STAFF]`, `[VENUE]` per `pulse.role`.
- An admin with `*` and `pulse.role=admin` shows ADMIN; a venue account shows VENUE only, never a town.
- `pulse.namestyle=rainbow` animates in the Java tab, is static in chat, and is one flat colour on Bedrock.
- `[12]` grey badge appears when PulseGames reports a level; rows never exceed 32 visible characters.
