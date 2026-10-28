package uk.co.playerready.pulsegames.games.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoundTrackerTest {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();

    @Test void longestSurvivorScoresMostAndFirstOutScoresOne() {
        var t = new RoundTracker(3);
        t.startRound(List.of(a, b, c, d));
        t.knockOut(b); t.knockOut(d); t.knockOut(a);
        var pts = RoundTracker.pointsFor(t.eliminationTiers());
        assertEquals(4, pts.get(c));
        assertEquals(3, pts.get(a));
        assertEquals(2, pts.get(d));
        assertEquals(1, pts.get(b));
    }

    @Test void survivorsOfAClockOutShareTheTopScoreAndOutsRankBehindThem() {
        var t = new RoundTracker(3);
        t.startRound(List.of(a, b, c, d));
        t.knockOut(d);
        var pts = RoundTracker.pointsFor(t.eliminationTiers());
        assertEquals(4, pts.get(a)); assertEquals(4, pts.get(b)); assertEquals(4, pts.get(c));
        assertEquals(1, pts.get(d));
        assertEquals(1, RoundTracker.placementsFor(t.eliminationTiers()).get(a));
        assertEquals(4, RoundTracker.placementsFor(t.eliminationTiers()).get(d));
    }

    @Test void pointsCarryAcrossRoundsAndRoundWinsBreakTies() {
        var t = new RoundTracker(3);
        t.startRound(List.of(a, b, c));
        t.knockOut(b); t.knockOut(c); t.award(t.eliminationTiers());   // a 3, c 2, b 1
        t.startRound(List.of(a, b, c));
        t.knockOut(a); t.knockOut(c); t.award(t.eliminationTiers());   // b 3, c 2, a 1
        assertEquals(4, t.total(a)); assertEquals(4, t.total(b)); assertEquals(4, t.total(c));
        // Level on points, each has one round win except c, who has none.
        assertEquals(List.of(a, b), t.matchWinners(List.of(a, b, c)));
        t.startRound(List.of(a, b, c));
        t.knockOut(b); t.knockOut(a); t.award(t.eliminationTiers());   // c 3, a 2, b 1
        assertEquals(List.of(c), t.matchWinners(List.of(a, b, c)));
        assertEquals(7, t.total(c)); assertEquals(6, t.total(a)); assertEquals(5, t.total(b));
    }

    @Test void leaverCannotWinAndRoundEndsWhenOneIsLeft() {
        var t = new RoundTracker(3);
        t.startRound(List.of(a, b));
        t.award(List.of(List.of(a), List.of(b)));
        t.leave(a);
        assertEquals(List.of(b), t.matchWinners(List.of(b)));
        assertTrue(t.lastStanding());
        assertFalse(t.knockOut(a));
    }
}
