package uk.co.playerready.pulsegames.games.common;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoopSummaryTest {
    @Test void mvpIsMostPointsThenRevivesAndFramingIsNeverGameOver() {
        var a = new CoopSummary.Entry("A", 9, 0, 500);
        var b = new CoopSummary.Entry("B", 2, 3, 500);
        assertEquals("B", CoopSummary.mvp(List.of(a, b)).name());
        assertNull(CoopSummary.mvp(List.of()));
        assertFalse(CoopSummary.title(false).toLowerCase().contains("game over"));
        assertTrue(CoopSummary.title(false).contains("Well played"));
        var lines = CoopSummary.lines(7, 9, List.of(a, b));
        assertTrue(lines.get(0).contains("7") && lines.get(0).contains("map best 9"));
        assertTrue(lines.get(lines.size() - 1).contains("MVP: <white>B"));
    }
}
