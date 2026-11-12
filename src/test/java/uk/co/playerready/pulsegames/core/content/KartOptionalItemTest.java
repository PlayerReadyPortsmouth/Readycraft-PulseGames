package uk.co.playerready.pulsegames.core.content;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartOptionalItemTest {
    public interface Items {
        boolean isContentReady(Player player);
        ItemStack createItem(String id,int count);
    }
    @Test void packReadinessAndMissingProviderLeaveVanillaFallbackAvailable() {
        var game=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        when(game.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(null);
        var bridge=new PulseContentBridge(game);var player=mock(Player.class);
        assertNull(bridge.optionalItem("readycraft:kart_magnet",player));
        var provider=mock(Plugin.class,withSettings().extraInterfaces(Items.class));
        when(game.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);
        assertNull(bridge.optionalItem("readycraft:kart_magnet",player));
        verify((Items)provider,never()).createItem(anyString(),anyInt());
    }
    @Test void returnsIndependentItemAndHandlesMissingAssetWithoutFailingMatch() {
        var game=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        var provider=mock(Plugin.class,withSettings().extraInterfaces(Items.class));
        when(game.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);
        var api=(Items)provider;var player=mock(Player.class);
        when(api.isContentReady(player)).thenReturn(true);
        var original=mock(ItemStack.class);var copy=mock(ItemStack.class);
        when(original.getType()).thenReturn(Material.PAPER);when(original.getAmount()).thenReturn(1);when(original.clone()).thenReturn(copy);
        when(api.createItem("readycraft:kart_magnet",1)).thenReturn(original);
        var bridge=new PulseContentBridge(game);
        assertSame(copy,bridge.optionalItem("readycraft:kart_magnet",player));
        when(api.createItem("readycraft:kart_magnet",1)).thenThrow(new IllegalArgumentException("missing"));
        assertNull(bridge.optionalItem("readycraft:kart_magnet",player));
        assertNull(bridge.optionalItem("readycraft:kart_magnet",player));
        verify(game.getLogger(),times(1)).warning(anyString());
        assertNull(bridge.optionalItem("bad id",player));
    }
}
