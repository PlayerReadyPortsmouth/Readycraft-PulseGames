# Handoff: identity debugging (30 Oct 2026)

Branch `feat/identity-debug` from `origin/main` 2a62d63 (PulseNetwork 0.2.1 bundle). Draft PR. Versions not bumped; nothing deployed.

## What changed
- `common/RemoteChat` (new): builds and parses the `REMOTECHAT` fields and renders the one chat line. Used by the Velocity relay and Paper, so the two cannot drift. `NameStyle.Tags.label(role)` gives `[DEV]` style text.
- Wire: `REMOTECHAT` takes 4 fields (old proxy, plain name) or 7 (adds role, styleId, level). HMAC, 12-field and 1024-character rules in `Wire` are unchanged. Paper accepts 4 or 7 and drops unknown roles, styles and non-numeric or out-of-range levels.
- Velocity: `IdentityCommand` and `IdentityReport` (pure text assembly and gating); `TabService` gains `hidden(Player)`, `refresh()` and a package-visible `bedrock(Player)`; `VelocityBridge` resolves the sender's identity once per message and registers `/identity` beside `/role`.
- Paper: `/namestyle preview <id>`; `NameStyleModel.preview` holds the gating (dev/admin by direct group, else must hold `pulse.namestyle.<id>`).
- README: remote chat bullet (replaces the old "Limit"), preview row, "Identity debugging" section.

## Rollout order
Paper backends first, then the proxy. A backend on 0.2.1 or older ignores a 7-field `REMOTECHAT`, so a new proxy in front of old backends silently stops global chat reaching them.

## Left out
- `/identity` works for online players only (level, server and Bedrock are only known online).
- Preview uses the default tag set, role from meta, no level badge, and is not rate-limited (it writes nothing).
- No bedrock-safe flag on the wire (the viewer decides).
- Not run on a real proxy, Paper server, Java or Bedrock client.

## Acceptance (Java and Bedrock)
1. Dev on Lobby sends a global message; a Java player on Survival sees `[Global · Lobby] [DEV] [12] name: message` with the style colours; a Bedrock player sees the same text in flat colours.
2. With the proxy deployed ahead of a backend that does not yet have this change, confirm global chat stops there (expected, hence the order), then upgrade and confirm it returns.
3. `/identity` as a dev: own and another online player, groups, role, tag, style, held count, level, hidden, Bedrock, server and both previews. `/identity <other>` as an ordinary player: refused; `/identity` on themselves: works.
4. `/identity refresh` as a dev: tab lists rebuild within a few seconds; as an ordinary player: refused.
5. `/namestyle preview rainbow` as a dev without the unlock: shows a chat line, `/namestyle list` and meta unchanged. As an ordinary player without it: "locked". As one with it: shows.
