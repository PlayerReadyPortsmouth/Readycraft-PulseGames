package uk.co.playerready.pulsegames.core.bot;

import java.util.Locale;

/**
 * Timing, accuracy and human-like habits only: all levels retain normal reach, damage and visibility rules.
 * {@link #profile()} holds the shipped defaults; {@code bots.combat.tiers.*} in config.yml can override them.
 */
public enum BotDifficulty {
    EASY(new Profile(28, 45, 40, 30, 6.0, 0.35, 0.40, 0.00, 5, 0.20)),
    NORMAL(new Profile(14, 80, 20, 20, 3.0, 0.70, 0.75, 0.25, 4, 0.30)),
    HARD(new Profile(10, 95, 12, 10, 0.5, 0.90, 1.00, 0.60, 2, 0.30));

    /**
     * @param meleeDelay      ticks between melee swings
     * @param hitChance       percent chance a swing connects
     * @param projectileDelay ticks between projectile shots
     * @param pathInterval    ticks between re-paths
     * @param aimErrorDegrees maximum yaw error of where the bot looks (pitch uses half)
     * @param strafeBias      0..1 chance a strafe cycle circles rather than holds position
     * @param sprintChance    0..1 chance a bot sprints in a sprint cycle
     * @param jumpCritChance  0..1 chance of a jump-crit / jump-reset when the moment arises
     * @param reactionJitter  extra random reaction ticks (0..5) on each engagement
     * @param retreatHealth   0..1 health fraction at which a bot with a healing item backs off
     */
    public record Profile(int meleeDelay, int hitChance, int projectileDelay, int pathInterval, double aimErrorDegrees,
                          double strafeBias, double sprintChance, double jumpCritChance, int reactionJitter,
                          double retreatHealth) {}

    final int meleeTicks, accuracy, throwTicks, pathTicks;
    private final Profile profile;
    BotDifficulty(Profile profile) {
        this.profile = profile;
        this.meleeTicks = profile.meleeDelay();
        this.accuracy = profile.hitChance();
        this.throwTicks = profile.projectileDelay();
        this.pathTicks = profile.pathInterval();
    }

    public Profile profile() { return profile; }
    public int meleeDelayTicks() { return meleeTicks; }
    public int projectileDelayTicks() { return throwTicks; }
    public int accuracyPercent() { return accuracy; }

    public static BotDifficulty parse(String value) {
        if (value == null) return NORMAL;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return NORMAL; }
    }
}
