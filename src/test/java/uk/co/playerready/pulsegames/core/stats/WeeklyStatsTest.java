package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyStatsTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
    private final LocalDate monday = LocalDate.of(2026, 10, 26);

    private WeeklyStats board() {
        var stats = new WeeklyStats(new YamlConfiguration());
        stats.rollover(monday);
        return stats;
    }

    @Test void ranksByWinsWithAPerGameColumn() {
        var w = board();
        for (int i = 0; i < 3; i++) w.recordWin(a, "Ann", "spleef");
        w.recordWin(a, "Ann", "koth");
        for (int i = 0; i < 2; i++) w.recordWin(b, "Ben", "koth");
        w.recordWin(c, "Cy", "tntrun");
        var top = w.top(null, 3);
        assertEquals(List.of("Ann", "Ben", "Cy"), top.stream().map(WeeklyStats.Entry::name).toList());
        assertEquals(4, top.get(0).wins());
        assertEquals("spleef", top.get(0).topGame());
        assertEquals(3, top.get(0).topGameWins());
        assertEquals("Ben", w.top("koth", 1).getFirst().name());
        assertEquals("Ben", w.leaders(List.of("koth", "spleef")).get("koth").name());
    }

    @Test void rolloverPaysTopThreeOnceThenClears() {
        var w = board();
        for (int i = 0; i < 4; i++) w.recordWin(a, "Ann", "spleef");
        for (int i = 0; i < 3; i++) w.recordWin(b, "Ben", "spleef");
        for (int i = 0; i < 2; i++) w.recordWin(c, "Cy", "spleef");
        w.recordWin(d, "Di", "spleef");
        assertNull(w.rollover(monday.plusDays(6)), "same ISO week: nothing happens");
        var winners = w.rollover(monday.plusDays(7));
        assertEquals(List.of(a, b, c), winners.stream().map(WeeklyStats.Entry::player).toList());
        assertTrue(w.top(null, 3).isEmpty());
        assertNull(w.rollover(monday.plusDays(8)), "the reward is paid only once");
    }

    @Test void firstEverRunOnlyStampsTheWeek() {
        var w = new WeeklyStats(new YamlConfiguration());
        assertEquals(List.of(), w.rollover(monday));
        assertEquals("2026-W44", WeeklyStats.weekKey(monday));
    }
}
