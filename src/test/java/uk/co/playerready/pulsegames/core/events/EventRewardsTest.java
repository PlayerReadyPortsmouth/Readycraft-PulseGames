package uk.co.playerready.pulsegames.core.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventRewardsTest {
    @Test void aLoneHumanIsNeverEligibleSoTokensAreZero() {
        assertFalse(EventRewards.eligible(1, 16));
        assertEquals(0, EventRewards.tokens(50, 2.0, EventRewards.eligible(1, 16)));
        assertFalse(EventRewards.eligible(0, 16));
    }

    @Test void twoOrMoreHumansArePaidAtTheMultiplier() {
        assertTrue(EventRewards.eligible(2, 2));
        assertEquals(100, EventRewards.tokens(50, 2.0, true));
        assertEquals(75, EventRewards.tokens(50, 1.5, true));
        assertEquals(20, EventRewards.tokens(10, 2.0, true));
    }

    @Test void theMultiplierNeverReducesAPayoutOrPaysNothingDue() {
        assertEquals(50, EventRewards.tokens(50, 0.2, true));
        assertEquals(0, EventRewards.tokens(0, 2.0, true));
        assertEquals(0, EventRewards.tokens(-5, 2.0, true));
        assertEquals(1, EventRewards.tokens(1, 1.0, true));
    }

    @Test void podiumPrizesByPlace() {
        assertEquals(50, EventRewards.prize(1, 50, 30, 20));
        assertEquals(30, EventRewards.prize(2, 50, 30, 20));
        assertEquals(20, EventRewards.prize(3, 50, 30, 20));
        assertEquals(0, EventRewards.prize(4, 50, 30, 20));
        assertEquals(0, EventRewards.prize(0, 50, 30, 20));
    }

    @Test void countdownAndOrdinalsAndPodium() {
        assertEquals("4:59", EventText.countdown(299));
        assertEquals("0:00", EventText.countdown(-3));
        assertEquals("11th", EventText.ordinal(11));
        assertEquals("22nd", EventText.ordinal(22));
        var places = new LinkedHashMap<String, Integer>();
        places.put("a", 1); places.put("b", 2); places.put("c", 2); places.put("d", 4);
        assertEquals(List.of("1st: A", "2nd: B, C"), EventText.podium(places, String::toUpperCase));
    }

    @Test void whenAndUntilReadLondonTime() {
        var zone = ZoneId.of("Europe/London");
        Instant now = Instant.parse("2026-07-01T12:00:00Z");
        assertEquals("19:00", EventText.when(now, Instant.parse("2026-07-01T18:00:00Z"), zone));
        assertEquals("Thu 01:00", EventText.when(now, Instant.parse("2026-07-02T00:00:00Z"), zone));
        assertEquals("in 42 min", EventText.until(now, now.plusSeconds(42 * 60)));
        assertEquals("in 1 h 5 min", EventText.until(now, now.plusSeconds(65 * 60)));
        assertEquals("in under a minute", EventText.until(now, now.plusSeconds(20)));
    }
}
