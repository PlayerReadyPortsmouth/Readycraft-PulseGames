package uk.co.playerready.pulsegames.games.prophunt;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;

class PropHuntTacticsTest {
    @Test void aHunterSweepsUncheckedSpotsBeforeRevisitingOnes() {
        var ledger = new PropHuntTactics.SearchLedger();
        ledger.check(1, 1, 0); // the near spot has just been looked at
        double[][] candidates = {{2, 2}, {20, 20}};
        double[] next = ledger.next(0, 0, candidates, 10, bound -> 0);
        assertArrayEquals(new double[]{20, 20}, next);
        assertTrue(ledger.fresh(2, 2, 10));
        assertFalse(ledger.fresh(2, 2, 10 + PropHuntTactics.CHECKED_TICKS + 1), "memory fades");
    }

    @Test void aHeardNoiseIsInvestigatedFirstThenForgotten() {
        var ledger = new PropHuntTactics.SearchLedger();
        ledger.hear(50, 50, 100);
        assertArrayEquals(new double[]{50, 50}, ledger.next(0, 0, new double[][]{{1, 1}}, 120, bound -> 0));
        assertArrayEquals(new double[]{1, 1}, ledger.next(0, 0, new double[][]{{1, 1}}, 100 + PropHuntTactics.HEARD_TICKS + 5, bound -> 0));
        ledger.hear(50, 50, 400);
        ledger.check(50, 50, 410); // already looked there
        assertArrayEquals(new double[]{1, 1}, ledger.next(0, 0, new double[][]{{1, 1}}, 420, bound -> 0));
    }

    @Test void propsHideFarFromHuntersAndApartFromEachOther() {
        double[][] spots = {{3, 0}, {30, 0}, {31, 1}, {12, 25}};
        double[] hunters = {0, 0};
        assertEquals(2, PropHuntTactics.pickHideSpot(spots, hunters, new double[0][], bound -> 0));
        assertEquals(3, PropHuntTactics.pickHideSpot(spots, hunters, new double[][]{{30, 0}}, bound -> 0),
                "the second prop avoids the first one's corner");
    }

    @Test void nervesDependOnTheTier() {
        // roll 10 (percent): panics on Easy (50) and Normal (25), not on Hard (5).
        assertEquals(PropHuntTactics.Reaction.PANIC, PropHuntTactics.react(2, true, BotDifficulty.EASY, bound -> 10));
        assertEquals(PropHuntTactics.Reaction.PANIC, PropHuntTactics.react(2, true, BotDifficulty.NORMAL, bound -> 10));
        assertEquals(PropHuntTactics.Reaction.STAY, PropHuntTactics.react(2, true, BotDifficulty.HARD, bound -> 10));
        // Unseen but close: Hard props slip away, Easy ones stay frozen.
        assertEquals(PropHuntTactics.Reaction.SNEAK_AWAY, PropHuntTactics.react(5, false, BotDifficulty.HARD, bound -> 10));
        assertEquals(PropHuntTactics.Reaction.STAY, PropHuntTactics.react(5, false, BotDifficulty.EASY, bound -> 10));
        assertEquals(PropHuntTactics.Reaction.STAY, PropHuntTactics.react(12, false, BotDifficulty.HARD, bound -> 0));
    }

    @Test void escapesAwayFromTheHunter() {
        double[] run = PropHuntTactics.escapePoint(0, 0, -5, 0, bound -> bound / 2);
        assertTrue(run[0] > 6, "runs away from a hunter on the west");
    }
}
