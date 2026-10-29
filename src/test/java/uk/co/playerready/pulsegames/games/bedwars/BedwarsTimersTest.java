package uk.co.playerready.pulsegames.games.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BedwarsTimersTest {
    @Test void upgradeCountdownFollowsTheFiveAndTenMinuteMarks() {
        assertEquals(130, BedwarsTimers.untilUpgrade(170));
        assertEquals(300, BedwarsTimers.untilUpgrade(300));
        assertEquals(1, BedwarsTimers.untilUpgrade(599));
        assertEquals(-1, BedwarsTimers.untilUpgrade(600));
    }

    @Test void diamondCountdownMatchesTheDropSchedule() {
        assertEquals(14, BedwarsTimers.untilDiamonds(16));
        assertEquals(30, BedwarsTimers.untilDiamonds(30));
        assertEquals(25, BedwarsTimers.untilDiamonds(300));
        assertEquals(20, BedwarsTimers.untilDiamonds(600));
    }

    @Test void sidebarTextAndLineBudget() {
        assertEquals("<gray>Next upgrade: <yellow>2:10", BedwarsTimers.lines(170, 2).get(0));
        assertEquals("<gray>Diamonds in <aqua>0:14", BedwarsTimers.lines(16, 2).get(1));
        assertEquals(2, BedwarsTimers.lines(10, 4).size());
        assertEquals(1, BedwarsTimers.lines(10, 8).size());
        assertTrue(BedwarsTimers.lines(700, 2).get(0).contains("Max tier"));
        assertEquals("0:14", BedwarsTimers.clock(14));
    }
}
