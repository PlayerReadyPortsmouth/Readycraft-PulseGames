package uk.co.playerready.pulsegames.games.karts;

import java.util.Objects;
import java.util.Set;

/**
 * Position-aware item table: leaders get weak items, backmarkers strong ones. Weights are out of 100 per tier;
 * trickster doubles turbo and lightning. Unavailable items drop out and the remaining weights renormalise.
 */
final class KartItemBalance {
    enum Tier { LEADER, MIDDLE, BACK }
    private static final PulseKartsGame.Item[] ORDER = {
            PulseKartsGame.Item.SHELL, PulseKartsGame.Item.TURBO, PulseKartsGame.Item.BANANA, PulseKartsGame.Item.SHIELD,
            PulseKartsGame.Item.TRIPLE_SHELL, PulseKartsGame.Item.STAR, PulseKartsGame.Item.LIGHTNING, PulseKartsGame.Item.BULLET};
    /** Rows follow ORDER; columns are LEADER, MIDDLE, BACK. */
    private static final int[][] WEIGHTS = {
            {25, 20, 5}, {20, 20, 15}, {30, 15, 0}, {20, 10, 5}, {5, 15, 15}, {0, 10, 20}, {0, 5, 15}, {0, 5, 25}};
    private KartItemBalance() {}

    /** Leader = place fraction up to 0.34, back = 0.67 or more; a lone racer gets the middle table. */
    static Tier tier(int place, int field) {
        if (field <= 1) return Tier.MIDDLE;
        double fraction = (Math.clamp(place, 1, field) - 1) / (double) (field - 1);
        return fraction <= .34 ? Tier.LEADER : fraction >= .67 ? Tier.BACK : Tier.MIDDLE;
    }

    static int[] weights(KartStyle style, Tier tier, Set<PulseKartsGame.Item> unavailable) {
        Objects.requireNonNull(style, "style"); Objects.requireNonNull(tier, "tier");
        int[] out = new int[ORDER.length];
        for (int i = 0; i < ORDER.length; i++) {
            if (unavailable.contains(ORDER[i])) continue;
            int weight = WEIGHTS[i][tier.ordinal()];
            if (style == KartStyle.TRICKSTER && (ORDER[i] == PulseKartsGame.Item.TURBO
                    || ORDER[i] == PulseKartsGame.Item.LIGHTNING)) weight *= 2;
            out[i] = weight;
        }
        return out;
    }
    static int total(KartStyle style, Tier tier, Set<PulseKartsGame.Item> unavailable) {
        int sum = 0;
        for (int weight : weights(style, tier, unavailable)) sum += weight;
        return sum;
    }
    /** {@code roll} is any int; it is reduced modulo the table total. An empty table falls back to turbo. */
    static PulseKartsGame.Item choose(KartStyle style, int roll, Tier tier, Set<PulseKartsGame.Item> unavailable) {
        int[] weights = weights(style, tier, unavailable);
        int total = 0;
        for (int weight : weights) total += weight;
        if (total == 0) return PulseKartsGame.Item.TURBO;
        int value = Math.floorMod(roll, total);
        for (int i = 0; i < weights.length; i++) { value -= weights[i]; if (value < 0) return ORDER[i]; }
        return PulseKartsGame.Item.TURBO;
    }
    /** Items a roulette may flash while spinning. */
    static PulseKartsGame.Item[] roulettePool() { return ORDER.clone(); }
}
