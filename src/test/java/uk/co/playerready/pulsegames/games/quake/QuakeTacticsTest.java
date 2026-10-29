package uk.co.playerready.pulsegames.games.quake;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;

class QuakeTacticsTest {
    @Test void aimErrorShrinksWithTier() {
        double easy = QuakeTactics.yawError(BotDifficulty.EASY.profile());
        double normal = QuakeTactics.yawError(BotDifficulty.NORMAL.profile());
        double hard = QuakeTactics.yawError(BotDifficulty.HARD.profile());
        assertTrue(easy > normal && normal > hard);
        assertTrue(hard < 2);
        assertEquals(easy, QuakeTactics.error(easy, 5), 1e-9, "clamped to the limit");
    }

    @Test void strafeAlwaysMovesFlipsSidesAndDiffersBetweenBots() {
        var profile = BotDifficulty.NORMAL.profile();
        boolean left = false, right = false;
        for (long tick = 0; tick < 400; tick += 5) {
            double s = QuakeTactics.strafe(tick, 42L, profile);
            assertTrue(Math.abs(s) >= 1.5);
            if (s > 0) left = true; else right = true;
        }
        assertTrue(left && right);
        int differ = 0;
        for (long tick = 0; tick < 400; tick += 5)
            if (QuakeTactics.strafe(tick, 42L, profile) != QuakeTactics.strafe(tick, 99L, profile)) differ++;
        assertTrue(differ > 10, "two bots do not mirror each other");
    }

    @Test void highStrafeBiasCirclesWiderMoreOften() {
        int wideHard = 0, wideEasy = 0;
        for (long tick = 0; tick < 4000; tick += 20) {
            if (Math.abs(QuakeTactics.strafe(tick, 7L, BotDifficulty.HARD.profile())) == 3) wideHard++;
            if (Math.abs(QuakeTactics.strafe(tick, 7L, BotDifficulty.EASY.profile())) == 3) wideEasy++;
        }
        assertTrue(wideHard > wideEasy);
    }
}
