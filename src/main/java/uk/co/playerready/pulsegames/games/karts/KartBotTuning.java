package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

/** Operator tunables for kart bots ({@code karts.bots.*}); every value is clamped to a safe range. */
public final class KartBotTuning {
    public static final KartBotTuning DEFAULT = new KartBotTuning(null);
    /** Top speed as a fraction of {@code MAX_SPEED * style.speed()}, indexed by {@link BotDifficulty#ordinal()}. */
    final double[] speedFraction = {.78, .90, .98};
    /** Target speeds for a bend over 25, 55 and 100 degrees, before the per-tier scale. */
    final double[] cornerSpeeds = {8, 5.5, 3};
    final int[] driftMaxTicks = {22, 40, 50};
    final boolean rubberBand;
    final double maxBonus, maxPenalty, rubberStart, rubberFull, rubberRange;
    final double laneWidth, overtakeRange, easyDrifterShare;
    final boolean strongItemsWhenBehind;

    /** Null (or a section whose values read as zero, e.g. a mock) gives the defaults. */
    public KartBotTuning(ConfigurationSection s) {
        boolean rubber = true, strong = true;
        double bonus = .08, penalty = .08, start = 25, full = 100, range = 150, lane = 1.5, overtake = 6, share = .25;
        if (s != null) {
            String[] tiers = {"easy", "normal", "hard"};
            for (int i = 0; i < 3; i++) {
                speedFraction[i] = pick(s.getDouble("speed-fraction." + tiers[i]), speedFraction[i], .3, 1);
                int ticks = s.getInt("drift-max-ticks." + tiers[i]);
                if (ticks > 0) driftMaxTicks[i] = Math.min(ticks, 100);
            }
            List<Double> corners = s.getDoubleList("corner-speeds");
            if (corners.size() == 3) for (int i = 0; i < 3; i++) cornerSpeeds[i] = pick(corners.get(i), cornerSpeeds[i], 1, 12);
            rubber = s.getBoolean("rubber-band.enabled", true);
            bonus = pick(s.getDouble("rubber-band.max-bonus"), bonus, 0, .15);
            penalty = pick(s.getDouble("rubber-band.max-penalty"), penalty, 0, .15);
            start = pick(s.getDouble("rubber-band.start-blocks"), start, 1, 500);
            full = Math.max(start + 1, pick(s.getDouble("rubber-band.full-blocks"), full, 2, 500));
            range = Math.max(full, pick(s.getDouble("rubber-band.range-blocks"), range, 2, 1000));
            lane = pick(s.getDouble("lane-width"), lane, .1, 1.5);
            overtake = pick(s.getDouble("overtake-range"), overtake, 2, 12);
            share = s.contains("easy-drifter-share") ? Math.clamp(s.getDouble("easy-drifter-share"), 0, 1) : share;
            strong = s.getBoolean("strong-items-when-behind", true);
        }
        rubberBand = rubber; maxBonus = bonus; maxPenalty = penalty; rubberStart = start; rubberFull = full;
        rubberRange = range; laneWidth = lane; overtakeRange = overtake; easyDrifterShare = share;
        strongItemsWhenBehind = strong;
    }
    private static double pick(double value, double fallback, double min, double max) {
        return value > 0 && Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    /**
     * Pace multiplier from the lead over the nearest human, in blocks (positive = bot ahead). Behind the human it
     * speeds up, comfortably ahead it eases off, smoothly and boundedly. Easy leans towards help, Hard towards
     * pressure. A human further ahead than {@code range} is not "nearby": a runaway leader is never throttled.
     */
    public double pace(double gap, boolean humanPresent, BotDifficulty tier) {
        if (!rubberBand || !humanPresent || !Double.isFinite(gap)) return 1;
        double[] help = {1, .8, .5}, ease = {.5, .8, 1};
        if (gap < 0) return 1 + maxBonus * help[tier.ordinal()] * ramp(-gap);
        if (gap > rubberRange) return 1;
        return 1 - maxPenalty * ease[tier.ordinal()] * ramp(gap);
    }
    private double ramp(double blocks) { return Math.clamp((blocks - rubberStart) / (rubberFull - rubberStart), 0, 1); }
}
