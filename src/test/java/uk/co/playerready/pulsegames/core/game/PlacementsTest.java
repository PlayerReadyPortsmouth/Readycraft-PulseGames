package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlacementsTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();

    @Test void lastAliveIsFirstAndLaterEliminationsRankHigher() {
        // a out first, then b, then c; d survives.
        var p = Placements.compute(Set.of(a, b, c, d), List.of(), Set.of(d), Set.of(d), List.of(a, b, c));
        assertEquals(1, p.get(d));
        assertEquals(2, p.get(c));
        assertEquals(3, p.get(b));
        assertEquals(4, p.get(a));
    }

    @Test void recordedFinishersOutrankEveryoneAndKeepTheirOrder() {
        var p = Placements.compute(Set.of(a, b, c, d), List.of(c, a), Set.of(c), Set.of(b, d), List.of());
        assertEquals(1, p.get(c));
        assertEquals(2, p.get(a));
        assertEquals(3, p.get(b));
        assertEquals(3, p.get(d));
    }

    @Test void survivorsWithNoDeclaredWinnerShareFirstAndTiesSkipPlaces() {
        var p = Placements.compute(Set.of(a, b, c, d), List.of(), Set.of(), Set.of(a, b), List.of(c, d));
        assertEquals(1, p.get(a));
        assertEquals(1, p.get(b));
        assertEquals(3, p.get(d));
        assertEquals(4, p.get(c));
    }

    @Test void departedPlayersStayInTheFieldAtTheirExitPosition() {
        var p = Placements.compute(Set.of(a, b, c), List.of(), Set.of(c), Set.of(c), List.of(a, b));
        assertEquals(3, p.get(a));
        assertEquals(2, p.get(b));
    }

    @Test void podiumGroupsTiesAndNamesThreePlaces() {
        Map<UUID, Integer> p = new LinkedHashMap<>();
        p.put(a, 1); p.put(b, 1); p.put(c, 3); p.put(d, 4);
        Map<UUID, String> names = Map.of(a, "Ana", b, "Bo", c, "Cy", d, "Di");
        assertEquals(List.of("1st: Ana, Bo", "3rd: Cy"), Placements.podium(p, names::get));
    }

    @Test void ordinalsHandleTeens() {
        assertEquals("1st", Placements.ordinal(1));
        assertEquals("2nd", Placements.ordinal(2));
        assertEquals("11th", Placements.ordinal(11));
        assertEquals("22nd", Placements.ordinal(22));
    }

    @Test void framingNeverSaysDefeat() {
        assertEquals("Finished 3rd of 8", Placements.outcome(true, false, true, false, 3, 8));
        assertEquals("Draw (joint 1st)", Placements.outcome(true, false, false, false, 1, 8));
        assertEquals("Round ended", Placements.outcome(true, false, false, true, 0, 4));
        assertEquals("Victory", Placements.outcome(true, true, true, false, 1, 4));
    }

    @Test void bonusesNeedAFieldAndPayJointFirstOnlyWhenNobodyWon() {
        assertEquals(20, Placements.bonus(2, true, 4, 4, 20, 10));
        assertEquals(10, Placements.bonus(3, true, 4, 4, 20, 10));
        assertEquals(0, Placements.bonus(4, true, 8, 4, 20, 10));
        assertEquals(0, Placements.bonus(2, true, 3, 4, 20, 10));
        assertEquals(20, Placements.bonus(1, false, 6, 4, 20, 10));
    }
}
