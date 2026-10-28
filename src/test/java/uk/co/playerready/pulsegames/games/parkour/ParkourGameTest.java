package uk.co.playerready.pulsegames.games.parkour;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParkourGameTest {
    final World world = mock(World.class);
    final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
    final Player p1 = player(), p2 = player(), p3 = player();

    Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getLocation()).thenReturn(at(0));
        return p;
    }
    Location at(double x) { return new Location(world, x, 80, 0.5); }

    ParkourGame start(String mode, List<Player> players) {
        when(game.world()).thenReturn(world);
        when(game.mode().id()).thenReturn(mode);
        when(game.alivePlayers()).thenReturn(players);
        for (Player p : players) when(game.player(p.getUniqueId())).thenReturn(p);
        when(game.arena().settings().getInt("elim-interval", 45)).thenReturn(45);
        when(game.arena().settings().getInt("finish-grace", 30)).thenReturn(30);
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(
                List.of(new Cuboid(2, 79, 0, 2, 82, 0), new Cuboid(4, 79, 0, 4, 82, 0)));
        when(game.arena().region("finish")).thenReturn(new Cuboid(8, 79, 0, 8, 82, 0));
        ParkourGame parkour = new ParkourGame(game);
        parkour.onStart();
        return parkour;
    }

    @Test void warningWindowIsTenSecondsBeforeEachCut() {
        assertEquals(0, ParkourGame.warningSecondsLeft(34, 45));
        assertEquals(10, ParkourGame.warningSecondsLeft(35, 45));
        assertEquals(1, ParkourGame.warningSecondsLeft(44, 45));
        assertEquals(0, ParkourGame.warningSecondsLeft(45, 45));
        assertEquals(10, ParkourGame.warningSecondsLeft(80, 90));
        assertEquals(4, ParkourGame.warningSecondsLeft(1, 5), "short intervals warn for interval-1 seconds");
    }

    ParkourGame spread() {
        ParkourGame parkour = start("elimination", List.of(p1, p2, p3));
        parkour.onMove(p1, at(0), at(2.5));
        parkour.onMove(p1, at(0), at(4.5));
        parkour.onMove(p2, at(0), at(2.5));
        return parkour;
    }

    @Test void lastPlaceGetsWarningThenBecomesGhostNotEliminated() {
        ParkourGame parkour = spread();
        parkour.onSecond(40);
        verify(p3, atLeastOnce()).sendActionBar(any(net.kyori.adventure.text.Component.class));
        verify(p3, never()).setGameMode(any());
        parkour.onSecond(45);
        verify(p3).setGameMode(GameMode.SPECTATOR);
        verify(game, never()).eliminate(any());
        verify(game, never()).end(any(), anyString());
    }

    @Test void ghostScoresPerCheckpointButCannotWin() {
        ParkourGame parkour = spread();
        parkour.onSecond(45);
        parkour.onMove(p3, at(0), at(2.5));
        verify(game).addScore(p3, 1);
        parkour.onMove(p3, at(0), at(4.5));
        parkour.onMove(p3, at(0), at(8.5));
        verify(game, never()).finishParticipant(p3);
        verify(game, never()).end(any(), anyString());
    }

    @Test void lastRunnerStandingWinsAfterFinalCut() {
        ParkourGame parkour = start("elimination", List.of(p1, p2));
        parkour.onMove(p1, at(0), at(2.5));
        parkour.onSecond(45);
        verify(p2).setGameMode(GameMode.SPECTATOR);
        verify(game).end(List.of(p1), "last-runner");
    }

    @Test void tiedLastPlaceAndSprintModeCutNobody() {
        ParkourGame tied = start("elimination", List.of(p1, p2));
        tied.onSecond(45);
        verify(p1, never()).setGameMode(any());
        verify(p2, never()).setGameMode(any());
        ParkourGame sprint = start("sprint", List.of(p1, p2, p3));
        sprint.onSecond(45);
        verify(p3, never()).setGameMode(any());
    }

    @Test void cutsPauseOnceSomeoneHasFinished() {
        ParkourGame parkour = spread();
        parkour.onMove(p1, at(0), at(8.5));
        parkour.onSecond(45);
        verify(p3, never()).setGameMode(any());
    }
}
