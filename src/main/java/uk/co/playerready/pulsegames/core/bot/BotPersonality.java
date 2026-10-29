package uk.co.playerready.pulsegames.core.bot;

import java.util.UUID;

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
    /** 0..1 each, stable per bot, so two bots in one match fight differently. */
    static double aggression(UUID id) { return trait(id, 0x9E3779B97F4A7C15L); }
    static double strafeBias(UUID id) { return trait(id, 0xC2B2AE3D27D4EB4FL); }
    static double jumpiness(UUID id) { return trait(id, 0x165667B19E3779F9L); }
    private static double trait(UUID id, long salt) {
        long z = (id.getMostSignificantBits() * 31 + id.getLeastSignificantBits()) ^ salt;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) / (double) (1L << 53);
    }
}
