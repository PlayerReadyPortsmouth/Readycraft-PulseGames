package uk.co.playerready.pulsegames.games.bedwars;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBridge.Cell;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBridge.Island;

class BedwarsHopPlannerTest {
    /** Three 5-wide islands in a row along x at floor y=63: x 0-4, x 20-24, x 40-44; gaps of 15 between them. */
    private static BedwarsBridge.Terrain row() {
        return (x, y, z) -> y == 63 && z >= 0 && z < 5 && ((x >= 0 && x < 5) || (x >= 20 && x < 25) || (x >= 40 && x < 45));
    }
    private static final Cell START = new Cell(2, 64, 2), MID = new Cell(22, 64, 2), BED = new Cell(42, 64, 2);

    @Test void splitsALongGapIntoHopsThatEachFit() {
        var route = BedwarsHopPlanner.plan(row(), START, BED, List.of(new Island("mid", MID)), 24);
        assertNotNull(route);
        assertEquals(List.of(MID, BED), route.stream().map(BedwarsHopPlanner.Hop::to).toList());
        assertEquals(15, route.get(0).gap()); assertEquals(15, route.get(1).gap());
    }

    @Test void maxGapIsAHardLimitPerHop() {
        assertNull(BedwarsHopPlanner.plan(row(), START, BED, List.of(new Island("mid", MID)), 14));
        assertNull(BedwarsHopPlanner.plan(row(), START, BED, List.of(), 24)); // direct gap is 30
    }

    @Test void goesStraightWhenTheDirectBridgeFits() {
        var route = BedwarsHopPlanner.plan(row(), START, BED, List.of(new Island("mid", MID)), 40);
        assertEquals(1, route.size()); assertEquals(BED, route.get(0).to());
        assertEquals(30, route.get(0).gap()); // the middle island is crossed on foot
    }

    @Test void walkingNeedsNoBlocks() {
        var route = BedwarsHopPlanner.plan(row(), START, new Cell(4, 64, 4), List.of(), 24);
        assertEquals(0, route.get(0).gap());
        assertEquals(0, BedwarsHopPlanner.blocksNeeded(route, 1));
    }

    @Test void blockStockIsGapPlusAQuarterRoundedUp() {
        assertEquals(0, BedwarsHopPlanner.blocksNeeded(0));
        assertEquals(2, BedwarsHopPlanner.blocksNeeded(1));
        assertEquals(19, BedwarsHopPlanner.blocksNeeded(15));
        assertEquals(25, BedwarsHopPlanner.blocksNeeded(20));
        var route = BedwarsHopPlanner.plan(row(), START, BED, List.of(new Island("mid", MID)), 24);
        assertEquals(19, BedwarsHopPlanner.blocksNeeded(route, 1));
        assertEquals(38, BedwarsHopPlanner.blocksNeeded(route, 2));
    }
}
