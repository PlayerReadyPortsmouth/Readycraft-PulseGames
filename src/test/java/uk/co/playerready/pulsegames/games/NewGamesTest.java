package uk.co.playerready.pulsegames.games;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.games.dodgeball.DodgeballGame;
import uk.co.playerready.pulsegames.games.dropper.DropperGame;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewGamesTest {
    @Test void unfinishedNewGamesNeverAwardTimeoutWins() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        new DropperGame(game).onTimeUp();
        verify(game).end(List.of(), "time-unfinished");
        new DodgeballGame(game).onTimeUp();
        verify(game).end(List.of(), "time-draw");
    }
    @Test void onlyTrackedThrowsCanEliminateAndCooldownBoundsThrows() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        World world = mock(World.class);
        Player shooter = mock(Player.class), victim = mock(Player.class);
        when(shooter.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.world()).thenReturn(world);
        when(shooter.getWorld()).thenReturn(world);
        when(victim.getWorld()).thenReturn(world);
        when(game.isActive(shooter)).thenReturn(true);
        when(game.isActive(victim)).thenReturn(true);
        when(shooter.getEyeLocation()).thenReturn(new Location(world,0,80,0));
        when(shooter.getLocation()).thenReturn(new Location(world,0,80,0));
        when(victim.getLocation()).thenReturn(new Location(world,1,80,0));
        when(game.teamOf(shooter)).thenReturn(null);
        Snowball ball = mock(Snowball.class);
        when(ball.getUniqueId()).thenReturn(UUID.randomUUID());
        when(ball.getShooter()).thenReturn(shooter);
        when(shooter.launchProjectile(eq(Snowball.class), any())).thenReturn(ball);
        DodgeballGame logic = new DodgeballGame(game);
        ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(ball);
        when(hit.getHitEntity()).thenReturn(victim);
        logic.onProjectileHit(hit);
        verify(game, never()).addScore(any(), anyInt());
        assertTrue(logic.throwBall(shooter));
        assertFalse(logic.throwBall(shooter));
        logic.onProjectileHit(hit);
        logic.onProjectileHit(hit);
        verify(game, times(1)).addScore(shooter, 1);
        verify(game, never()).eliminate(victim);
        verify(shooter, times(1)).launchProjectile(eq(Snowball.class), any());
    }
    @Test void frozenOrEliminatedPlayersCannotThrow() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class);
        when(game.state()).thenReturn(GameState.RUNNING);
        DodgeballGame logic = new DodgeballGame(game);
        assertFalse(logic.throwBall(player));
        when(game.isActive(player)).thenReturn(true);
        when(game.frozen()).thenReturn(true);
        assertFalse(logic.throwBall(player));
        verify(player, never()).launchProjectile(any(), any());
    }
    @Test void dropperRequiresOrderedWaterLandingsAndRetriesLatestLaunch() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        World world = mock(World.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(game.world()).thenReturn(world);
        when(game.isActive(player)).thenReturn(true);
        when(game.alivePlayers()).thenReturn(List.of(player));
        Location first = new Location(world, 0, 100, 0);
        Location second = new Location(world, 30, 100, 0);
        when(game.spawnFor(player)).thenReturn(first);
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of(
                new uk.co.playerready.pulsegames.core.util.Cuboid(0,60,0,2,61,2)));
        when(game.arena().region("finish")).thenReturn(
                new uk.co.playerready.pulsegames.core.util.Cuboid(30,60,0,32,61,2));
        when(game.arena().settingLocation("checkpoint-spawn1", world)).thenReturn(second);
        org.bukkit.block.Block water = mock(org.bukkit.block.Block.class);
        when(water.isLiquid()).thenReturn(true);
        when(water.getType()).thenReturn(org.bukkit.Material.WATER);
        when(world.getBlockAt(any(Location.class))).thenReturn(water);
        DropperGame logic = new DropperGame(game);
        logic.onStart();
        Location finish = new Location(world,31,60,1);
        logic.onMove(player, first, finish);
        verify(game, never()).end(anyList(), anyString());
        logic.onMove(player, first, new Location(world,1,60,1));
        verify(player).teleport(second);
        logic.onDeath(player, null);
        verify(player, times(2)).teleport(second);
        logic.onMove(player, second, finish);
        verify(game).end(List.of(player), "finished");
    }
    @Test void dodgeballEliminatesOnThirdEnemyHitOnly() {
        DodgeFixture fixture = new DodgeFixture();
        fixture.hit(null);
        fixture.hit(null);
        verify(fixture.game, never()).eliminate(fixture.victim);
        fixture.hit(null);
        verify(fixture.game).eliminate(fixture.victim);
    }
    @Test void friendlyHitsAndInactiveVictimsDoNotCount() {
        DodgeFixture fixture = new DodgeFixture();
        var team = new uk.co.playerready.pulsegames.core.team.GameTeam(0);
        when(fixture.game.teamOf(fixture.victim)).thenReturn(team);
        fixture.hit(team);
        verify(fixture.game, never()).addScore(any(), anyInt());
        when(fixture.game.isActive(fixture.victim)).thenReturn(false);
        fixture.hit(null);
        verify(fixture.game, never()).addScore(any(), anyInt());
    }
    @Test void cleanupRemovesOutstandingBallsAndInvalidatesTheirHits() {
        DodgeFixture fixture = new DodgeFixture();
        ProjectileHitEvent hit = fixture.launch(null);
        fixture.logic.onCleanup();
        verify(hit.getEntity()).remove();
        fixture.logic.onProjectileHit(hit);
        verify(fixture.game, never()).addScore(any(), anyInt());
    }
    @Test void dropperRejectsMissingNextLaunchBeforeStarting() {
        GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.arena().region("finish")).thenReturn(
                new uk.co.playerready.pulsegames.core.util.Cuboid(0,60,0,1,61,1));
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of(
                new uk.co.playerready.pulsegames.core.util.Cuboid(10,60,0,11,61,1)));
        when(game.arena().settingLocation(eq("checkpoint-spawn1"), any())).thenReturn(null);
        assertEquals("Missing checkpoint-spawn1", new DropperGame(game).startProblem(List.of()));
        verify(game, never()).end(anyList(), anyString());
    }
    private static final class DodgeFixture {
        final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        final World world = mock(World.class);
        final Player victim = mock(Player.class);
        final DodgeballGame logic = new DodgeballGame(game);
        DodgeFixture() {
            when(game.state()).thenReturn(GameState.RUNNING);
            when(game.world()).thenReturn(world);
            when(game.isActive(victim)).thenReturn(true);
            when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
            when(victim.getWorld()).thenReturn(world);
        }
        ProjectileHitEvent launch(uk.co.playerready.pulsegames.core.team.GameTeam team) {
            Player shooter = mock(Player.class);
            when(shooter.getUniqueId()).thenReturn(UUID.randomUUID());
            when(game.isActive(shooter)).thenReturn(true);
            when(game.teamOf(shooter)).thenReturn(team);
            when(shooter.getWorld()).thenReturn(world);
            when(shooter.getEyeLocation()).thenReturn(new Location(world,0,80,0));
        when(shooter.getLocation()).thenReturn(new Location(world,0,80,0));
        when(victim.getLocation()).thenReturn(new Location(world,1,80,0));
            Snowball ball = mock(Snowball.class);
            when(ball.getUniqueId()).thenReturn(UUID.randomUUID());
            when(ball.getShooter()).thenReturn(shooter);
            when(shooter.launchProjectile(eq(Snowball.class), any())).thenReturn(ball);
            assertTrue(logic.throwBall(shooter));
            ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
            when(hit.getEntity()).thenReturn(ball);
            when(hit.getHitEntity()).thenReturn(victim);
            return hit;
        }
        void hit(uk.co.playerready.pulsegames.core.team.GameTeam team) {
            logic.onProjectileHit(launch(team));
        }
    }
}
