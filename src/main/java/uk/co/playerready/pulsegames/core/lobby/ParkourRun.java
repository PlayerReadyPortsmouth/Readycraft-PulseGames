package uk.co.playerready.pulsegames.core.lobby;

import java.util.List;
import java.util.Locale;

/** One player's lobby parkour attempt: the timer, the checkpoints passed in order, and fall handling. */
public final class ParkourRun {
    private final long startMs;
    private final int checkpointCount;
    private int reached;

    public ParkourRun(long startMs, int checkpointCount) {
        this.startMs = startMs;
        this.checkpointCount = checkpointCount;
    }

    public long elapsed(long nowMs) { return Math.max(0, nowMs - startMs); }

    /** Number of checkpoints passed so far. */
    public int reached() { return reached; }
    public int total() { return checkpointCount; }

    /** Checkpoints count only in order, so skipping ahead or stepping on one twice does nothing. */
    public boolean reachCheckpoint(int index) {
        if (index != reached || index >= checkpointCount) return false;
        reached++;
        return true;
    }

    public boolean canFinish() { return reached == checkpointCount; }

    /** Index of the checkpoint to respawn at, or -1 for the start plate. */
    public int respawnIndex() { return reached - 1; }

    /** Falling below this height sends the player back. Null/NaN config means just under the lowest plate. */
    public static double fallLimit(Double configured, List<Double> plateYs) {
        if (configured != null && !configured.isNaN()) return configured;
        return plateYs.stream().mapToDouble(Double::doubleValue).min().orElse(0) - 2;
    }

    public static boolean fell(double y, double limit) { return y < limit; }

    /** "1:05.32" */
    public static String format(long ms) {
        long cs = ms / 10;
        return String.format(Locale.ROOT, "%d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100);
    }
}
