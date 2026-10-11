package uk.co.playerready.pulsegames.core.bot;

import java.util.Locale;

/** Timing and accuracy only: all levels retain normal reach, damage and visibility rules. */
public enum BotDifficulty {
    EASY(28, 45, 40, 30), NORMAL(14, 80, 20, 20), HARD(10, 95, 12, 10);

    final int meleeTicks, accuracy, throwTicks, pathTicks;
    BotDifficulty(int meleeTicks, int accuracy, int throwTicks, int pathTicks) {
        this.meleeTicks = meleeTicks;
        this.accuracy = accuracy;
        this.throwTicks = throwTicks;
        this.pathTicks = pathTicks;
    }

    public int meleeDelayTicks() { return meleeTicks; }
    public int projectileDelayTicks() { return throwTicks; }
    public int accuracyPercent() { return accuracy; }

    public static BotDifficulty parse(String value) {
        if (value == null) return NORMAL;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return NORMAL; }
    }
}
