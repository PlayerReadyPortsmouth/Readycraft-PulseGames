package uk.co.playerready.pulsegames.games.blockparty;

import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

import java.util.function.IntUnaryOperator;

/** Pure Block Party bot reasoning: how long a bot takes to react to the call, and whether it misreads it. No Bukkit. */
final class BlockPartyTactics {
    private BlockPartyTactics() {}

    /** The call's own walking allowance already includes a second to react, so a bot never waits longer than that. */
    static final int MAX_REACT_TICKS = 20;

    /**
     * @param reactTicks   ticks after the call before the bot moves at all
     * @param wrongGuess   the bot heads for the wrong colour first
     * @param correctAfter ticks after the call at which a wrong guess is corrected (Integer.MAX_VALUE: never)
     */
    record Plan(long start, int round, int reactTicks, boolean wrongGuess, int correctAfter) {}

    static int baseReaction(BotDifficulty tier) {
        return switch (tier) { case EASY -> 14; case NORMAL -> 8; case HARD -> 3; };
    }

    /** Percent chance of heading for the wrong colour at first: only Easy does so often, Hard never. */
    static int wrongGuessPercent(BotDifficulty tier, boolean hardcore) {
        return switch (tier) { case EASY -> hardcore ? 20 : 12; case NORMAL -> 2; case HARD -> 0; };
    }

    /**
     * One decision per bot per call. Hardcore has a faster timer, so reactions are halved. A wrong guess is noticed
     * (and corrected) half way through the call about half of the time; otherwise the bot commits and loses.
     */
    static Plan plan(long start, int round, BotDifficulty tier, boolean hardcore, int callTicks, IntUnaryOperator random) {
        int react = baseReaction(tier) + random.applyAsInt(6);
        if (hardcore) react /= 2;
        react = Math.min(react, Math.min(MAX_REACT_TICKS, Math.max(2, callTicks / 4)));
        boolean wrong = random.applyAsInt(100) < wrongGuessPercent(tier, hardcore);
        int correctAfter = wrong && random.applyAsInt(100) < 50 ? Math.max(react + 4, callTicks / 2) : Integer.MAX_VALUE;
        return new Plan(start, round, react, wrong, correctAfter);
    }

    /** What the bot is doing {@code elapsed} ticks into the call. */
    enum Step { WAIT, WRONG, RIGHT }

    static Step step(Plan plan, long elapsed) {
        if (elapsed < plan.reactTicks()) return Step.WAIT;
        if (plan.wrongGuess() && elapsed < plan.correctAfter()) return Step.WRONG;
        return Step.RIGHT;
    }
}
