package uk.co.playerready.pulsegames.core.bot;

/** Bounded hesitation and attention; never grants hidden information or changes combat rules. */
final class BotPersonality {
    private BotPersonality() {}
    static double targetScore(double squaredDistance, boolean current) {
        // Keep a visible opponent unless another is substantially closer. Candidates are visibility-filtered first.
        return squaredDistance * (current ? 0.6 : 1.0);
    }
    static int reactionTicks(BotDifficulty difficulty, int jitter) {
        int base = switch (difficulty) { case EASY -> 15; case NORMAL -> 10; case HARD -> 5; };
        return base + Math.max(0, Math.min(5, jitter));
    }
}
