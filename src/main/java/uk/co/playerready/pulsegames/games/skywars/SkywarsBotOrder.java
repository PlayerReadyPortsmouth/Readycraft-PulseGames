package uk.co.playerready.pulsegames.games.skywars;

/** Pure order of a Skywars bot's errands: chests first, then a bridge only if it has blocks, else hunt on its own island. */
public final class SkywarsBotOrder {
    private SkywarsBotOrder() {}

    public enum Step { LOOT, BRIDGE, HUNT_HERE }

    public static Step next(boolean unlootedChestInRange, boolean hasBridgeBlocks, boolean rivalInRange) {
        if (unlootedChestInRange) return Step.LOOT;
        if (rivalInRange && hasBridgeBlocks) return Step.BRIDGE;
        return Step.HUNT_HERE;
    }
}
