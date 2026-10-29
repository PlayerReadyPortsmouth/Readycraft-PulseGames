package uk.co.playerready.pulsegames.games.prophunt;

import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/** Pure Prop Hunt bot reasoning: a hunter's memory of checked spots, believable hiding places, and nerves. No Bukkit. */
final class PropHuntTactics {
    private PropHuntTactics() {}

    static final int CELL = 4;
    /** A spot looked at within this many ticks counts as checked (90 seconds). */
    static final long CHECKED_TICKS = 1800;
    /** A sound or taunt is worth chasing for this long (10 seconds). */
    static final long HEARD_TICKS = 200;

    /** What a hunter has already looked at, and the most recent noise it has not yet investigated. */
    static final class SearchLedger {
        private final Map<Long, Long> checked = new HashMap<>();
        private double heardX, heardZ;
        private long heardUntil = -1;

        private static long key(double x, double z) {
            return ((long) Math.floorDiv((int) Math.floor(x), CELL) << 32) ^ (Math.floorDiv((int) Math.floor(z), CELL) & 0xffffffffL);
        }

        void check(double x, double z, long tick) { checked.put(key(x, z), tick); }

        boolean fresh(double x, double z, long tick) {
            Long at = checked.get(key(x, z));
            return at != null && tick - at <= CHECKED_TICKS;
        }

        /** A taunt or heartbeat: remember where it came from, rounded to the search grid. */
        void hear(double x, double z, long tick) { heardX = x; heardZ = z; heardUntil = tick + HEARD_TICKS; }

        /** The heard spot while it is still new and unchecked, else null. */
        double[] heard(long tick) {
            if (tick > heardUntil || fresh(heardX, heardZ, tick)) return null;
            return new double[]{heardX, heardZ};
        }

        /**
         * The next place to look: the noise first, otherwise the candidate with the best mix of unchecked, near and a
         * little chance. Recently checked spots cost 30, so a tidy sweep beats pacing the same corner. Null if none.
         */
        double[] next(double fromX, double fromZ, double[][] candidates, long tick, IntUnaryOperator random) {
            double[] noise = heard(tick);
            if (noise != null) return noise;
            double[] best = null;
            double bestScore = Double.MAX_VALUE;
            for (double[] c : candidates) {
                double score = Math.hypot(c[0] - fromX, c[1] - fromZ) * 0.5 + random.applyAsInt(30) / 10.0
                        + (fresh(c[0], c[1], tick) ? 30 : 0);
                if (score < bestScore) { bestScore = score; best = c; }
            }
            return best;
        }
    }

    /**
     * Where a prop hides: far from where the hunters start (their spawn), spread from the other props, with a
     * little noise so two props do not pick the same corner. Returns the index, or -1.
     */
    static int pickHideSpot(double[][] candidates, double[] hunters, double[][] taken, IntUnaryOperator random) {
        int best = -1;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < candidates.length; i++) {
            double away = hunters == null ? 0 : Math.min(40, Math.hypot(candidates[i][0] - hunters[0], candidates[i][1] - hunters[1]));
            double spread = 12;
            for (double[] other : taken) spread = Math.min(spread, Math.hypot(candidates[i][0] - other[0], candidates[i][1] - other[1]));
            double score = away + spread * 1.5 + random.applyAsInt(40) / 10.0;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    enum Reaction { STAY, SNEAK_AWAY, PANIC }

    static double panicChance(BotDifficulty tier) {
        return switch (tier) { case EASY -> 0.5; case NORMAL -> 0.25; case HARD -> 0.05; };
    }

    static double sneakChance(BotDifficulty tier) {
        return switch (tier) { case EASY -> 0.1; case NORMAL -> 0.3; case HARD -> 0.5; };
    }

    /**
     * A locked prop's choice when a hunter closes in (rolled about once a second). Seen and very close: usually
     * freeze, but sometimes bolt (panic, worst on Easy). Not yet seen but near: Hard props slip away quietly.
     */
    static Reaction react(double hunterDistance, boolean hunterCanSeeUs, BotDifficulty tier, IntUnaryOperator random) {
        if (hunterCanSeeUs && hunterDistance <= 3.5)
            return random.applyAsInt(100) < panicChance(tier) * 100 ? Reaction.PANIC : Reaction.STAY;
        if (!hunterCanSeeUs && hunterDistance <= 6)
            return random.applyAsInt(100) < sneakChance(tier) * 100 ? Reaction.SNEAK_AWAY : Reaction.STAY;
        return Reaction.STAY;
    }

    /** A spot 8 to 14 blocks from {@code from}, directly away from the hunter (jittered by the roll). */
    static double[] escapePoint(double fromX, double fromZ, double hunterX, double hunterZ, IntUnaryOperator random) {
        double dx = fromX - hunterX, dz = fromZ - hunterZ, d = Math.hypot(dx, dz);
        double angle = d < 0.01 ? random.applyAsInt(360) * Math.PI / 180 : Math.atan2(dz, dx);
        angle += (random.applyAsInt(61) - 30) * Math.PI / 180;
        double reach = 8 + random.applyAsInt(7);
        return new double[]{fromX + Math.cos(angle) * reach, fromZ + Math.sin(angle) * reach};
    }
}
