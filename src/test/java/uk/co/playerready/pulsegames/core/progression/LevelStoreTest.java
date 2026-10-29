package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LevelStoreTest {
    private final UUID p = UUID.randomUUID();

    @Test void xpAccumulatesAndKeepsTheLastSixSources() {
        var store = new LevelStore(new YamlConfiguration());
        assertFalse(store.has(p));
        for (int i = 1; i <= 8; i++) store.add(p, 10, "round " + i);
        assertTrue(store.has(p));
        assertEquals(80, store.xp(p));
        assertEquals(LevelStore.RECENT, store.recent(p).size());
        assertEquals("+10 XP: round 8", store.recent(p).get(0));
    }

    @Test void firstOfTheDayIsOncePerGameAndDay() {
        var store = new LevelStore(new YamlConfiguration());
        LocalDate day = LocalDate.of(2026, 10, 29);
        assertTrue(store.claimFirstOfDay(p, "spleef", day));
        assertFalse(store.claimFirstOfDay(p, "spleef", day));
        assertTrue(store.claimFirstOfDay(p, "koth", day), "another game has its own bonus");
        assertTrue(store.claimFirstOfDay(p, "spleef", day.plusDays(1)));
    }

    @Test void migratedPlayerIsRecognisedEvenAtZeroXp() {
        var store = new LevelStore(new YamlConfiguration());
        store.set(p, XpRules.migratedXp(0));
        assertTrue(store.has(p));
    }
}
