package uk.co.playerready.pulsegames.games.bedwars;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBridge.Cell;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBridge.Island;

class BedwarsBridgeTest {
    /** Islands are 5x5 slabs at floor y=63; everything else is void. */
    private static final int[][] ISLANDS = {{0, 0}, {10, 0}, {8, 14}};
    private static BedwarsBridge.Terrain voidMap() {
        return (x, y, z) -> {
            if (y != 63) return false;
            for (int[] i : ISLANDS) if (x >= i[0] && x < i[0] + 5 && z >= i[1] && z < i[1] + 5) return true;
            return false;
        };
    }

    private static List<Cell> path(Cell from, Cell to) {
        List<Cell> cells = new ArrayList<>(); Cell at = from;
        for (int i = 0; i < 100; i++) { Cell next = BedwarsBridge.next(from, at, to); if (next == null) break; cells.add(next); at = next; }
        return cells;
    }

    @Test void picksTheIslandWithTheShortestVoidGapNotJustTheNearest() {
        var terrain = voidMap();
        Cell here = new Cell(2, 64, 2);
        var near = new Island("near-but-far-gap", new Cell(12, 64, 2));   // 5 void cells (x 5..9)
        var far = new Island("diagonal", new Cell(10, 64, 16));           // long staircase
        assertEquals(near, BedwarsBridge.shortestGap(terrain, here, List.of(far, near), 24));
        assertEquals(5, BedwarsBridge.gap(terrain, here, near.target(), 24));
        assertTrue(BedwarsBridge.gap(terrain, here, far.target(), 24) > 5);
    }

    @Test void islandBeyondTheMaxGapIsNotChosen() {
        assertNull(BedwarsBridge.shortestGap(voidMap(), new Cell(2, 64, 2), List.of(new Island("x", new Cell(12, 64, 2))), 3));
    }

    @Test void diagonalTargetBecomesAnAlternatingFourConnectedStaircase() {
        List<Cell> cells = path(new Cell(0, 64, 0), new Cell(6, 64, 6));
        assertEquals(12, cells.size());
        Cell prev = new Cell(0, 64, 0); int longestRun = 1, run = 1; Integer lastAxis = null;
        for (Cell c : cells) {
            int dx = Math.abs(c.x() - prev.x()), dz = Math.abs(c.z() - prev.z());
            assertEquals(1, dx + dz, "never a diagonal leap");
            int axis = dx == 1 ? 0 : 1;
            run = lastAxis != null && lastAxis == axis ? run + 1 : 1; longestRun = Math.max(longestRun, run); lastAxis = axis; prev = c;
        }
        assertTrue(longestRun <= 2, "staircase, not an L");
        assertEquals(new Cell(6, 64, 6), cells.get(cells.size() - 1));
    }

    @Test void risesOneBlockAtATimeWhenTheTargetIsHigher() {
        List<Cell> cells = path(new Cell(0, 64, 0), new Cell(8, 68, 0));
        int y = 64;
        for (Cell c : cells) { assertTrue(c.y() - y >= 0 && c.y() - y <= 1); y = c.y(); }
        assertEquals(68, y);
    }
}
