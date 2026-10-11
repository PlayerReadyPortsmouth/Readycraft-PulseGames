package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.games.elytra.ElytraRacingGame;

import static org.mockito.Mockito.*;

class GameDamageTest {
    @Test void cancelledAttackDoesNotClaimLaterEnvironmentalKill() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        GameInstance instance = mock(GameInstance.class);
        Player victim = mock(Player.class);
        Player attacker = mock(Player.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        MiniGame logic = mock(MiniGame.class);
        when(plugin.instances().byPlayer(victim)).thenReturn(instance);
        when(instance.logic()).thenReturn(logic);
        when(instance.state()).thenReturn(GameState.RUNNING);
        when(instance.isActive(any())).thenReturn(true);
        when(logic.pvp()).thenReturn(true);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(attacker);
        doAnswer(call -> { when(event.isCancelled()).thenReturn(true); return null; })
                .when(logic).onDamageByPlayer(victim, attacker, event);
        new GameListener(plugin).onDamage(event);
        verify(instance, never()).recordDamager(any(), any());
        verify(instance, never()).handleDeath(any(), any());
    }

    @Test void elytraLandingReachesResetHookWithoutDamage() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        GameInstance instance = mock(GameInstance.class);
        Player racer = mock(Player.class);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        Location checkpoint = new Location(null, 4, 90, 8);
        when(plugin.instances().byPlayer(racer)).thenReturn(instance);
        when(instance.logic()).thenReturn(new ElytraRacingGame(instance));
        when(instance.state()).thenReturn(GameState.RUNNING);
        when(instance.isActive(racer)).thenReturn(true);
        when(instance.spawnFor(racer)).thenReturn(checkpoint);
        when(event.getEntity()).thenReturn(racer);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        doAnswer(call -> { when(event.isCancelled()).thenReturn(true); return null; })
                .when(event).setCancelled(true);
        new GameListener(plugin).onDamage(event);
        verify(racer).teleport(checkpoint);
        verify(event).setCancelled(true);
        verify(instance, never()).handleDeath(any(), any());
    }
}
