package uk.co.playerready.pulsegames.games.spleef;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import uk.co.playerready.pulsegames.games.spleef.SpleefTactics.Cell;
import uk.co.playerready.pulsegames.games.spleef.SpleefTactics.Grid;

class SpleefTacticsTest {
    static Grid floor(int min, int max, int... holes) {
        Set<Cell> gone = new HashSet<>();
        for (int i = 0; i < holes.length; i += 2) gone.add(new Cell(holes[i], holes[i + 1]));
        return (x, z) -> x >= min && x <= max && z >= min && z <= max && !gone.contains(new Cell(x, z));
    }

    @Test void neverDigsOwnBlockOrLastExit() {
        Grid g = floor(0, 10);
        assertFalse(SpleefTactics.safeToDig(g, new Cell(5, 5), new Cell(5, 5), 1));
        // Corner bot at (0,0) with neighbours (1,0) and (0,1): digging one leaves one exit, digging both is refused.
        assertTrue(SpleefTactics.safeToDig(g, new Cell(0, 0), new Cell(1, 0), 1));
        Grid thin = floor(0, 10, 0, 1);
        assertFalse(SpleefTactics.safeToDig(thin, new Cell(0, 0), new Cell(1, 0), 1));
    }

    @Test void refusesDigThatStrandsBotOnAnIsland() {
        // Bot at (0,0) on a corridor: (1,0) leads to the rest. The only exit is the dig.
        Grid g = (x, z) -> z == 0 && x >= 0 && x <= 8;
        assertFalse(SpleefTactics.safeToDig(g, new Cell(0, 0), new Cell(1, 0), 1));
        assertTrue(SpleefTactics.safeToDig(g, new Cell(4, 0), new Cell(7, 0), 1));
    }

    @Test void trenchCutsTwoBlocksTowardApproachingEnemy() {
        Grid g = floor(0, 12);
        assertEquals(new Cell(2, 5), SpleefTactics.trench(g, new Cell(0, 5), 0.5, 5.5, 8.5, 5.5, 1));
        assertNull(SpleefTactics.trench(g, new Cell(0, 5), 0.5, 5.5, 2.5, 5.5, 1)); // too close for a trench
    }

    @Test void hardBotPrefersTrenchForApproachingEnemyButAdjacentEnemyIsDugFirst() {
        Grid g = floor(0, 12);
        var approaching = SpleefTactics.classicTargets(g, new Cell(2, 5), 2.5, 5.5, 8.5, 5.5, -0.2, 0, true, 1);
        assertEquals(new Cell(4, 5), approaching.get(0));
        var adjacent = SpleefTactics.classicTargets(g, new Cell(2, 5), 2.5, 5.5, 4.5, 5.5, -0.2, 0, true, 1);
        assertEquals(new Cell(4, 5), adjacent.get(0)); // under the enemy
        assertFalse(adjacent.contains(new Cell(2, 5)));
    }

    @Test void predictedAheadTargetFollowsVelocityAndCapsTheLead() {
        assertEquals(new Cell(7, 5), SpleefTactics.ahead(5.5, 5.5, 0.2, 0, 8, 2)); // 1.6 blocks on
        assertEquals(new Cell(7, 5), SpleefTactics.ahead(5.5, 5.5, 1.0, 0, 8, 2)); // capped at 2
        assertEquals(new Cell(5, 5), SpleefTactics.ahead(5.5, 5.5, 0.01, 0, 8, 2)); // standing still
        Grid g = floor(0, 12);
        var targets = SpleefTactics.classicTargets(g, new Cell(2, 5), 2.5, 5.5, 8.5, 5.5, 0.2, 0, false, 1);
        assertEquals(new Cell(10, 5), targets.get(0));
    }

    @Test void decayPrefersDenseCellsAndFleesCrackingOnes() {
        Grid g = (x, z) -> x >= 0 && x <= 10 && z >= 0 && z <= 10 && !(x >= 6 && z <= 4);
        Grid none = (x, z) -> false;
        double middle = SpleefTactics.comfort(g, none, 3, 5), edge = SpleefTactics.comfort(g, none, 5, 3);
        assertTrue(middle > edge);
        Grid cracking = (x, z) -> x == 3 && z == 5;
        assertTrue(SpleefTactics.comfort(g, cracking, 3, 5) < SpleefTactics.comfort(g, cracking, 2, 5));
        assertTrue(SpleefTactics.comfort(g, cracking, 3, 4) < middle); // beside a crack
    }

    @Test void botWithNoBetterCellStillPicksARandomSafeNeighbour() {
        Grid g = floor(0, 4);
        var step = SpleefTactics.randomNeighbour(new Cell(0, 0), bound -> 0, c -> g.solid(c.x(), c.z()));
        assertEquals(new Cell(1, 0), step);
        var skip = SpleefTactics.randomNeighbour(new Cell(0, 0), bound -> 1, c -> g.solid(c.x(), c.z()));
        assertEquals(new Cell(0, 1), skip.equals(new Cell(-1, 0)) ? null : skip);
        assertNull(SpleefTactics.randomNeighbour(new Cell(0, 0), bound -> 0, c -> false));
    }

    @Test void stuckWatchFiresAfterTwoSecondsOfWantingToMove() {
        var watch = new SpleefTactics.StuckWatch();
        assertFalse(watch.update(0, 3, 3, true, 40));
        assertFalse(watch.update(39, 3, 3, true, 40));
        assertTrue(watch.update(40, 3, 3, true, 40));
        assertFalse(watch.update(41, 3, 3, true, 40));      // re-armed
        assertFalse(watch.update(100, 4, 3, true, 40));     // moved
        assertFalse(watch.update(200, 4, 3, false, 40));    // did not want to move (standing on purpose)
    }
}
