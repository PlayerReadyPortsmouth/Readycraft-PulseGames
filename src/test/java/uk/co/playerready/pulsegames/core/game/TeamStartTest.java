package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.team.TeamAllocator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamStartTest {
    @Test void singlePartyCannotStartWithoutOpposingTeam() throws Exception {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        Player first = player(), second = player();
        List<Player> players = List.of(first, second);
        when(plugin.parties().groupsOf(players)).thenReturn(new ArrayList<>(List.of(players)));
        Arena arena = mock(Arena.class);
        when(arena.spawnCount()).thenReturn(2);
        GameType type = mock(GameType.class);
        MiniGame logic = mock(MiniGame.class);
        when(logic.maxTeams()).thenReturn(2);
        when(type.createLogic(any())).thenReturn(logic);
        GameInstance game = spy(new GameInstance(plugin, type,
                GameMode.teams("teams", "Teams", 2, 4, 2), arena));
        var state = GameInstance.class.getDeclaredField("state");
        state.setAccessible(true);
        state.set(game, GameState.COUNTDOWN);
        doReturn(players).when(game).players();
        doReturn(2).when(game).humanCount();
        doNothing().when(game).broadcast(anyString());
        game.start();
        assertEquals(GameState.WAITING, game.state());
        assertTrue(game.teams().isEmpty());
        verify(logic, never()).onStart();
        verify(first, never()).teleport(any(org.bukkit.Location.class));
        verify(game).broadcast(contains("opposing team"));
    }

    @Test void humanAndIndependentBotCanOccupyOpposingTeams() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        Player human = player(), bot = player();
        List<Player> players = List.of(human, bot);
        when(plugin.parties().groupsOf(players)).thenReturn(new ArrayList<>(List.of(List.of(human), List.of(bot))));
        var teams = TeamAllocator.allocate(players, 2, 2, plugin.parties());
        assertEquals(2, teams.stream().filter(t -> !t.members().isEmpty()).count());
        assertNotEquals(teams.stream().filter(t -> t.contains(human)).findFirst(),
                teams.stream().filter(t -> t.contains(bot)).findFirst());
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
