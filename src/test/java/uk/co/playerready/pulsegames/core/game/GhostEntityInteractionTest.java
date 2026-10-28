package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;

import static org.mockito.Mockito.*;

class GhostEntityInteractionTest {
    private void click(boolean ghost, GameState state) {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.state()).thenReturn(state);
        when(instance.isSpectator(player)).thenReturn(ghost);
        when(plugin.instances().byPlayer(player)).thenReturn(instance);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        new GameListener(plugin).onInteractEntity(event);
        if (ghost && state == GameState.RUNNING) verify(event).setCancelled(true);
        else verify(event, never()).setCancelled(true);
    }

    @Test void aGhostCannotRideTradeOrPokeEntities() { click(true, GameState.RUNNING); }

    @Test void aLivePlayerIsNotAffected() { click(false, GameState.RUNNING); }
}
