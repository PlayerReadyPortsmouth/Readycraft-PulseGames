package uk.co.playerready.pulsegames.core.ranked;

/** Elo maths for a 1v1. Pure: no Bukkit, no storage. */
public final class Elo {
    private Elo() { }

    public static final int START = 1000;
    public static final int PLACEMENT_GAMES = 5;
    private static final int NEW_K = 32, SETTLED_K = 20, NEW_GAMES = 10;

    /** 32 for a player's first 10 rated games (counted before this one), then 20. */
    public static int k(int gamesPlayed) { return gamesPlayed < NEW_GAMES ? NEW_K : SETTLED_K; }

    public static double expected(int mine, int theirs) {
        return 1.0 / (1.0 + Math.pow(10.0, (theirs - mine) / 400.0));
    }

    /** Rating change for one player. {@code score} is 1 win, 0.5 draw, 0 loss; {@code factor} dampens repeat opponents. */
    public static int delta(int mine, int theirs, double score, int gamesPlayed, double factor) {
        return (int) Math.round(k(gamesPlayed) * (score - expected(mine, theirs)) * factor);
    }

    /** Ratings never go below zero. */
    public static int apply(int rating, int delta) { return Math.max(0, rating + delta); }

    public static boolean placed(int gamesPlayed) { return gamesPlayed >= PLACEMENT_GAMES; }
}
