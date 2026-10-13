package uk.co.playerready.pulsegames.core.content;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WardrobeBridgeTest {
    public interface Api {
        int wardrobeApiVersion();
        void wardrobeContext(Plugin owner, Player player, String game, String phase);
        void clearWardrobeContext(Plugin owner, Player player);
        void wardrobeCelebration(Plugin owner, Player player, String category);
    }
    @Test void forwardsOwnerContextAndOneShotCategoriesWithoutPropApi() {
        var owner=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        var provider=mock(Plugin.class,withSettings().extraInterfaces(Api.class));var api=(Api)provider;
        when(owner.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);when(api.wardrobeApiVersion()).thenReturn(1);
        var player=mock(Player.class);var bridge=new PulseContentBridge(owner);
        bridge.wardrobeContext(player,"duels","playing");bridge.wardrobeContext(player,"duels","ended");
        bridge.wardrobeCelebration(player,"finish_event");bridge.wardrobeCelebration(player,"win_animation");
        bridge.wardrobeCelebration(player,"trail");bridge.clearWardrobeContext(player);
        verify(api).wardrobeContext(owner,player,"duels","playing");verify(api).wardrobeContext(owner,player,"duels","ended");
        verify(api).wardrobeCelebration(owner,player,"finish_event");verify(api).wardrobeCelebration(owner,player,"win_animation");
        verify(api,never()).wardrobeCelebration(owner,player,"trail");verify(api).clearWardrobeContext(owner,player);
    }
    @Test void missingOldDisabledAndThrowingProvidersNeverBreakLifecycle() {
        var owner=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var player=mock(Player.class);
        var bridge=new PulseContentBridge(owner);
        when(owner.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(null);
        assertDoesNotThrow(()->bridge.wardrobeContext(player,"prophunt","playing"));
        var old=mock(Plugin.class);when(old.isEnabled()).thenReturn(true);
        when(owner.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(old);
        assertDoesNotThrow(()->bridge.clearWardrobeContext(player));
        var provider=mock(Plugin.class,withSettings().extraInterfaces(Api.class));var api=(Api)provider;
        when(owner.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);when(api.wardrobeApiVersion()).thenReturn(2);
        bridge.wardrobeContext(player,"duels","playing");verify(api,never()).wardrobeContext(any(),any(),any(),any());
        when(api.wardrobeApiVersion()).thenReturn(1);doThrow(new IllegalStateException("unavailable")).when(api).clearWardrobeContext(owner,player);
        assertDoesNotThrow(()->bridge.clearWardrobeContext(player));
        when(provider.isEnabled()).thenReturn(false);bridge.wardrobeCelebration(player,"win_animation");
        verify(api,never()).wardrobeCelebration(any(),any(),any());
    }
}
