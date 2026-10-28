package uk.co.playerready.pulsegames.games.karts;

/**
 * Per-kart speed boosts. Each source keeps its own factor and expiry and the kart uses the largest
 * active factor, so turbo, drift, panels, tricks and anti-gravity never stack.
 */
final class KartBoost {
    enum Source { TURBO, DRIFT, PANEL, TRICK, STAR, BULLET, ANTIGRAV, BUMP }
    static final double TURBO_FACTOR = 1.55;
    private final double[] factor = new double[Source.values().length];
    private final long[] until = new long[Source.values().length];

    /** Re-applying a live source extends it by maximum (never addition) and keeps the stronger factor. */
    void apply(Source source, double boostFactor, long now, int ticks) {
        if (ticks <= 0 || !(boostFactor > 1)) return;
        int i = source.ordinal();
        long end = now + ticks;
        if (now >= until[i]) { factor[i] = boostFactor; until[i] = end; }
        else { factor[i] = Math.max(factor[i], boostFactor); until[i] = Math.max(until[i], end); }
    }
    double multiplier(long now) {
        double best = 1;
        for (int i = 0; i < factor.length; i++) if (now < until[i]) best = Math.max(best, factor[i]);
        return best;
    }
    boolean active(long now) { return multiplier(now) > 1; }
    boolean active(Source source, long now) { return now < until[source.ordinal()]; }
    /** Latest expiry over all sources (0 when never boosted). */
    long until() { long latest = 0; for (long end : until) latest = Math.max(latest, end); return latest; }
    void clear() { java.util.Arrays.fill(factor, 0); java.util.Arrays.fill(until, 0); }
    /** Clears one source, e.g. a star or bullet that has ended early. */
    void clear(Source source) { factor[source.ordinal()] = 0; until[source.ordinal()] = 0; }
}
