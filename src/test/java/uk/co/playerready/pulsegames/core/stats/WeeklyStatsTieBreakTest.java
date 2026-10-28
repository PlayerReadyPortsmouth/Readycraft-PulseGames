package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeeklyStatsTieBreakTest {
    @Test void aTieGoesToWhoeverGotThereFirstNotTheEarlierAlphabet() {
        WeeklyStats weekly = new WeeklyStats(new YamlConfiguration());
        UUID zed = UUID.randomUUID(), abe = UUID.randomUUID();
        weekly.recordWin(zed, "Zed", "spleef", 1_000);
        weekly.recordWin(abe, "Abe", "spleef", 2_000);
        List<WeeklyStats.Entry> top = weekly.top(null, 3);
        assertEquals("Zed", top.get(0).name());
        assertEquals("Abe", top.get(1).name());
    }

    @Test void moreWinsStillBeatsEarlier() {
        WeeklyStats weekly = new WeeklyStats(new YamlConfiguration());
        UUID early = UUID.randomUUID(), late = UUID.randomUUID();
        weekly.recordWin(early, "Early", "quake", 1_000);
        weekly.recordWin(late, "Late", "quake", 2_000);
        weekly.recordWin(late, "Late", "quake", 3_000);
        assertEquals("Late", weekly.top("quake", 1).get(0).name());
    }
}
