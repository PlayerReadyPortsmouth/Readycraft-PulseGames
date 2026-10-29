package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsKitShapes.Kind;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsKitShapes.Pos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BedwarsKitShapesTest {
    @Test void bridgeEggLaysOneBlockUnderTheFlightWithoutGapsOrRepeats() {
        List<Pos> path = BedwarsKitShapes.bridgeCells(0.5, 65.2, 0.5, 4.3, 65.2, 0.5);
        assertEquals(new Pos(4, 64, 0), path.get(path.size() - 1));
        assertEquals(path.size(), new HashSet<>(path).size(), "no repeated cells");
        for (int i = 1; i < path.size(); i++) {
            Pos a = path.get(i - 1), b = path.get(i);
            assertTrue(Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) <= 1, "no gap between " + a + " and " + b);
        }
        assertTrue(path.stream().allMatch(p -> p.y() == 64));
    }

    @Test void bridgeFollowsADescendingFlight() {
        List<Pos> path = BedwarsKitShapes.bridgeCells(0.5, 66.5, 0.5, 2.5, 64.5, 0.5);
        assertEquals(64 - 1, path.get(path.size() - 1).y());
        assertTrue(path.get(0).y() >= path.get(path.size() - 1).y());
    }

    @Test void towerIsAHollowThreeByThreeFiveHighWithADoorAndOneLadderColumn() {
        var pieces = BedwarsKitShapes.tower(new Pos(0, 64, 0), 0, 1);
        long ladders = pieces.stream().filter(p -> p.kind() == Kind.LADDER).count();
        long wool = pieces.stream().filter(p -> p.kind() == Kind.WOOL).count();
        assertEquals(BedwarsKitShapes.TOWER_HEIGHT, ladders);
        assertEquals(8 * BedwarsKitShapes.TOWER_HEIGHT - 2, wool, "eight wall cells per level, minus a two-high doorway");
        assertTrue(pieces.stream().noneMatch(p -> p.pos().equals(new Pos(0, 64, 1)) || p.pos().equals(new Pos(0, 65, 1))), "doorway on the player's side");
        assertTrue(pieces.stream().allMatch(p -> p.pos().y() >= 64 && p.pos().y() < 64 + BedwarsKitShapes.TOWER_HEIGHT));
        assertTrue(pieces.stream().allMatch(p -> Math.abs(p.pos().x()) <= 1 && p.pos().z() >= 1 && p.pos().z() <= 3), "stays in a 3x3 in front");
    }

    @Test void towerNeverBuildsOverBedsChestsOrProtectedCells() {
        assertFalse(BedwarsKitShapes.canReplace(Material.RED_BED));
        assertFalse(BedwarsKitShapes.canReplace(Material.CHEST));
        assertFalse(BedwarsKitShapes.canReplace(Material.END_STONE));
        assertTrue(BedwarsKitShapes.canReplace(Material.AIR));
        Set<Pos> bed = Set.of(new Pos(1, 64, 2));
        Set<Pos> chest = Set.of(new Pos(-1, 65, 3));
        List<Pos> cells = BedwarsKitShapes.tower(new Pos(0, 64, 0), 0, 1).stream().map(p -> p.pos()).toList();
        List<Pos> built = BedwarsKitShapes.buildable(cells, p -> !chest.contains(p), bed::contains);
        assertFalse(built.contains(new Pos(1, 64, 2)));
        assertFalse(built.contains(new Pos(-1, 65, 3)));
        assertEquals(cells.size() - 2, built.size());
    }

    @Test void wallIsFiveWideThreeHighTwoAhead() {
        List<Pos> wall = BedwarsKitShapes.wall(new Pos(0, 64, 0), 1, 0);
        assertEquals(15, wall.size());
        assertTrue(wall.stream().allMatch(p -> p.x() == 2 && p.y() >= 64 && p.y() <= 66 && Math.abs(p.z()) <= 2));
    }

    @Test void iceRoadIsEightBlocksInTheLookDirection() {
        List<Pos> road = BedwarsKitShapes.iceRoad(new Pos(0, 64, 0), -1, 0);
        assertEquals(8, road.size());
        assertEquals(new Pos(-1, 63, 0), road.get(0));
        assertEquals(new Pos(-8, 63, 0), road.get(7));
    }

    @Test void facingAndHoneyPatch() {
        assertArrayEquals(new int[]{1, 0}, BedwarsKitShapes.cardinal(0.9, 0.3));
        assertArrayEquals(new int[]{0, -1}, BedwarsKitShapes.cardinal(0.1, -0.8));
        assertEquals(9, BedwarsKitShapes.honeyPatch(new Pos(5, 60, 5)).size());
    }
}
