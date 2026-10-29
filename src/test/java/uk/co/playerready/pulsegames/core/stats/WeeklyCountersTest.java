package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyCountersTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    private final LocalDate monday = LocalDate.of(2026, 10, 26), sunday = LocalDate.of(2026, 11, 1), nextMonday = LocalDate.of(2026, 11, 2);

    @Test void ranksPerGameAndAcrossTheNetwork() {
        var w = new WeeklyCounters(new YamlConfiguration());
        w.add(monday, a, "Ann", "spleef", "wins", 3);
        w.add(monday, a, "Ann", "koth", "wins", 1);
        w.add(monday, b, "Bo", "spleef", "wins", 2);
        w.add(monday, b, "Bo", "spleef", "kills", 9);
        assertEquals("Ann", w.top(monday, "spleef", "wins", 3).getFirst().name());
        assertEquals(4, w.top(monday, null, "wins", 3).getFirst().value());
        assertEquals("Bo", w.top(monday, null, "kills", 3).getFirst().name());
    }

    @Test void clearsWhenTheIsoWeekRollsOver() {
        var w = new WeeklyCounters(new YamlConfiguration());
        w.add(monday, a, "Ann", "spleef", "wins", 3);
        assertFalse(w.top(sunday, "spleef", "wins", 3).isEmpty());
        assertTrue(w.top(nextMonday, "spleef", "wins", 3).isEmpty());
        assertEquals("2026-W45", w.week());
    }

    @Test void tokensAreNetworkOnlyAndAllTimeSharesTheLayout() {
        var y = new YamlConfiguration();
        y.set(a + ".network.tokens", 500);
        y.set(a + ".spleef.wins", 4);
        y.set(a + ".lobby.best-ms.parkour", 30_000L);
        y.set(b + ".lobby.best-ms.parkour", 25_000L);
        y.set("_names." + a, "Ann");
        y.set("_names." + b, "Bo");
        var names = y.getConfigurationSection("_names");
        assertEquals(500, WeeklyCounters.rank(y, names, null, "tokens", 3).getFirst().value());
        assertEquals(4, WeeklyCounters.rank(y, names, null, "wins", 3).getFirst().value());
        var fastest = WeeklyCounters.fastest(y, names, "lobby", "parkour", 5);
        assertEquals("Bo", fastest.getFirst().name());
        assertEquals(2, fastest.size());
    }
}
