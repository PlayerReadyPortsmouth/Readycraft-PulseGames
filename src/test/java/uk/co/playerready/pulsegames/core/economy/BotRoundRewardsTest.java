package uk.co.playerready.pulsegames.core.economy;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotRoundRewardsTest {
    @TempDir Path directory;

    // Stop after the real ledger mutation but before Paper's Sound registry access.
    private static final class RewardDisplayed extends RuntimeException {}

    @Test void botRoundSuppressesMatchRewardsButPaysOrdinaryBonuses() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        GameInstance instance = mock(GameInstance.class);
        when(plugin.instances().byPlayer(player)).thenReturn(instance);
        when(instance.ranked()).thenReturn(false);
        doThrow(new RewardDisplayed()).when(player).sendActionBar(any(Component.class));
        EconomyService economy = new EconomyService(plugin);

        economy.addMatchTokens(player, 50, "victory");
        economy.addMatchTokens(player, 5, "kill");
        economy.addMatchTokens(player, 10, "game played");
        assertEquals(0, economy.tokens(player));
        assertThrows(RewardDisplayed.class, () -> economy.addTokens(player, 25, "playtime bonus"));
        assertEquals(25, economy.tokens(player));
        when(instance.ranked()).thenReturn(true);
        assertThrows(RewardDisplayed.class, () -> economy.addMatchTokens(player, 50, "victory"));
        assertEquals(75, economy.tokens(player));
    }

    @Test void delayedDailyBonusIsPaidOnceEvenIfPlayerEnteredBotRound() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        GameInstance instance = mock(GameInstance.class);
        when(plugin.instances().byPlayer(player)).thenReturn(instance);
        when(instance.ranked()).thenReturn(false);
        doThrow(new RewardDisplayed()).when(player).sendActionBar(any(Component.class));
        EconomyService economy = new EconomyService(plugin);
        economy.handleDailyBonus(player);
        ArgumentCaptor<Runnable> delayed = ArgumentCaptor.forClass(Runnable.class);
        verify(plugin.getServer().getScheduler()).runTaskLater(eq(plugin), delayed.capture(), eq(40L));
        assertThrows(RewardDisplayed.class, () -> delayed.getValue().run());
        assertEquals(100, economy.tokens(player));
        economy.handleDailyBonus(player);
        delayed.getValue().run();
        assertEquals(100, economy.tokens(player));
        verify(plugin.getServer().getScheduler(), times(1)).runTaskLater(eq(plugin), any(Runnable.class), eq(40L));
    }
}
