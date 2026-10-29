package uk.co.playerready.pulsegames.games.quake;

import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

/** Pure Quake bot reasoning: how wide a shot misses and which way (and whether) to strafe. No Bukkit. */
final class QuakeTactics {
    private QuakeTactics() {}

    /**
     * Maximum yaw error in degrees: the shared tier aim error plus a share of the miss chance. A railgun is a hitscan, so
     * this is what separates a tier: about 14 degrees on Easy, 6 on Normal, just over 1 on Hard.
     */
    static double yawError(BotDifficulty.Profile profile) {
        return profile.aimErrorDegrees() + (100 - profile.hitChance()) * 0.15;
    }

    /** A signed random error in {@code [-limit, limit]} from a value in {@code [-1, 1]}. */
    static double error(double limit, double unit) { return Math.max(-1, Math.min(1, unit)) * limit; }

    /** Per-bot hash, stable across calls. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Lateral move in blocks (positive left, negative right) for this bot at this tick. The side flips every 15 to 34
     * ticks (a different rhythm per bot, so two bots never mirror each other) and a bot with a low strafe bias will
     * sometimes shuffle a short step instead of circling. Never zero: an idle bot would charge its target instead.
     */
    static double strafe(long tick, long botHash, BotDifficulty.Profile profile) {
        int period = 15 + (int) Math.floorMod(mix(botHash), 20L);
        long window = tick / period;
        long roll = mix(botHash ^ (window * 0x9E3779B97F4A7C15L));
        double side = (roll & 1) == 0 ? 1 : -1;
        double circles = ((roll >>> 8) % 100) / 100.0;
        double magnitude = circles < profile.strafeBias() ? 3.0 : 1.5;
        return side * magnitude;
    }
}
