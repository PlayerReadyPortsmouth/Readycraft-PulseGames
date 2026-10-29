# Supermarket Sweep checkout hints (29 Oct 2026)

Problem: players collected the whole list but nothing ticked, because items only check out inside the arena `checkout` region and the game never said so.

Rules unchanged. Added (all behind `supermarket.hints.enabled`, default true):
- Sidebar line "Checkout: 23m ahead right" (distance to the nearest point of the region, direction relative to the player's yaw), or "At the checkout" when inside.
- Every second: a ring of HAPPY_VILLAGER particles on the region's floor edge (8 to 24 points) and 6 END_ROD points up the centre. No resource pack.
- Chat reminder at start and at 60 s left; one-off action bar "Got it! Take it to the checkout." on a player's first list-item pickup.
- After a list-item pickup, the checkout check re-runs one tick later (the item is only in the inventory after the event), so standing at the tills while a teammate's drop arrives now ticks it.
- Compass "Checkout" in slot 8 pointing at the checkout centre (lodestone, untracked). Cannot be dropped or moved by clicks/number keys; removed in onEnd.

Shared-file edits (small): `MiniGame.itemDrops(ItemStack)` default delegating to `itemDrops()`; `GameListener.onDrop` calls it. `config.yml` gains `supermarket.hints.enabled`.

Tests: `CheckoutPointerTest` (5): inside, nearest-point distance, direction by yaw, "At the checkout" line, perimeter walk.

In-game acceptance (Java and Bedrock): sidebar line updates and turns to "At the checkout"; particles visible from afar; chat lines at start and 60 s; action bar once; teammate drop at the tills ticks; compass points at the tills, cannot be dropped/moved; compass gone after the match. Check the compass renders on Bedrock (lodestone compasses should); sidebar direction is words, no arrows, for font safety.
