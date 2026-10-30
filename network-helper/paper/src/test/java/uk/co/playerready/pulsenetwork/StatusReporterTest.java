package uk.co.playerready.pulsenetwork;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusReporterTest {
    /** Stands in for PulseGames: only the reflective hook the bridge already uses. */
    static abstract class GamesPlugin implements Plugin {
        public boolean networkPlayerBusy(Player p) { return p.getName().equals("busy"); }
        public int networkPlayerLevel(Player p) { return p.getName().equals("busy") ? 12 : 0; }
    }
    Plugin games() {
        Plugin games = mock(GamesPlugin.class, CALLS_REAL_METHODS); when(games.isEnabled()).thenReturn(true); return games;
    }
    Player player(String name, String key) {
        Player p = mock(Player.class); when(p.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes())); when(p.getName()).thenReturn(name);
        when(p.getMetadata(anyString())).thenReturn(List.of());
        if (key != null) when(p.getMetadata(key)).thenReturn(List.of(new FixedMetadataValue(mock(Plugin.class), true)));
        return p;
    }
    @Test void flagsAfkHiddenAndMatchAndExcludesHiddenAndNpcsFromOnline() {
        
        Player afk = player("afk", "afk"), ghost = player("ghost", "vanished"), busy = player("busy", null), npc = player("npc", null), plain = player("plain", null);
        when(npc.hasMetadata("NPC")).thenReturn(true);
        var reporter = new StatusReporter(List.of("afk", "AFK"));
        var status = reporter.collect(19.5, 8.0, List.of(afk, ghost, busy, npc, plain), games());
        assertEquals(3, status.online()); // afk, busy, plain; ghost hidden; npc ignored
        assertEquals(ServerStatus.AFK, status.flags().get(afk.getUniqueId()));
        assertEquals(ServerStatus.HIDDEN, status.flags().get(ghost.getUniqueId()));
        assertEquals(ServerStatus.MATCH, status.flags().get(busy.getUniqueId()));
        assertFalse(status.flags().containsKey(plain.getUniqueId()));
        assertEquals(0, reporter.collect(20, 1, List.of(plain), null).flags().size());
    }
    @Test void levelsComeFromTheSoftPulseGamesHookAndAreOmittedWhenAbsent() {
        Player busy = player("busy", null), plain = player("plain", null);
        var reporter = new StatusReporter(List.of());
        var status = reporter.collect(20, 1, List.of(busy, plain), games());
        assertEquals(Map.of(busy.getUniqueId(), 12), status.levels());
        assertTrue(reporter.collect(20, 1, List.of(busy), null).levels().isEmpty());
        Plugin old = mock(Plugin.class); when(old.isEnabled()).thenReturn(true);
        assertTrue(new StatusReporter(List.of()).collect(20, 1, List.of(busy), old).levels().isEmpty());
    }
}
