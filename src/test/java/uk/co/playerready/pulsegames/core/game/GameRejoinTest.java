package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameRejoinTest {
    private final PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
    private final InstanceManager manager = new InstanceManager(plugin);
    private final GameInstance game = mock(GameInstance.class);
    private final Player previous = mock(Player.class), reconnected = mock(Player.class);
    GameRejoinTest() {
        UUID id = UUID.randomUUID();
        when(previous.getUniqueId()).thenReturn(id); when(reconnected.getUniqueId()).thenReturn(id);
        when(plugin.getConfig().getInt("rejoin.grace-seconds", 30)).thenReturn(30);
        when(game.isParticipant(previous)).thenReturn(true);
        manager.index(previous, game);
    }
    @Test void waitingSeatResumesOnlyOnceAndBlocksCountdownUntilClaimed() {
        when(game.state()).thenReturn(GameState.WAITING);
        when(game.resumeWaiting(reconnected)).thenReturn(true);
        manager.disconnect(previous);
        assertTrue(manager.hasReservedSeats(game));
        verify(game).suspendWaiting(previous); verify(game, never()).remove(previous, false);
        assertTrue(manager.rejoin(reconnected)); assertFalse(manager.rejoin(reconnected));
        assertFalse(manager.hasReservedSeats(game));
        verify(game, times(1)).resumeWaiting(reconnected);
    }
    @Test void expiryReleasesWaitingSeatAndCannotBeClaimed() {
        when(game.state()).thenReturn(GameState.COUNTDOWN);
        manager.disconnect(previous);
        var expiry = ArgumentCaptor.forClass(Runnable.class);
        verify(plugin.getServer().getScheduler()).runTaskLater(eq(plugin), expiry.capture(), eq(600L));
        expiry.getValue().run();
        verify(game).remove(previous, false);
        assertFalse(manager.hasReservedSeats(game)); assertFalse(manager.rejoin(reconnected));
    }
    @Test void runningDisconnectOnlyOffersSpectatorAndDoesNotReserveAnAliveSeat() {
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.returnSpectator(reconnected)).thenReturn(true);
        manager.disconnect(previous);
        verify(game).remove(previous, false);
        assertFalse(manager.hasReservedSeats(game));
        assertTrue(manager.rejoin(reconnected));
        verify(game).returnSpectator(reconnected); verify(game, never()).resumeWaiting(any());
    }
    @Test void resettingMatchDropsReservationWithoutRecreatingWorld() {
        when(game.state()).thenReturn(GameState.WAITING);
        manager.disconnect(previous);
        manager.remove(game);
        assertFalse(manager.rejoin(reconnected)); assertFalse(manager.hasReservedSeats(game));
    }
}
