# Network nicknames (30 Oct 2026)

Draft PR from `feat/network-nicknames`. Not released, no versions bumped. Details: `network-helper/README.md`, section "Nicknames".

## What changed

- `common`: `NickRules` (shape, impersonation, blocked words), `NameStyle.Identity` gains `nickname` and `displayName()`, `NameStyle.renderChat` (hover for Java, `Nick (username)` for Bedrock), `IdentityResolver` reads `pulse.nickname`, `REMOTECHAT` 8-field form, `RoleMenuCommands` accepts `nick`.
- `velocity`: `NickCommand` (registered in one line in `VelocityBridge.registerRoles`), `NickLogic` (gating), `RoleStore.nickname/setNickname`, tab roster carries the nickname, `/identity` shows it. New proxy key `nick.blocked-words`.
- `paper`: admin hub Set nickname / Reset nickname, `AnvilPromptLogic.Rule.nickname()`, chat names use `renderChat`.

## Rollout order

1. Paper backends first (they must understand the 8-field message), then the proxy. Nothing changes for players until the first nickname is set.

## Acceptance: Java

1. As a plain player, `/nick Foo` is refused / unknown command. As staff, `/nick Foo_Bar` works and says so.
2. Chat on the local server and on another server shows `Foo_Bar`; hovering shows `Real name: <username>`. The tab shows `Foo_Bar` with no hover, in the same position as before.
3. Join and leave lines show the nickname, hover shows the real name.
4. `/identity` shows `Nickname (pulse.nickname)` and the real name.
5. `/nick reset` brings the real name back everywhere within a few seconds.
6. Refusals: `/nick ab`, `/nick a b`, `/nick Steve` (an online player), `/nick <staff name>` plus one letter, a blocked word. Each gets a clear message and nothing changes.
7. Gating: staff cannot change a dev's or an admin's nickname; admin cannot change a dev's or another admin's (only a dev may change a dev's); dev can change anyone's; console can too.
8. Offline target by exact name works; an unknown name says so.
9. Admin hub: `/admin`, Players, pick a player, Set nickname (anvil, bad text re-prompts), Reset nickname (asks yes/no).
10. Console shows `NICK <actor> set|reset <target> <nick>`; the LuckPerms action log has the entry.

## Acceptance: Bedrock

1. Chat from a nicked player shows `Foo_Bar (RealName)` in plain colours, no hover; the tab shows `Foo_Bar`.
2. Admin hub Set nickname opens a one-field form; Reset asks Yes/No.
3. A nicked Bedrock staff member sees their own nickname when they chat locally and across servers.

## Not covered

- Offline players' nicknames are not checked for clashes (online only), as briefed.
- Command-name standardisation is a separate pass; `/nick` is one class and one registration line.
