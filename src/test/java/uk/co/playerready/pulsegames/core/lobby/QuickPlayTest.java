package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class QuickPlayTest {
    private static QuickPlay.Open open(String game, int humans) { return new QuickPlay.Open(game, game + "-1", humans); }

    @Test void joinsTheBusiestQueue() {
        var pick = QuickPlay.pickQueue(List.of(open("spleef", 1), open("koth", 4), open("tntrun", 2)), "spleef", new Random(1));
        assertEquals("koth", pick.gameId());
    }

    @Test void tiesGoToTheFeaturedGame() {
        for (long seed = 0; seed < 20; seed++) {
            var pick = QuickPlay.pickQueue(List.of(open("spleef", 3), open("koth", 3), open("tntrun", 3)), "koth", new Random(seed));
            assertEquals("koth", pick.gameId());
        }
    }

    @Test void tiesWithoutFeaturedPickAmongTheTiedOnly() {
        for (long seed = 0; seed < 20; seed++) {
            var pick = QuickPlay.pickQueue(List.of(open("spleef", 3), open("koth", 3), open("tntrun", 1)), "parkour", new Random(seed));
            assertTrue(pick.gameId().equals("spleef") || pick.gameId().equals("koth"));
        }
    }

    @Test void noOpenQueueFallsBackToFeaturedThenRandomEligible() {
        assertNull(QuickPlay.pickQueue(List.of(), "koth", new Random(1)));
        assertEquals("koth", QuickPlay.pickGame(List.of("spleef", "koth"), "koth", new Random(1)));
        assertTrue(List.of("spleef", "koth").contains(QuickPlay.pickGame(List.of("spleef", "koth"), "parkour", new Random(1))));
        assertNull(QuickPlay.pickGame(List.of(), "koth", new Random(1)));
    }

    @Test void featuredGameIsDeterministicByDateAndIgnoresPoolOrder() {
        LocalDate day = LocalDate.of(2026, 10, 28);
        var pool = List.of("spleef", "koth", "tntrun");
        String first = QuickPlay.featured(day, pool, "");
        assertEquals(first, QuickPlay.featured(day, pool, null));
        assertEquals(first, QuickPlay.featured(day, List.of("tntrun", "spleef", "koth"), ""));
        assertNotEquals(first, QuickPlay.featured(day.plusDays(1), pool, ""));
        assertEquals(first, QuickPlay.featured(day.plusDays(3), pool, ""));
    }

    @Test void featuredOverrideWinsOnlyWhenPlayable() {
        LocalDate day = LocalDate.of(2026, 10, 28);
        var pool = List.of("spleef", "koth", "tntrun");
        assertEquals("tntrun", QuickPlay.featured(day, pool, " tntrun "));
        assertEquals(QuickPlay.featured(day, pool, ""), QuickPlay.featured(day, pool, "unknown"));
        assertNull(QuickPlay.featured(day, List.of(), "koth"));
    }
}
