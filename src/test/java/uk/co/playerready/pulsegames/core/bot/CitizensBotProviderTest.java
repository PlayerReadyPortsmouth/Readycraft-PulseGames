package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CitizensBotProviderTest {
    @Test void absentDependencyDoesNotLoadCitizensClasses() {
        Plugin owner = owner(null);
        assertTrue(CitizensBotProvider.connect(owner).isEmpty());
    }
    @Test void disabledDependencyIsNotConnected() {
        Plugin citizens = mock(Plugin.class);
        assertTrue(CitizensBotProvider.connect(owner(citizens)).isEmpty());
    }
    @Test void incompatibleEnabledDependencyFailsClosed() {
        Plugin citizens = mock(Plugin.class);
        when(citizens.isEnabled()).thenReturn(true);
        assertTrue(CitizensBotProvider.connect(owner(citizens)).isEmpty());
    }
    @Test void namesCannotImpersonateHumansOrInjectFormatting() {
        assertEquals("Bot_Alex", CitizensBotProvider.botName("Alex"));
        assertEquals("Bot_abcdefghijkl", CitizensBotProvider.botName("abcdefghijklmnopqrst"));
        assertEquals("Bot_Alex", CitizensBotProvider.botName("Bot_Alex"));
        assertEquals("Bot_Player", CitizensBotProvider.botName("§!<>"));
    }
    private Plugin owner(Plugin citizens) {
        Plugin owner = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(owner.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("Citizens")).thenReturn(citizens);
        when(owner.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return owner;
    }
}
