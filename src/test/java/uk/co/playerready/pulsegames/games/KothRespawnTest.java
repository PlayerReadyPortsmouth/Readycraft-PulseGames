package uk.co.playerready.pulsegames.games;

import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.games.koth.KothGame;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class KothRespawnTest {
    @Test void inactiveHillOccupantCannotEarnSoloOrTeamPoints() {
        for (boolean teamMode : List.of(false, true)) {
            GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
            when(game.mode()).thenReturn(teamMode ? GameMode.teams("teams", "Teams", 8, 16, 4)
                    : GameMode.ffa("solo", "Solo", 2, 12));
            when(game.arena().settings()).thenReturn(new MemoryConfiguration());
            when(game.arena().region("hill")).thenReturn(new Cuboid(0,60,0,5,65,5));
            Player respawning = mock(Player.class);
            when(respawning.getUniqueId()).thenReturn(UUID.randomUUID());
            when(respawning.getLocation()).thenReturn(new Location(null, 2, 61, 2));
            when(game.alivePlayers()).thenReturn(List.of(respawning));
            when(game.isActive(respawning)).thenReturn(false);
            when(game.topScores()).thenReturn(List.of());
            GameTeam team = new GameTeam(0);
            team.add(respawning);
            when(game.teams()).thenReturn(List.of(team));
            when(game.teamOf(respawning)).thenReturn(teamMode ? team : null);
            KothGame logic = new KothGame(game);
            logic.onStart();
            logic.onSecond(1);
            logic.onTimeUp();
            verify(game, never()).addScore(any(), anyInt());
            verify(game).end(List.of(), "time");
        }
    }
}
