# PulseKarts showcase candidate — 31 October 2026

PulseGames 0.11.0 extends the retained track-physics worktree. Local and default off; no commit, push, server installation, restart or production flag write.

Implemented three extra gameplay items, custom item lookup for all eleven items, and authored cosmetic props bound to successful pads/gates. The companion additive Copper Circuit catalogue contains twelve managed props and eleven modelled inventory items. Trackside marshals/drones are scenery; actual spectator avatars/cameras are not implemented.

## Validation

- 248 targeted kart/content bridge tests pass, zero failures/errors/skips; jar built. Gate `20261031-010437-90u`.
- Two asset tests pass: all item sources load and every managed model compiles to both provider inputs. Gate `20261031-010217-59t`.
- Actual-geometry boost, assembled checkpoint, marshal and repair previews visually checked. Generator is deterministic; provenance/dimensions are in the asset manifest.
- Review fixed shared provider capacity starvation and inactive-model fallback loss after failed cleanup.
- Tracker HTML/PDF regenerated; changed cover and new section visually inspected.

## Handoff

See [authoring guide](../PULSEKARTS-SHOWCASE.md). Asset sources: `/mnt/data/worktrees/ReadyCraft-PulseAddon-kart-surface-visuals/plugins/pulse-content/examples/kart-showcase/`. Validation and jar hashes: `/home/ara/Projects/Ready/ops/readycraft/kart-showcase-2026-10-31-validation.json`.

The source catalogue is additive, not an installed/final client pack. Merge with existing catalogue and obtain external model-provider outputs before client acceptance. Validate Java/Bedrock placement, inventory readability, held models, crowd limits, pack failure and item balance. Wall/ceiling props use baked orientations and yaw; they do not promise arbitrary model roll. Cosmetic props never create gameplay gates/collision.

`karts.showcase` remains false with broad rollout false and empty canary list. All human racers must qualify before rules change; revocation cancels without normal rewards. Optional prop settings are ignored for ineligible races. Physics opt-in remains separate.
