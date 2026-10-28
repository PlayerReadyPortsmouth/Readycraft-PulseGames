package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LeaverWinTest {
    @Test void onlyDepartureEndingsAreLeaverWins() {
        assertTrue(LeaverWin.byDefault("last-standing"));
        assertTrue(LeaverWin.byDefault("last-team"));
        assertFalse(LeaverWin.byDefault("kills"));
        assertFalse(LeaverWin.byDefault("time"));
    }

    @Test void aWinInTheFirstMinuteDoesNotCount() {
        assertFalse(LeaverWin.counts(1, 60, 0, 0));
        assertFalse(LeaverWin.counts(59, 60, 0, 0));
        assertTrue(LeaverWin.counts(60, 60, 0, 0));
    }

    @Test void killTargetGamesNeedHalfTheTargetFromNonRepeatKills() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        // Eleven kills, all on the same friend: only three count.
        assertEquals(3, LeaverWin.validKills(Map.of(a, 11), 3));
        assertFalse(LeaverWin.counts(300, 60, 15, LeaverWin.validKills(Map.of(a, 11), 3)));
        // Three victims, three each: nine valid kills clears half of fifteen.
        assertTrue(LeaverWin.counts(300, 60, 15, LeaverWin.validKills(Map.of(b, 3, c, 3, d, 4), 3)));
        assertTrue(LeaverWin.counts(300, 60, 0, 0), "games without a kill target need only the time");
    }
}
