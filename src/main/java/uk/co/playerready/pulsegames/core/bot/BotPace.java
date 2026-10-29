package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;

/**
 * Re-issue policy for step-by-step ("precise") movement in Bedwars and Skywars.
 * Before: after each one-block step the next command waited for pathTicks + jitter (20-35 ticks on Normal),
 * so the bot walked one block then stood still. Now it may re-issue as soon as it has arrived, with a floor
 * of one think (5 ticks), and callers hand it a goal a few cells ahead so it keeps moving between thinks.
 */
public final class BotPace {
    /** Horizontal distance squared at which a bot counts as having reached its step. */
    public static final double ARRIVED_SQ = 0.09;
    public static final int PRECISE_MIN_TICKS = 5;
    private BotPace() {}

    public static boolean arrived(Location here, Location destination) {
        if (here == null || destination == null || here.getWorld() != destination.getWorld()) return false;
        double dx = here.getX() - destination.getX(), dz = here.getZ() - destination.getZ();
        return dx * dx + dz * dz < ARRIVED_SQ;
    }

    public static boolean mayIssue(boolean precise, long tick, long nextPath, boolean arrived) {
        return tick >= nextPath || (precise && arrived);
    }

    public static int interval(boolean precise, int pathTicks, int jitter) {
        return precise ? PRECISE_MIN_TICKS : pathTicks + jitter;
    }
}
