package uk.co.playerready.pulsegames.games.tntrun;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;

class TntRunTacticsTest {
    @Test void prefersTheBiggerIntactAreaAndPunishesTilesItJustCrossed() {
        assertTrue(TntRunTactics.areaScore(25, 0, 3) > TntRunTactics.areaScore(9, 0, 3));
        assertTrue(TntRunTactics.areaScore(20, 0, 2) > TntRunTactics.areaScore(20, 2, 2));
        assertTrue(TntRunTactics.areaScore(12, 0, 4) > TntRunTactics.areaScore(12, 0, 1));
    }

    @Test void easyBotsRunShorterAndOccasionallyWander() {
        assertEquals(2, TntRunTactics.corridorLength(BotDifficulty.EASY));
        assertEquals(3, TntRunTactics.corridorLength(BotDifficulty.HARD));
        assertTrue(TntRunTactics.wanders(BotDifficulty.EASY, bound -> 3));
        assertFalse(TntRunTactics.wanders(BotDifficulty.EASY, bound -> 50));
        assertFalse(TntRunTactics.wanders(BotDifficulty.HARD, bound -> 0));
    }
}
