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
    static Stats stats(int round, Variant variant) {
        int stage = Math.max(1, Math.min(50, round));
        double health = Math.min(70, 18 + stage * 2), damage = Math.min(9, 2 + stage * .2),
               speed = Math.min(.32, .23 + stage * .002);
        return switch (variant) {
            case BOSS -> new Stats(Math.min(300, health * 4), speed * .85, Math.min(12, damage * 1.5), 1.5);
            case BRUTE -> new Stats(health * 1.6, speed * .85, damage * 1.3, 1.1);
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
