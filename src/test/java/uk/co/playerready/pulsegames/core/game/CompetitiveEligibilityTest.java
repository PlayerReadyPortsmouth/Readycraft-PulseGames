package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompetitiveEligibilityTest {
    @Test void largerMapsRequireAtLeastHalfActualStartingRosterHuman() {
        assertTrue(CompetitiveEligibility.eligible(6, 6, 12));
        assertTrue(CompetitiveEligibility.eligible(7, 5, 12));
        assertFalse(CompetitiveEligibility.eligible(5, 6, 12));
        assertTrue(CompetitiveEligibility.eligible(2, 2, 12));
    }
    @Test void smallMapsRequireStrictHumanMajority() {
        assertTrue(CompetitiveEligibility.eligible(3, 1, 4));
        assertFalse(CompetitiveEligibility.eligible(2, 2, 4));
        assertFalse(CompetitiveEligibility.eligible(1, 1, 2));
        assertTrue(CompetitiveEligibility.eligible(2, 1, 3));
    }
    @Test void humanOnlyRoundsStayEligible() {
        assertTrue(CompetitiveEligibility.eligible(2, 0, 2));
        assertTrue(CompetitiveEligibility.eligible(5, 0, 12));
    }
    @Test void invalidOrBotOnlyRostersAreNeverEligible() {
        assertFalse(CompetitiveEligibility.eligible(0, 2, 12));
        assertFalse(CompetitiveEligibility.eligible(7, 6, 12));
        assertFalse(CompetitiveEligibility.eligible(2, -1, 12));
        assertFalse(CompetitiveEligibility.eligible(2, 0, 0));
        assertFalse(CompetitiveEligibility.eligible(Integer.MAX_VALUE, Integer.MAX_VALUE, 12));
    }
    @Test void aLoneHumanInACompetitiveModeIsUnranked() {
        // Dropper practice (min-players 1) was a one-player token loop.
        assertFalse(CompetitiveEligibility.eligible(1, 0, 12, false));
        assertTrue(CompetitiveEligibility.eligible(2, 0, 12, false));
        assertTrue(CompetitiveEligibility.eligible(1, 1, 12, false), "a bot opponent still counts as a contest");
        assertTrue(CompetitiveEligibility.eligible(1, 0, 8, true), "co-op against the map stays ranked");
    }
}
