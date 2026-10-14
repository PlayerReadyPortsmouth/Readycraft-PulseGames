package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.EconomyService;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DuelKitPersistenceTest {
    @TempDir Path directory;
    @Test void playersAndProfilesRetainIndependentChoicesAcrossReload() {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        Player first = player(), second = player();
        EconomyService economy = new EconomyService(plugin);
        economy.duelKit(first, "modern.classic", "op");
        economy.duelKit(first, "classic.classic", "warrior");
        economy.duelKit(first, "modern.crystal", "duel-crystal");
        economy.duelKit(second, "modern.classic", "warrior");
        economy.flush();
        EconomyService reloaded = new EconomyService(plugin);
        assertEquals("op", reloaded.duelKit(first, "modern.classic"));
        assertEquals("warrior", reloaded.duelKit(first, "classic.classic"));
        assertEquals("duel-crystal", reloaded.duelKit(first, "modern.crystal"));
        assertEquals("warrior", reloaded.duelKit(second, "modern.classic"));
        verify(first, never()).getInventory();
        verify(second, never()).getInventory();
    }
    private Player player() {
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); return player;
    }
}
