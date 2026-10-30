package uk.co.playerready.pumpkinhunt.logic;

import static org.junit.jupiter.api.Assertions.*;

import uk.co.playerready.pumpkinhunt.logic.PumpkinProgress.Outcome;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PumpkinProgressTest {
    private static Set<String> ids(int n) {
        Set<String> s = new LinkedHashSet<>();
        for (int i = 1; i <= n; i++) s.add("p" + i);
        return s;
    }

    @Test void firstFindCountsAndRepeatDoesNot() {
        PumpkinProgress p = new PumpkinProgress();
        UUID a = UUID.randomUUID();
        assertEquals(new PumpkinProgress.Result(Outcome.NEW, 1), p.record(a, "A", "p1", ids(13), 1));
        assertEquals(new PumpkinProgress.Result(Outcome.ALREADY, 1), p.record(a, "A", "p1", ids(13), 2));
        assertEquals(1, p.foundCount(a, ids(13)));
    }

    @Test void thirteenthFindCompletesExactlyOnce() {
        PumpkinProgress p = new PumpkinProgress();
        UUID a = UUID.randomUUID();
        Set<String> all = ids(13);
        int completed = 0;
        for (int i = 1; i <= 13; i++) {
            Outcome o = p.record(a, "A", "p" + i, all, 100 + i).outcome();
            if (o == Outcome.COMPLETED) { completed++; assertEquals(13, i); }
        }
        assertEquals(1, completed);
        for (int i = 1; i <= 13; i++) assertEquals(Outcome.ALREADY_COMPLETE, p.record(a, "A", "p" + i, all, 999).outcome());
        assertEquals(113, p.top(10).get(0).completedAt());
    }

    @Test void removedLanternStopsCountingAndTopIsEarliestFirst() {
        PumpkinProgress p = new PumpkinProgress();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Set<String> two = ids(2);
        p.record(a, "A", "p1", two, 1);
        p.record(b, "B", "p1", two, 2);
        p.record(b, "B", "p2", two, 3);
        p.record(a, "A", "p2", two, 4);
        assertEquals(List.of("B", "A"), p.top(10).stream().map(PumpkinProgress.Finisher::name).toList());
        assertEquals(1, p.top(1).size());
        assertEquals(1, p.foundCount(a, Set.of("p1")));
    }

    @Test void statsResetAndRoundTrip() {
        PumpkinProgress p = new PumpkinProgress();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        p.record(a, "A", "p1", ids(3), 1);
        p.record(b, "B", "p1", ids(3), 2);
        p.record(b, "B", "p2", ids(3), 3);
        assertEquals(2, p.stats(ids(3)).get("p1"));
        assertEquals(0, p.stats(ids(3)).get("p3"));
        PumpkinProgress q = new PumpkinProgress();
        q.loadJson(p.toJson());
        assertEquals(Set.of("p1", "p2"), q.found(b));
        assertTrue(q.reset(a));
        assertFalse(q.reset(a));
        assertEquals(1, q.resetAll());
        assertEquals(0, q.players());
    }
}
