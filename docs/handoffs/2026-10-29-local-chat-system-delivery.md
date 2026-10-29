# Handoff: local chat as system messages (29 Oct 2026)

Branch `feat/local-chat-system-delivery` (draft PR against `main`). No version bump; a bundle does that.

## Problem
Same-backend players cannot see each other's chat behind Velocity (1.19+ signed chat; Floodgate/Bedrock cannot sign). Relayed cross-server chat is unaffected because the proxy sends system messages.

## Change (network-helper/paper)
- `chat.local-system-delivery` (default false) and `chat.local-format` in `config.yml`.
- `LocalChatDelivery` (pure): per-viewer render with the event renderer, fallback format, public-audience check.
- `PaperBridge.chat` (MONITOR, ignoreCancelled): records the relay privacy decision from the viewer set first, then delivers system messages to a snapshot of the viewers and clears them. Relay logic is otherwise unchanged and now uses the recorded decision. Console gets its line once as a viewer.
- Tests: `LocalChatDeliveryTest` (format fallback, viewer preservation, renderer failure, public check) and two `PaperBridgeTest` cases (relay still fires once with viewers cleared; off leaves the event alone).

## Rollout
Set `chat.local-system-delivery: true` in the PulseNetwork `config.yml` on MinigamesTest and SurvivalTest first, then Lobby, Survival, Creative, SkyBlock, Sessions, Minigames. Needs a restart or helper reload per server; not done here. Survival (AdvancedChat) and Creative (EssentialsXChat) need the matrix below before enabling, because their formatting comes from the renderer.

## Acceptance
- Two Java players on one server see each other's chat.
- Java and Bedrock on one server see each other's chat.
- A muted player stays muted.
- Global relay arrives once on the other server.
- Console shows one line per message.
- Private/staff channels still reach only their members.

## Limits
Per-message report/hover and secure indicator are lost. A later MONITOR listener that cancels no longer stops delivery. Not run in-game.
