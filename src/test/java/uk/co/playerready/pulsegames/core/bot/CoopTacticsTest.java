package uk.co.playerready.pulsegames.core.bot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoopTacticsTest {
    @Test void takesTheMobNearestTheObjectiveNotTheNearestToTheBot() {
        double[] guard = {0, 0};
        double[][] mobs = {{12, 0}, {3, 0}};
        // The bot stands at (14, 0): the first mob is on top of it, the second is at the villagers.
        assertEquals(1, CoopTactics.pickTarget(guard, 14, 0, mobs, -1));
    }

    @Test void staysOnItsCurrentMobUnlessAnotherIsClearlyWorse() {
        double[] guard = {0, 0};
        double[][] mobs = {{6, 0}, {5.5, 0}};
        assertEquals(0, CoopTactics.pickTarget(guard, 8, 0, mobs, 0));
        assertEquals(1, CoopTactics.pickTarget(guard, 8, 0, mobs, -1));
        assertEquals(-1, CoopTactics.pickTarget(guard, 0, 0, new double[0][], -1));
    }

    @Test void lanesSpreadRoundTheObjectiveFacingTheThreat() {
        double[] centre = {0, 0}, threat = {10, 0};
        double[] a = CoopTactics.lanePoint(centre, 0, 3, 3.5, threat, new double[0][]);
        double[] b = CoopTactics.lanePoint(centre, 1, 3, 3.5, threat, new double[0][]);
        double[] c = CoopTactics.lanePoint(centre, 2, 3, 3.5, threat, new double[0][]);
        assertEquals(3.5, Math.hypot(b[0], b[1]), 1e-9);
        assertEquals(3.5, b[0], 1e-9, "the middle lane faces the threat");
        assertTrue(Math.hypot(a[0] - c[0], a[1] - c[1]) > 4, "lanes are apart, not stacked");
    }

    @Test void aLaneNeverSitsOnAHuman() {
        double[] spot = CoopTactics.lanePoint(new double[]{0, 0}, 0, 1, 3.5, new double[]{10, 0}, new double[][]{{3.5, 0.5}});
        assertTrue(Math.hypot(spot[0] - 3.5, spot[1] - 0.5) >= 2.5);
    }

    @Test void kitesOnlyWhenHurtAndCloseAndHoldsFarFromTheObjective() {
        assertTrue(CoopTactics.shouldKite(0.3, 0.2, 3));
        assertFalse(CoopTactics.shouldKite(0.9, 0.2, 3));
        assertFalse(CoopTactics.shouldKite(0.3, 0.2, 9));
        assertTrue(CoopTactics.shouldHold(new double[]{0, 0}, 20, 0));
        assertFalse(CoopTactics.shouldHold(new double[]{0, 0}, 8, 0));
        assertFalse(CoopTactics.shouldHold(null, 99, 0));
    }
}
