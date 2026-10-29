package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParkourRunTest {
    @Test void timerAndFormat() {
        var run = new ParkourRun(1_000, 0);
        assertEquals(65_320, run.elapsed(66_320));
        assertEquals("1:05.32", ParkourRun.format(65_320));
        assertEquals("0:00.00", ParkourRun.format(0));
    }

    @Test void checkpointsCountInOrderOnly() {
        var run = new ParkourRun(0, 2);
        assertFalse(run.reachCheckpoint(1));
        assertTrue(run.reachCheckpoint(0));
        assertFalse(run.reachCheckpoint(0));
        assertFalse(run.canFinish());
        assertEquals(0, run.respawnIndex());
        assertTrue(run.reachCheckpoint(1));
        assertTrue(run.canFinish());
        assertEquals(-1, new ParkourRun(0, 2).respawnIndex());
    }

    @Test void fallLimitIsConfiguredOrJustBelowTheLowestPlate() {
        assertEquals(40.0, ParkourRun.fallLimit(40.0, List.of(64.0, 70.0)));
        assertEquals(62.0, ParkourRun.fallLimit(null, List.of(64.0, 70.0)));
        assertTrue(ParkourRun.fell(61.9, 62.0));
        assertFalse(ParkourRun.fell(62.0, 62.0));
    }
}
