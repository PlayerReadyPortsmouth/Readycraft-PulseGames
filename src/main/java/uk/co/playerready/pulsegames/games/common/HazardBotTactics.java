package uk.co.playerready.pulsegames.games.common;

/** Pure scoring shared by the Avalanche and Volcano bots: falling-block danger and how hard to climb. No Bukkit. */
public final class HazardBotTactics {
    private HazardBotTactics() {}

    /** A body is hit by a block landing within this horizontal distance (hit radius plus body width and a margin). */
    public static final double HIT = 1.7;
    private static final double NEAR = 3.2;

    /**
     * How bad it is to stand at horizontal offset ({@code dx}, {@code dz}) from a falling block that is
     * {@code heightAbove} blocks above that spot. Lower blocks land sooner so they weigh more; a block already
     * below the spot, or too far to the side, is harmless. Returns a non-negative cost.
     */
    public static double fallingDanger(double dx, double dz, double heightAbove) {
        if (heightAbove < -0.5) return 0;
        double distance = Math.hypot(dx, dz);
        // About 0.04 blocks per tick squared before drag: time to land in ticks, softened.
        double ticks = Math.sqrt(2 * Math.max(0, heightAbove) / 0.04);
        double urgency = 1 / (1 + ticks / 10);
        if (distance < HIT) return 200 * urgency;
        if (distance < NEAR) return 40 * urgency * (NEAR - distance) / (NEAR - HIT);
        return 0;
    }

    /** Standing in the open beats a corner: small bonus per open neighbouring cell (0 to 4). */
    public static double openness(int openNeighbours) { return openNeighbours * 0.6; }

    /**
     * Points per block of height while climbing from rising lava. The nearer the lava, the harder the bot climbs;
     * with plenty of room it still prefers height but also avoids rocks and dead ends.
     */
    public static double climbWeight(double gapAboveLava) {
        if (gapAboveLava <= 2) return 30;
        if (gapAboveLava <= 5) return 18;
        return 10;
    }

    /** Cells at or just above the lava are poison; a cell the lava reaches on the next rise is nearly as bad. */
    public static double lavaCost(double cellY, double lavaY, boolean riseImminent) {
        if (cellY <= lavaY + 0.5) return 500;
        if (riseImminent && cellY <= lavaY + 1.5) return 120;
        return 0;
    }
}
