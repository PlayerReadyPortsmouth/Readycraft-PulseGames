package uk.co.playerready.pulsegames.core.game;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Pure rules for ending a clearly decided match early. A game reports a "decided" signal each second;
 * the {@link Timer} turns that into a hold, a short visible countdown and the end, and cancels the
 * countdown if the signal clears (the trailing side scored). No Bukkit state, so every rule is unit tested.
 */
public final class Mercy {
    private Mercy() {}

    public static final String NOTE = "Ended early: clearly decided";

    // ---- config ------------------------------------------------------------------------

    /** {@code mercy.enabled} and {@code mercy.<game>.enabled}, both on unless switched off. */
    public static boolean enabled(ConfigurationSection config, String gameId) {
        return config.getBoolean("mercy.enabled", true) && config.getBoolean("mercy." + gameId + ".enabled", true);
    }

    public static int countdownSeconds(ConfigurationSection config) {
        return Math.max(1, Math.min(60, config.getInt("mercy.countdown-seconds", 10)));
    }

    public static int holdSeconds(ConfigurationSection config, String gameId, int fallback) {
        return Math.max(0, config.getInt("mercy." + gameId + ".hold-seconds", fallback));
    }

    // ---- decision rules ----------------------------------------------------------------

    /**
     * Bedwars: every other team has no bed and the leading team has at least {@code ratio} times their
     * combined living players. (A side with no bed and nobody alive already ends the match normally.)
     */
    public static boolean bedwars(boolean allOthersBedless, int leaderAlive, int othersAlive, double ratio) {
        return allOthersBedless && leaderAlive > 0 && leaderAlive >= ratio * Math.max(1, othersAlive);
    }

    /** Score games: the gap is at least {@code gapFraction} of the target with less than {@code timeFraction} of the clock left. */
    public static boolean scoreGap(int leader, int second, int target, int secondsLeft, int secondsTotal,
                                   double gapFraction, double timeFraction) {
        if (target <= 0 || secondsTotal <= 0) return false;
        return leader - second >= gapFraction * target && secondsLeft < timeFraction * secondsTotal;
    }

    /** {@link #scoreGap} with the {@code mercy.<game>.gap-percent} (60) and {@code time-left-percent} (40) settings. */
    public static boolean scoreGap(ConfigurationSection config, String gameId, int leader, int second, int target,
                                   int secondsLeft, int secondsTotal) {
        return scoreGap(leader, second, target, secondsLeft, secondsTotal,
                config.getDouble("mercy." + gameId + ".gap-percent", 60) / 100.0,
                config.getDouble("mercy." + gameId + ".time-left-percent", 40) / 100.0);
    }

    /** The leading side in a score game, with the runner-up's score (0 when there is only one side). */
    public record Lead<T>(T member, int best, int second) {}

    /** Groups members by side (a team, or the player alone), scores each side once, and returns the leader. */
    public static <T> Lead<T> leading(java.util.Collection<T> members, java.util.function.Function<T, Object> side,
                                      java.util.function.ToIntFunction<T> sideScore) {
        java.util.Map<Object, T> rep = new java.util.LinkedHashMap<>();
        for (T member : members) rep.putIfAbsent(side.apply(member), member);
        T best = null;
        int top = Integer.MIN_VALUE, second = 0;
        for (T member : rep.values()) {
            int score = sideScore.applyAsInt(member);
            if (score > top) { if (best != null) second = Math.max(second, top); best = member; top = score; }
            else second = Math.max(second, score);
        }
        return best == null ? null : new Lead<>(best, top, Math.max(0, second));
    }

    /** Rounds mode: the runner-up cannot catch the leader even by winning every remaining round. */
    public static boolean roundsUnreachable(int leader, int second, int roundsLeft, int maxPointsPerRound) {
        return roundsLeft > 0 && leader - second > (long) roundsLeft * maxPointsPerRound;
    }

    /** Karts: somebody has finished, no human is still racing and only bots remain on the track. */
    public static boolean botsOnly(boolean humanFinished, int humansRacing, int botsRacing) {
        return humanFinished && humansRacing == 0 && botsRacing > 0;
    }

    // ---- stats -------------------------------------------------------------------------

    public enum Stat { NONE, LOSS, DRAW }

    /**
     * What a non-winner's record gets. A forced stop is neither; a co-op round nobody won is a loss;
     * any round with a declared winner (a mercy end always has one) is a loss for the rest; only a round
     * that ends with no winner at all is a draw.
     */
    public static Stat statForNonWinner(boolean forced, boolean anyWinner, boolean coop) {
        if (forced) return Stat.NONE;
        return anyWinner || coop ? Stat.LOSS : Stat.DRAW;
    }

    // ---- wording -----------------------------------------------------------------------

    public static String headline(String who, boolean plural) {
        return "Game over: " + who + (plural ? " win" : " wins") + " by a mile";
    }

    // ---- the countdown -----------------------------------------------------------------

    public enum Phase { IDLE, HOLDING, START, COUNT, CANCEL, FIRE }

    public record Step(Phase phase, int secondsLeft) {}

    /** Call once a second with the game's current "decided" signal. */
    public static final class Timer {
        private int held;
        private int left = -1;

        public boolean counting() { return left >= 0; }

        public Step tick(boolean decided, int holdSeconds, int countdownSeconds) {
            if (!decided) {
                boolean was = counting();
                held = 0;
                left = -1;
                return new Step(was ? Phase.CANCEL : Phase.IDLE, 0);
            }
            if (counting()) {
                if (--left <= 0) { left = -1; held = 0; return new Step(Phase.FIRE, 0); }
                return new Step(Phase.COUNT, left);
            }
            if (++held <= holdSeconds) return new Step(Phase.HOLDING, holdSeconds - held + 1);
            left = Math.max(1, countdownSeconds);
            return new Step(Phase.START, left);
        }
    }
}
