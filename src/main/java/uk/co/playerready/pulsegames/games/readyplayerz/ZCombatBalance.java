package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.random.RandomGenerator;

/** Bounded, deterministic combat decisions; callers supply the round's random source. */
final class ZCombatBalance {
    enum Variant { WALKER, RUNNER, BRUTE, VENOM, BOSS }
    record Stats(double health, double speed, double damage, double scale) {}
    static Variant variant(int round, boolean boss, RandomGenerator random) {
        if (boss)
            return Variant.BOSS;
        int roll = random.nextInt(100);
        if (round >= 3 && roll < 12)
            return Variant.VENOM;
        if (round >= 2 && roll >= 12 && roll < 30)
            return Variant.BRUTE;
        if (roll >= 30 && roll < 55)
            return Variant.RUNNER;
        return Variant.WALKER;
    }
    /**
     * Arena-overridable health curve: health = min(max, base + perRound * (round - 1)); boss = health * bossMultiplier.
     * Defaults give a wooden sword (4 dmg) 3 hits on a round 1 walker and keep late rounds below ~30 hearts-worth.
     */
    record Tuning(double base, double perRound, double max, double bossMultiplier) {
        static final Tuning DEFAULT = new Tuning(10, 1.25, 60, 3.5);
        static Tuning of(double base, double perRound, double max, double bossMultiplier) {
            return new Tuning(clamp(base, 1, 100, DEFAULT.base), clamp(perRound, 0, 10, DEFAULT.perRound),
                clamp(max, 1, 300, DEFAULT.max), clamp(bossMultiplier, 1, 10, DEFAULT.bossMultiplier));
        }
        private static double clamp(double v, double lo, double hi, double fallback) {
            return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : fallback;
        }
    }
    static Stats stats(int round, Variant variant) {
        return stats(round, variant, Tuning.DEFAULT);
    }
    static Stats stats(int round, Variant variant, Tuning tuning) {
        int stage = Math.max(1, Math.min(50, round));
        double health = Math.min(tuning.max(), tuning.base() + tuning.perRound() * (stage - 1)), damage = Math.min(9, 2 + stage * .2),
               speed = Math.min(.32, .23 + stage * .002);
        return switch (variant) {
            case BOSS -> new Stats(Math.min(300, health * tuning.bossMultiplier()), speed * .85, Math.min(12, damage * 1.5), 1.5);
            case BRUTE -> new Stats(health * 1.5, speed * .85, damage * 1.3, 1.1);
            case RUNNER -> new Stats(health * .8, Math.min(.36, speed * 1.25), damage * .8, 1);
            case VENOM -> new Stats(health, speed, damage, 1);
            case WALKER -> new Stats(health, speed, damage, 1);
        };
    }
    static String drop(
        int round, int lastNukeRound, long tick, long nextDropTick, double chance, RandomGenerator random) {
        if (!Double.isFinite(chance) || tick < nextDropTick
            || random.nextDouble() >= Math.max(0, Math.min(.10, chance)))
            return null;
        int weight = random.nextInt(100);
        if (weight < 2 && round >= 5 && round - lastNukeRound >= 3)
            return "nuke";
        if (weight < 35)
            return "insta-kill";
        if (weight < 68)
            return "double-points";
        return "max-ammo";
    }
}
