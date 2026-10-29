package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.WatchList.Candidate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WatchListTest {
    private static Candidate c(String name, boolean close, int score) {
        return new Candidate(UUID.randomUUID(), name, close, score, 20, 20);
    }

    @Test void closePlayersFirstThenScoreThenName() {
        var far = c("Zed", false, 9);
        var near = c("Amy", true, 1);
        var nearHigh = c("Bo", true, 5);
        var farLow = c("Al", false, 2);
        var farTie = c("Ab", false, 2);
        assertEquals(List.of(nearHigh, near, far, farTie, farLow), WatchList.order(List.of(far, near, nearHigh, farLow, farTie), 45));
    }

    @Test void capKeepsTheBestOnes() {
        assertEquals(1, WatchList.order(List.of(c("a", false, 0), c("b", true, 0)), 1).size());
        assertEquals("b", WatchList.order(List.of(c("a", false, 0), c("b", true, 0)), 1).get(0).name());
    }

    @Test void leaderIsHighestScoreAndAbsentWhenNobodyScored() {
        var a = c("a", false, 3);
        var b = c("b", false, 7);
        assertSame(b, WatchList.leader(List.of(a, b)));
        assertNull(WatchList.leader(List.of(c("x", false, 0))));
    }

    @Test void statsShowScoreAndHearts() {
        assertEquals("Score: 4  Hearts: 5/10", WatchList.stats(new Candidate(UUID.randomUUID(), "n", false, 4, 9, 20)));
        assertEquals("Hearts: 10/10", WatchList.stats(c("n", false, 0)));
    }
}
