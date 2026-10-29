package uk.co.playerready.pulsegames.games.bedwars;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * Whether a bot is fooled by an enemy Decoy Dummy. Each bot decides once per decoy, the first time it sees it: Easy bots
 * usually fall for it, Hard ones rarely. A bot that falls for it chases it for a few seconds, then ignores it for good.
 */
final class BedwarsBotDecoys {
    /** Percent chance to fall for it, and ticks of chasing, for tiers 0, 1, 2. */
    private static final int[] FALL = {70, 40, 10};
    private static final int[] CHASE_TICKS = {120, 80, 60};

    /** Ends of the chase per (bot, decoy); a bot that declined has an end already in the past. */
    private final Map<String, Long> until = new HashMap<>();

    private static int clamp(int tier) { return Math.max(0, Math.min(2, tier)); }

    /** True while this bot should treat the decoy as a target. */
    boolean attracts(UUID bot, UUID decoy, long tick, int tier, IntUnaryOperator rng) {
        Long end = until.get(bot + "/" + decoy);
        if (end == null) {
            int t = clamp(tier);
            end = rng.applyAsInt(100) < FALL[t] ? tick + CHASE_TICKS[t] : Long.MIN_VALUE;
            until.put(bot + "/" + decoy, end);
        }
        return tick < end;
    }

    void forget() { until.clear(); }
}
