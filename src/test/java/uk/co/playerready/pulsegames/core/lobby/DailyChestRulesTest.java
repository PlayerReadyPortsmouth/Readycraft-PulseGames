package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DailyChestRulesTest {
    private final LocalDate today = LocalDate.of(2026, 10, 29);

    @Test void onceADayThenAgainTomorrow() {
        assertTrue(DailyChestRules.canClaim(null, today));
        assertFalse(DailyChestRules.canClaim(today, today));
        assertTrue(DailyChestRules.canClaim(today.minusDays(1), today));
    }

    @Test void waitRunsToLocalMidnight() {
        var wait = DailyChestRules.untilNext(LocalDateTime.of(2026, 10, 29, 18, 48));
        assertEquals("5h 12m", DailyChestRules.describe(wait));
        assertEquals("1m", DailyChestRules.describe(DailyChestRules.untilNext(LocalDateTime.of(2026, 10, 29, 23, 59, 30))));
    }

    @Test void extraStaysInRangeEvenIfConfigIsBackwards() {
        var r = new Random(1);
        for (int i = 0; i < 200; i++) {
            int v = DailyChestRules.extra(r, 25, 5);
            assertTrue(v >= 5 && v <= 25);
        }
    }
}
