# Handoff: network role tooling (30 Oct 2026)

Branch `feat/network-roles`, draft PR against `main`. PulseNetwork Velocity module only (version not bumped).

## What changed
- `/role` (alias `/networkrole`) in `RoleCommand`: set, remove (with `/role confirm`, 30 s), who, list, venues, hide, unhide. Tab-completes subcommands, roles and online names.
- `RoleLogic`: pure rules (role names, `roles.groups` parsing, gating by group membership, confirmation expiry, tab-hidden check, completion). Unit tested.
- `RoleStore` / `LuckPermsStore`: LuckPerms API behind an interface. `net.luckperms:api:5.4` is `compileOnly`; the store is only constructed when the `luckperms` plugin is loaded.
- Tab: `pulsenetwork.tab.hidden` permission check replaced by meta `pulse.tab.hidden=true` or config `tab.hidden-players` (`OptionalPlugins.metaValue`, `TabService.hiddenNames`).
- `VelocityBridge`: registration and two config reads only.

## Not done, and why
- No "last dev" guard: only a dev can remove dev and nobody can change their own roles, so only the console could remove the last one.
- No click UI (design section 7, optional 6b).
- Gating uses direct group membership; a role held only by inheritance (a group that has `dev` as a parent) does not count.
- Not run on a real proxy. Sync delay to backends unmeasured.

## Deploy notes
Proxy jar only. Groups `dev`, `admin`, `staff`, `venue-*` must exist (seeded separately). Wildcard holders reappear in the tab on deploy; run `/role hide` for anyone who should stay hidden.

## In-game acceptance (Java and Bedrock)
1. As console and as Aurora (dev): `/role set` a test account to `venue:ports`, `/role who`, `/role list venue:ports`, `/role venues`.
2. `/role remove` then wait over 30 s then `/role confirm`: refused. Repeat inside 30 s: removed.
3. Refusals: admin runs `/role set X dev`; staff runs `/role hide X`; a venue or ordinary account runs `/role who X`; unknown player; unknown role; set own role.
4. `/role hide` a test account: vanishes from the tab on every server within a refresh; `/role unhide` brings it back.
5. An admin holding `*` and not hidden is visible in the tab.
6. Dev/admin online see the mirrored chat line; proxy log has the `ROLE` line; `lp log recent` shows the actor.
7. A change appears on a backend (for example `/lp user X info` there) within a few seconds; note the delay.
8. Bedrock: tab-complete, plain messages readable, no clickable text needed.
