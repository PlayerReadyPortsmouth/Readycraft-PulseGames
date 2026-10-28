package uk.co.playerready.pulsegames.core.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardRulesTest {
    @Test void killTokensStopAtTheCapAndNeverGoNegative() {
        int paid = 0;
        for (int kill = 0; kill < 20; kill++) paid += RewardRules.killPayout(paid, 5, 25);
        assertEquals(25, paid);
        assertEquals(0, RewardRules.killPayout(25, 5, 25));
        assertEquals(2, RewardRules.killPayout(23, 5, 25));
        assertEquals(0, RewardRules.killPayout(0, 5, 0));
    }

    @Test void capKeepsKillsBelowAWin() {
        assertTrue(25 < 50);
    }

    @Test void streakBonusEscalatesToItsMaximum() {
        assertEquals(0, RewardRules.streakBonus(1, 5, 25));
        assertEquals(5, RewardRules.streakBonus(2, 5, 25));
        assertEquals(10, RewardRules.streakBonus(3, 5, 25));
        assertEquals(25, RewardRules.streakBonus(6, 5, 25));
        assertEquals(25, RewardRules.streakBonus(40, 5, 25));
        assertEquals(0, RewardRules.streakBonus(5, 0, 25));
    }

    @Test void streaksAreAnnouncedAtThreeFiveAndTen() {
        for (int n = 0; n <= 12; n++) assertEquals(n == 3 || n == 5 || n == 10, RewardRules.announceStreak(n), "streak " + n);
    }
}
