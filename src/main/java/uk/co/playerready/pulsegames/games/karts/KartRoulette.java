package uk.co.playerready.pulsegames.games.karts;

/** Hotbar item roulette timing: 30 ticks, icons change at most five times a second and slow towards the end. */
final class KartRoulette {
    static final int[] STEPS = {4, 4, 4, 5, 6, 7};
    static final int TOTAL_TICKS = 30;
    private KartRoulette() {}

    static boolean done(int elapsed) { return elapsed >= TOTAL_TICKS; }

    /** True on the ticks where the shown icon advances (and the tick sound plays). */
    static boolean changesAt(int elapsed) {
        int boundary = 0;
        for (int step : STEPS) { boundary += step; if (elapsed == boundary && boundary < TOTAL_TICKS) return true; }
        return false;
    }
    /** Which pool entry to show; the final result replaces it at {@link #TOTAL_TICKS}. */
    static int iconIndex(int elapsed, int poolSize) {
        int boundary = 0, changes = 0;
        for (int step : STEPS) { boundary += step; if (elapsed >= boundary && boundary < TOTAL_TICKS) changes++; }
        return poolSize <= 0 ? 0 : changes % poolSize;
    }
}
