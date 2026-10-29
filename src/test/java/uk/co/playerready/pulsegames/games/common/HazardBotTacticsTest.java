package uk.co.playerready.pulsegames.games.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HazardBotTacticsTest {
    @Test void aBlockLandingOnYouIsWorseThanOneBesideYouAndLowerBlocksWeighMore() {
        assertTrue(HazardBotTactics.fallingDanger(0, 0, 5) > HazardBotTactics.fallingDanger(2.5, 0, 5));
        assertTrue(HazardBotTactics.fallingDanger(0, 0, 3) > HazardBotTactics.fallingDanger(0, 0, 20));
        assertEquals(0, HazardBotTactics.fallingDanger(5, 0, 5), 1e-9);
        assertEquals(0, HazardBotTactics.fallingDanger(0, 0, -3), 1e-9, "already below us");
    }

    @Test void climbsHarderTheCloserTheLavaIs() {
        assertTrue(HazardBotTactics.climbWeight(1) > HazardBotTactics.climbWeight(4));
        assertTrue(HazardBotTactics.climbWeight(4) > HazardBotTactics.climbWeight(12));
    }

    @Test void lavaCellsArePoisonAndTheNextRiseIsNearlyAsBad() {
        assertTrue(HazardBotTactics.lavaCost(60, 60, false) > 100);
        assertEquals(0, HazardBotTactics.lavaCost(62, 60, true), 1e-9);
        assertTrue(HazardBotTactics.lavaCost(61, 60, true) > 0);
        assertEquals(0, HazardBotTactics.lavaCost(61, 60, false), 1e-9);
        assertTrue(HazardBotTactics.openness(4) > HazardBotTactics.openness(1));
    }
}
