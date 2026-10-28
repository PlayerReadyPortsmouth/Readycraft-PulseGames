package uk.co.playerready.pulsegames.games.partygames;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyScoringTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();

    @Test void placementsPay5_3_1PlusOneForTakingPart() {
        Map<UUID, Integer> points = PartyScoring.award(List.of(List.of(a), List.of(b), List.of(c)), List.of(a, b, c, d), 1);
        assertEquals(6, points.get(a));
        assertEquals(4, points.get(b));
        assertEquals(2, points.get(c));
        assertEquals(1, points.get(d));
    }

    @Test void tiedFirstShareTopPointsAndSkipTheLowerPlaces() {
        Map<UUID, Integer> points = PartyScoring.award(List.of(List.of(a, b), List.of(c)), List.of(a, b, c, d), 1);
        assertEquals(6, points.get(a));
        assertEquals(6, points.get(b));
        assertEquals(2, points.get(c)); // two placed above, so third place pays 1, plus 1 for taking part
        assertEquals(1, points.get(d));
    }

    @Test void chaosRoundDoublesEverything() {
        Map<UUID, Integer> points = PartyScoring.award(List.of(List.of(a)), List.of(a, b), 2);
        assertEquals(12, points.get(a));
        assertEquals(2, points.get(b));
    }

    @Test void leaversAndNobodyPlacedStillBehave() {
        assertEquals(Map.of(a, 1), PartyScoring.award(List.of(List.of(b)), List.of(a), 1));
        assertEquals(Map.of(a, 1, b, 1), PartyScoring.award(List.of(), List.of(a, b), 1));
    }
}
