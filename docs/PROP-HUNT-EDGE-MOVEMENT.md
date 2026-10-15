# Prop Hunt edge movement correction — 0.3.21

Prepared locally, not deployed. Attribution: AurorasChaos (on behalf of Player Ready).

Unlocked full-block hiders have a scaled player body approximately 0.3 blocks wide and 0.9 high. The movement callback previously applied a second 1×1×1 disguise clearance check after Minecraft accepted movement. At walls, corners and steps this cancelled valid body movement, including rotation and moving away from an overlap.

Unlocked movement now uses native player terrain collision. The full-sized visual remains unchanged and can overlap neighbouring geometry while moving. Full prop clearance/support is still required to lock; disguise selection and decoy placement retain their existing validation. Native world-border and shared game freeze/void rules remain. Water/lava now follow ordinary player movement rather than being refused by the decorative cube check. This change does not introduce new arena-boundary validation or change player scale/step height.

Regression: the old implementation fails a wall-edge movement case; the patched implementation permits wall-parallel movement, a step, backing away and camera rotation. Existing lock tests verify anchored camera rotation and rejection of invalid snap space. Another plugin's cancellation is preserved.

Client acceptance on Java and Bedrock: run along walls and corners, move off slabs/stairs, enter a one-block-high gap, back away when the visual overlaps a block, turn camera, lock beside a wall then unlock. Full-size block should remain visible. Check head disguises, sprint, water, world border and leaving/falling from the arena. No server restart or deployment performed with this patch.
