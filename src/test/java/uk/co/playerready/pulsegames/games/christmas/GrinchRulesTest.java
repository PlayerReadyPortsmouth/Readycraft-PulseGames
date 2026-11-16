package uk.co.playerready.pulsegames.games.christmas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.games.christmas.GrinchRules.*;

class GrinchRulesTest {
    @Test void freshSnowPrintsLastNinetySecondsButClearedAndIndoorsFadeSooner() {
        assertEquals(1800, printLifetime(Ground.FRESH, false));
        assertEquals(600, printLifetime(Ground.CLEARED, false));
        assertEquals(200, printLifetime(Ground.INDOORS, false));
    }
    @Test void sneakingShortensAndFadesAllSurfacePrints() {
        for (Ground ground : Ground.values()) {
            assertTrue(printLifetime(ground, true) < printLifetime(ground, false));
            assertTrue(printScale(ground, true) < printScale(ground, false));
        }
    }
    @Test void secondGrinchAppearsAtTwelvePlayers() {
        assertEquals(1, grinchCount(11)); assertEquals(2, grinchCount(12));
    }
    @Test void tagRequiresThreeHitsOrContinuousThreeSecondHold() {
        assertFalse(tagged(2, 59)); assertTrue(tagged(3, 0)); assertTrue(tagged(0, 60));
    }
    @Test void fifteenBankedOrCurrentlyHeldAtTimeoutWins() {
        assertTrue(grinchWins(15, 0, 1)); assertTrue(grinchWins(0, 1, 1));
        assertFalse(grinchWins(14, 0, 1)); assertFalse(grinchWins(0, 2, 3));
        assertTrue(grinchWins(0, 3, 3)); assertFalse(grinchWins(0, 0, 0));
    }
}
