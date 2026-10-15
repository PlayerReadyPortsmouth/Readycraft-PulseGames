package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CountdownTest {
    private GameInstance game() {
        var type = mock(GameType.class);
        when(type.createLogic(any())).thenReturn(mock(MiniGame.class));
        return new GameInstance(mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS), type,
                mock(GameMode.class), mock(Arena.class));
    }
    private void field(GameInstance game, String name, Object value) throws Exception {
        var f = GameInstance.class.getDeclaredField(name); f.setAccessible(true); f.set(game,value);
    }
    @Test void movementAndVisibleCountdownShareDeadline() throws Exception {
        var game = game(); field(game,"state",GameState.RUNNING);
        field(game,"frozenUntil",System.currentTimeMillis()+1500);
        assertEquals(2,game.startCountdownSeconds()); assertTrue(game.frozen());
        field(game,"frozenUntil",System.currentTimeMillis()-1);
        assertEquals(0,game.startCountdownSeconds()); assertFalse(game.frozen());
    }
    @Test void matchDeadlineUsesGameSpecificLimitAndNeverGoesNegative() throws Exception {
        var game = game(); when(game.logic().timeLimitSeconds()).thenReturn(300);
        field(game,"gameTime",42); assertEquals(258,game.remainingSeconds());
        field(game,"gameTime",301); assertEquals(0,game.remainingSeconds());
    }
    @Test void endedMatchCannotKeepAnActiveStartCountdown() throws Exception {
        var game = game(); field(game,"frozenUntil",System.currentTimeMillis()+15000);
        for (var state : new GameState[]{GameState.WAITING,GameState.ENDING,GameState.RESETTING}) {
            field(game,"state",state);
            assertEquals(0,game.startCountdownSeconds()); assertFalse(game.frozen());
        }
    }
}
