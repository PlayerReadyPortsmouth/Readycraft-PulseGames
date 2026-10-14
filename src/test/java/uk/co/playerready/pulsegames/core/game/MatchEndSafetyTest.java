package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchEndSafetyTest {
    @Test void deadTeammatesReceiveTeamWinButReturnedObserversCannotWin() {
        Player alive = mock(Player.class), dead = mock(Player.class), observer = mock(Player.class), rival = mock(Player.class);
        GameTeam winning = mock(GameTeam.class), losing = mock(GameTeam.class);
        assertEquals(List.of(alive, dead), GameInstance.winningMembers(List.of(alive, dead, rival), List.of(alive, observer),
                p -> p == rival ? losing : winning));
        assertEquals(List.of(), GameInstance.winningMembers(List.of(alive, dead, rival), List.of(observer), p -> winning));
    }
    @Test void endingTwiceCannotRunRewardOrCompletionPipelineAgain() throws Exception {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var type = mock(GameType.class); var logic = mock(MiniGame.class); var arena = mock(Arena.class);
        when(type.createLogic(any())).thenReturn(logic);
        var game = new GameInstance(plugin, type, mock(GameMode.class), arena);
        var field = GameInstance.class.getDeclaredField("state"); field.setAccessible(true); field.set(game, GameState.RUNNING);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            game.end(List.of(), "forced");
            game.end(List.of(), "time");
        }
        assertEquals(GameState.ENDING, game.state()); assertFalse(game.ranked());
        verify(logic, times(1)).onEnd(List.of());
        verify(plugin.bots(), times(1)).onRoundEnd(game, List.of(), "forced");
        verify(plugin.economy(), never()).addMatchTokens(any(), anyInt(), anyString());
    }
    @Test void returnedSpectatorCannotEarnWinLossOrParticipationAgain() throws Exception {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var type = mock(GameType.class); var logic = mock(MiniGame.class);
        when(type.createLogic(any())).thenReturn(logic);
        var game = new GameInstance(plugin, type, mock(GameMode.class), mock(Arena.class));
        Player observer = mock(Player.class); var id = java.util.UUID.randomUUID();
        when(observer.getUniqueId()).thenReturn(id); when(observer.isOnline()).thenReturn(true);
        when(plugin.getServer().getPlayer(id)).thenReturn(observer);
        var state = GameInstance.class.getDeclaredField("state"); state.setAccessible(true); state.set(game, GameState.RUNNING);
        var spectators = GameInstance.class.getDeclaredField("spectators"); spectators.setAccessible(true);
        ((java.util.Set<java.util.UUID>) spectators.get(game)).add(id);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            game.end(List.of(observer), "time");
        }
        verify(plugin.stats(), never()).addWin(any(), any());
        verify(plugin.stats(), never()).addLoss(any(), any());
        verify(plugin.economy(), never()).addMatchTokens(any(), anyInt(), anyString());
    }

    @Test void manualStartCannotConsumeAReservedSeat() throws Exception {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var type = mock(GameType.class); when(type.createLogic(any())).thenReturn(mock(MiniGame.class));
        var game = new GameInstance(plugin, type, mock(GameMode.class), mock(Arena.class));
        var field = GameInstance.class.getDeclaredField("state"); field.setAccessible(true); field.set(game, GameState.WAITING);
        when(plugin.instances().hasReservedSeats(game)).thenReturn(true);
        game.start();
        assertEquals(GameState.WAITING, game.state());
        verify(game.logic(), never()).onStart();
    }
}
