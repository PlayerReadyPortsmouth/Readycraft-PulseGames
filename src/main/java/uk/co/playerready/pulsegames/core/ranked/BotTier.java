package uk.co.playerready.pulsegames.core.ranked;

import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

/** Which bot tier suits a rating: the tier whose anchor rating is nearest. */
public final class BotTier {
    private BotTier() { }

    public static BotDifficulty forRating(int rating, int easyAt, int normalAt, int hardAt) {
        int toEasy = Math.abs(rating - easyAt), toNormal = Math.abs(rating - normalAt), toHard = Math.abs(rating - hardAt);
        if (toEasy <= toNormal && toEasy <= toHard) return BotDifficulty.EASY;
        return toNormal <= toHard ? BotDifficulty.NORMAL : BotDifficulty.HARD;
    }
}
