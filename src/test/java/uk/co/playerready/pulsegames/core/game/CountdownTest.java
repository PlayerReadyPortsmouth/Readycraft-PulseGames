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
    @Test void startSignalWaitsForUnlockAndNeverRepeatsOrRunsAfterEnd() throws Exception {
        var game = spy(game()); var player = mock(org.bukkit.entity.Player.class);
        doReturn(java.util.List.of(player)).when(game).players();
        field(game,"state",GameState.RUNNING);field(game,"frozenUntil",System.currentTimeMillis()+1500);
        assertTrue(game.announceStartCountdown());
        verify(player).showTitle(any(net.kyori.adventure.title.Title.class));clearInvocations(player);
        assertTrue(game.announceStartCountdown());verify(player,never()).showTitle(any(net.kyori.adventure.title.Title.class));
        field(game,"frozenUntil",System.currentTimeMillis()-1);
        assertFalse(game.announceStartCountdown());
        var title=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.title.Title.class);
        verify(player).showTitle(title.capture());
        assertEquals("GO!", uk.co.playerready.pulsegames.core.util.Text.legacy(title.getValue().title()).replaceAll("§.",""));
        clearInvocations(player);assertFalse(game.announceStartCountdown());verify(player,never()).showTitle(any(net.kyori.adventure.title.Title.class));
        field(game,"state",GameState.ENDING);field(game,"lastStartAnnouncement",-1);
        assertFalse(game.announceStartCountdown());verify(player,never()).showTitle(any(net.kyori.adventure.title.Title.class));
    }
    @Test void phaseAndMatchClocksSurviveFullCustomScoreboard() throws Exception {
        var game=game();field(game,"state",GameState.RUNNING);
        when(game.logic().timeLimitSeconds()).thenReturn(300);
        when(game.logic().phaseCountdown()).thenReturn("Seekers released in: 12s");
        var rows=java.util.stream.IntStream.range(0,20).mapToObj(i->"Score "+i).toList();
        when(game.logic().sidebar(any())).thenReturn(rows);
        var lines=game.runningSidebar(mock(org.bukkit.entity.Player.class));
        assertTrue(lines.contains("<yellow>Seekers released in: 12s"));
        assertTrue(lines.contains("<gray>Match left: <white>5:00"));assertEquals(10,lines.size());
        when(game.logic().phaseCountdown()).thenReturn(null);
        assertFalse(game.runningSidebar(mock(org.bukkit.entity.Player.class)).stream().anyMatch(l->l.contains("Seekers")));
    }
    @Test void activePhaseDoesNotAnnounceGoAtMovementUnlock() throws Exception {
        var game=spy(game());var player=mock(org.bukkit.entity.Player.class);
        doReturn(java.util.List.of(player)).when(game).players();field(game,"state",GameState.RUNNING);
        when(game.logic().phaseCountdown()).thenReturn("Seekers released in: 20s");
        assertFalse(game.announceStartCountdown());
        var title=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.title.Title.class);
        verify(player).showTitle(title.capture());
        assertEquals("READY!",uk.co.playerready.pulsegames.core.util.Text.legacy(title.getValue().title()).replaceAll("§.",""));
    }
    @Test void botFooterAndPreparationStillFitFifteenLines() throws Exception {
        var game=game();field(game,"state",GameState.RUNNING);field(game,"frozenUntil",System.currentTimeMillis()+15000);
        var bots=GameInstance.class.getDeclaredField("botIds");bots.setAccessible(true);
        ((java.util.Set<java.util.UUID>)bots.get(game)).add(java.util.UUID.randomUUID());
        when(game.logic().phaseCountdown()).thenReturn("Choose kart: 15s");
        when(game.logic().sidebar(any())).thenReturn(java.util.stream.IntStream.range(0,20).mapToObj(i->"Score "+i).toList());
        var lines=game.runningSidebar(mock(org.bukkit.entity.Player.class));
        assertEquals(15,2+lines.size()+4);
        assertTrue(lines.getFirst().contains("Starting in"));assertTrue(lines.get(1).contains("Choose kart"));
    }
}
