package uk.co.playerready.pulsegames.core.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BracketTest {
    private static List<String> seeds(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add("p" + i);
        return out;
    }

    /** Plays the bracket out, the better (lower numbered) seed always winning. Returns matches actually played. */
    private static int playOut(Bracket b) {
        int played = 0;
        while (!b.finished()) {
            var ready = b.playable();
            assertFalse(ready.isEmpty(), "a bracket that is not finished must have something to play");
            for (var m : ready) {
                int x = Integer.parseInt(m.a().substring(1)), y = Integer.parseInt(m.b().substring(1));
                b.report(m, x < y ? m.a() : m.b());
                played++;
            }
        }
        return played;
    }

    @Test void seedOrderMatchesTheStandardLayout() {
        assertEquals(List.of(1, 8, 4, 5, 2, 7, 3, 6), Bracket.seedOrder(8));
        assertEquals(List.of(1, 2), Bracket.seedOrder(2));
    }

    @Test void everyFieldFromTwoToSixteenGetsTheRightByesAndPlaysOut() {
        for (int n = 2; n <= 16; n++) {
            Bracket b = Bracket.of(seeds(n), false);
            int size = Integer.highestOneBit(n) == n ? n : Integer.highestOneBit(n) << 1;
            assertEquals(size, b.size(), "size for " + n);
            Set<String> seen = new HashSet<>();
            int byes = 0;
            for (var m : b.rounds().getFirst()) {
                if (m.a() != null) assertTrue(seen.add(m.a()));
                if (m.b() != null) assertTrue(seen.add(m.b()));
                assertFalse(m.a() == null && m.b() == null, "no match may be empty, n=" + n);
                if (m.bye()) byes++;
            }
            assertEquals(n, seen.size(), "everyone is placed once, n=" + n);
            assertEquals(size - n, byes, "byes for " + n);
            assertEquals(n - 1, playOut(b), "single elimination plays n-1 matches, n=" + n);
            assertEquals("p1", b.champion());
            assertEquals(n, b.placings().size(), "everyone has a place, n=" + n);
        }
    }

    @Test void topSeedsGetTheByes() {
        Bracket b = Bracket.of(seeds(6), false);
        var byeWinners = b.rounds().getFirst().stream().filter(Bracket.Match::bye).map(Bracket.Match::winner).toList();
        assertEquals(List.of("p1", "p2"), byeWinners);
        // Byes are already advanced into round two.
        assertEquals("p1", b.rounds().get(1).getFirst().a());
        assertEquals("p2", b.rounds().get(1).get(1).a());
    }

    @Test void twoPlayersPlayOneMatchAndThereIsNoThirdPlace() {
        Bracket b = Bracket.of(seeds(2), true);
        assertFalse(b.hasThirdPlace());
        assertEquals(1, b.playable().size());
        playOut(b);
        assertEquals(Map.of("p1", 1, "p2", 2), b.placings());
    }

    @Test void thirdPlaceMatchPlaysAlongsideTheFinalFromFourEntrants() {
        Bracket b = Bracket.of(seeds(4), true);
        assertTrue(b.hasThirdPlace());
        for (var m : b.playable()) b.report(m, m.a());
        var last = b.playable();
        assertEquals(2, last.size());
        assertTrue(last.stream().anyMatch(Bracket.Match::thirdPlace));
        assertFalse(b.finished());
        for (var m : last) b.report(m, m.a());
        assertTrue(b.finished());
        var places = b.placings();
        assertEquals(1, places.get(b.champion()));
        assertEquals(Set.of(1, 2, 3, 4), new HashSet<>(places.values()));
    }

    @Test void withoutAThirdPlaceMatchBothSemifinalLosersShareThird() {
        Bracket b = Bracket.of(seeds(4), false);
        playOut(b);
        assertEquals(Map.of("p1", 1, "p2", 2, "p3", 3, "p4", 3), b.placings());
    }

    @Test void quarterFinalLosersShareFifth() {
        Bracket b = Bracket.of(seeds(8), false);
        playOut(b);
        var p = b.placings();
        assertEquals(5, p.get("p5"));
        assertEquals(5, p.get("p8"));
        assertEquals(3, p.get("p3"));
    }

    @Test void reportsAreValidated() {
        Bracket b = Bracket.of(seeds(4), false);
        var m = b.playable().getFirst();
        assertThrows(IllegalArgumentException.class, () -> b.report(m, "nobody"));
        b.report(m, m.a());
        assertThrows(IllegalArgumentException.class, () -> b.report(m, m.b()));
        assertThrows(IllegalArgumentException.class, () -> Bracket.of(seeds(1), false));
    }
}
