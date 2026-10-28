package uk.co.playerready.pulsegames.games.common;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegionSnapshotLimitTest {
    @Test void aMillionBlockArenaIsRefusedWithAConsoleWarning() {
        List<String> console = new ArrayList<>();
        String refusal = RegionSnapshot.refusal("Avalanche", List.of(new Cuboid(0, 0, 0, 99, 99, 99)), console::add);
        assertNotNull(refusal);
        assertTrue(refusal.contains("300000"));
        assertEquals(1, console.size());
        assertTrue(console.get(0).contains("1000000"));
    }

    @Test void theLimitIsInclusiveAndSumsEveryRegion() {
        List<String> console = new ArrayList<>();
        assertNull(RegionSnapshot.refusal("Spleef", List.of(new Cuboid(0, 0, 0, 99, 2, 999)), console::add), "exactly 300000");
        assertTrue(console.isEmpty());
        assertNotNull(RegionSnapshot.refusal("Spleef", List.of(new Cuboid(0, 0, 0, 99, 2, 999), new Cuboid(0, 5, 0, 0, 5, 0)), console::add));
    }
}
