package uk.co.playerready.pulsegames.core.events;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PointsTallyTest {
    private static PointsTally tally(String... ids) {
        return new PointsTally(List.of(10, 7, 5, 3, 2, 1), List.of(ids));
    }

    @Test void placementPointsFollowTheTableAndTheLastValueCoversTheRest() {
        var t = tally("a");
        assertEquals(10, t.pointsForPlace(1));
        assertEquals(1, t.pointsForPlace(6));
        assertEquals(1, t.pointsForPlace(40));
        assertEquals(0, t.pointsForPlace(0));
    }

    @Test void threeRoundsAddUpAndRankBest() {
        var t = tally("a", "b", "c");
        t.addRound(Map.of("a", 1, "b", 2, "c", 3));
        t.addRound(Map.of("a", 2, "b", 1, "c", 3));
        t.addRound(Map.of("a", 1, "b", 3, "c", 2));
        var s = t.standings();
        assertEquals("a", s.get(0).id());
        assertEquals(27, s.get(0).points());
        assertEquals(List.of(1, 2, 3), s.stream().map(PointsTally.Standing::rank).toList());
        assertEquals(3, t.rounds());
    }

    @Test void moreRoundWinsBreakEqualPoints() {
        var t = tally("winner", "steady");
        t.addRound(Map.of("winner", 1, "steady", 2));  // 10 v 7
        t.addRound(Map.of("winner", 5, "steady", 3));  // 2 v 5  -> 12 v 12
        var s = t.standings();
        assertEquals(s.get(0).points(), s.get(1).points());
        assertEquals("winner", s.get(0).id());
        assertEquals(List.of(1, 2), s.stream().map(PointsTally.Standing::rank).toList());
    }

    @Test void theBetterTotalOfPlacesBreaksEqualPointsAndWins() {
        // Anything past the table is worth one point, so 2nd + 8th and 2nd + 6th are level on points with no wins.
        var t = tally("late", "close");
        t.addRound(Map.of("late", 2, "close", 2));
        t.addRound(Map.of("late", 8, "close", 6));
        var s = t.standings();
        assertEquals(s.get(0).points(), s.get(1).points());
        assertEquals("close", s.get(0).id());
    }

    @Test void thenTheBetterLastRoundAndOtherwiseTheyShareARank() {
        var t = tally("a", "b", "c");
        t.addRound(Map.of("a", 2, "b", 2, "c", 1));
        t.addRound(Map.of("a", 1, "b", 3, "c", 2));
        t.addRound(Map.of("a", 3, "b", 1, "c", 3));
        var s = t.standings();
        // Every one has 22 points, one win and a place total of 6; only the last round (a 3rd, b 1st, c 3rd) differs.
        assertEquals(List.of(22, 22, 22), s.stream().map(PointsTally.Standing::points).toList());
        assertEquals("b", s.get(0).id());
        assertEquals(List.of(1, 2, 2), s.stream().map(PointsTally.Standing::rank).toList());
    }

    @Test void aPlayerWhoLeftScoresNothingAndRanksBehindEveryoneWhoPlayed() {
        var t = tally("a", "b", "gone");
        t.addRound(Map.of("a", 1, "b", 2));
        var s = t.standings();
        assertEquals("gone", s.getLast().id());
        assertEquals(0, s.getLast().points());
        assertEquals(3, s.getLast().rank());
    }

    @Test void anEmptyTableIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PointsTally(List.of(), List.of("a")));
    }
}
