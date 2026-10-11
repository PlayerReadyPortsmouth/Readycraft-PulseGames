package uk.co.playerready.pulsegames.games;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.games.duels.DuelsGame;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DuelsTeamsTest {
    @Test void firstDeathDoesNotEndTeamRoundAndWholeWinningTeamWins() {
        var game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.mode()).thenReturn(GameMode.teams("classic-doubles", "2v2", 4, 4, 2));
        when(game.arena().settings().getInt("rounds-to-win", 2)).thenReturn(1);
        List<Player> players = new ArrayList<>();
        for(int i=0;i<4;i++){var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());players.add(p);}
        GameTeam red=new GameTeam(0),blue=new GameTeam(1);
        for(int i=0;i<4;i++){var team=i<2?red:blue;team.add(players.get(i));when(game.teamOf(players.get(i))).thenReturn(team);}
        when(game.players()).thenReturn(players); when(game.alivePlayers()).thenReturn(players);
        var logic = new DuelsGame(game);
        logic.onDeath(players.get(0), players.get(2));
        verify(game, never()).end(anyList(), anyString());
        assertFalse(logic.acceptsDeath(players.get(0)));
        assertTrue(logic.acceptsDeath(players.get(1)));
        logic.onDeath(players.get(1), players.get(3));
        verify(game).end(List.of(players.get(2),players.get(3)), "rounds");
        assertFalse(logic.acceptsDeath(players.get(2)));
    }
}
