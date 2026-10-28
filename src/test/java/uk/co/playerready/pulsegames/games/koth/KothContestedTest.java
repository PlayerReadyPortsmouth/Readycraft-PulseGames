package uk.co.playerready.pulsegames.games.koth;

import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class KothContestedTest {
    private Player onHill() {
        Player p = mock(Player.class, RETURNS_DEEP_STUBS);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getLocation()).thenReturn(new Location(null, 2, 61, 2));
        return p;
    }

    private GameInstance game(List<Player> players) {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.mode()).thenReturn(GameMode.ffa("solo", "Solo", 2, 12));
        when(game.arena().settings()).thenReturn(new MemoryConfiguration());
        when(game.arena().region("hill")).thenReturn(new Cuboid(0, 60, 0, 5, 65, 5));
        when(game.alivePlayers()).thenReturn(players);
        players.forEach(p -> { when(game.isActive(p)).thenReturn(true); when(game.teamOf(p)).thenReturn(null); });
        when(game.topScores()).thenReturn(List.of());
        return game;
    }

    @Test void twoRivalsOnTheHillScoreNothing() {
        Player a = onHill(), b = onHill();
        GameInstance game = game(List.of(a, b));
        KothGame logic = new KothGame(game);
        logic.onStart();
        logic.onSecond(1);
        verify(game, never()).addScore(any(), anyInt());
    }

    @Test void aLoneHolderScoresOnePerSecond() {
        Player a = onHill();
        GameInstance game = game(List.of(a));
        KothGame logic = new KothGame(game);
        logic.onStart();
        logic.onSecond(1);
        verify(game).addScore(a, 1);
    }
}
