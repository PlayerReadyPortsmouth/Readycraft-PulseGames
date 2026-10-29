package uk.co.playerready.pulsegames.games.tntrun;

import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

import java.util.function.IntUnaryOperator;

/** Pure TNT Run bot reasoning: how good a tile is to run towards. No Bukkit. */
final class TntRunTactics {
    private TntRunTactics() {}

    /** The window scored round a tile is 5 x 5. */
    static final int WINDOW_CELLS = 25;

    /**
     * Higher is better. The share of untouched floor round the tile dominates (the biggest intact area wins), tiles
     * the bot has crossed lately cost 3 each, and room to keep going (open neighbours) breaks ties.
     */
    static double areaScore(int intactInWindow, int recentVisits, int openNeighbours) {
        return 10.0 * intactInWindow / WINDOW_CELLS - recentVisits * 3.0 + openNeighbours * 0.5;
    }

    /** Tiles a bot runs in a straight line before choosing again; the floor goes in about a second so keep rolling. */
    static int corridorLength(BotDifficulty tier) {
        return tier == BotDifficulty.EASY ? 2 : 3;
    }

    /** Easy bots sometimes pick a random open direction instead of the best one (but never stand still). */
    static boolean wanders(BotDifficulty tier, IntUnaryOperator random) {
        return tier == BotDifficulty.EASY && random.applyAsInt(100) < 8;
    }
}
