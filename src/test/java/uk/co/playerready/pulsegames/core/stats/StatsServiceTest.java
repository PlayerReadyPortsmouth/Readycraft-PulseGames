package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.playerready.pulsegames.PulseGamesPlugin;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatsServiceTest {
    @TempDir File dir;

    StatsService service() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(dir);
        return new StatsService(plugin);
    }

    Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return p;
    }

    @Test void personalBestOnlyImprovesAndIsPerMap() {
        StatsService stats = service();
        Player p = player();
        assertEquals(-1, stats.bestTime(p, "parkour", "map.one"));
        assertTrue(stats.submitBestTime(p, "parkour", "map.one", 62_000));
        assertFalse(stats.submitBestTime(p, "parkour", "map.one", 62_000));
        assertFalse(stats.submitBestTime(p, "parkour", "map.one", 70_000));
        assertTrue(stats.submitBestTime(p, "parkour", "map.one", 58_500));
        assertEquals(58_500, stats.bestTime(p, "parkour", "map.one"));
        assertEquals(-1, stats.bestTime(p, "parkour", "other"));
    }

    @Test void npcsNeverSaveAndSurviveReload() {
        StatsService stats = service();
        Player npc = player();
        when(npc.hasMetadata("NPC")).thenReturn(true);
        assertFalse(stats.submitBestTime(npc, "dropper", "m", 1000));
        Player p = player();
        stats.submitBestTime(p, "dropper", "m", 41_000);
        stats.flush();
        assertEquals(41_000, service().bestTime(p, "dropper", "m"));
        assertEquals(0, stats.get(p, "dropper", "wins"), "existing counters are unaffected");
    }
}
