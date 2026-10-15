package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckpointRaceGameTest {
    final World world = mock(World.class);
    final GameInstance game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
    final Player player = mock(Player.class);
    class Race extends CheckpointRaceGame {
        final boolean swept;
        Race(boolean swept) { super(CheckpointRaceGameTest.this.game); this.swept = swept; }
        protected int laps() { return 1; }
        protected boolean sweptCheckpoints() { return swept; }
    }
    Location at(double x) { return new Location(world, x, 80, 0.5); }
    Cuboid gate(int x) { return new Cuboid(x, 79, 0, x, 82, 0); }
    Race race(boolean swept, List<Cuboid> checkpoints) {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(at(0));
        when(player.isInsideVehicle()).thenReturn(true);
        when(game.world()).thenReturn(world);
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        org.bukkit.block.Block air=mock(org.bukkit.block.Block.class);when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);
        when(game.alivePlayers()).thenReturn(List.of(player));
        when(game.spawnFor(player)).thenReturn(at(0));
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(checkpoints);
        when(game.arena().region("finish")).thenReturn(gate(8));
        when(game.arena().settingLocation(anyString(), eq(world))).thenReturn(null);
        Race race = new Race(swept); race.onStart(); return race;
    }
    @Test void crossesSeveralOrderedGatesAndFinishWithinOneMovement() {
        Race race = race(true, List.of(gate(2), gate(5)));
        race.onMove(player, at(0), at(10));
        verify(game).end(List.of(player), "finish");
    }
    @Test void cannotCreditGatesCrossedInWrongOrder() {
        Race race = race(true, List.of(gate(5), gate(2)));
        race.onMove(player, at(0), at(10));
        assertEquals(1, race.progressValue(player.getUniqueId()));
        verify(game, never()).end(any(), anyString());
    }
    @Test void finishBeforeFinalCheckpointDoesNotFinish() {
        Race race = race(true, List.of(gate(9)));
        race.onMove(player, at(0), at(11));
        verify(game, never()).end(any(), anyString());
    }
    @Test void ordinaryRaceKeepsEndpointOnlySemantics() {
        Race race = race(false, List.of(gate(2)));
        race.onMove(player, at(0), at(10));
        assertEquals(0, race.progressValue(player.getUniqueId()));
    }
    @Test void reviewedResetPreservesYawAndCannotBeMutatedByCaller() {
        Race race = race(true, List.of(gate(2)));
        Location reviewed = new Location(world, 3, 81, 0.5, 90, 10);
        when(game.arena().settingLocation("checkpoint-spawn1", world)).thenReturn(reviewed);
        org.bukkit.block.Block floor=mock(org.bukkit.block.Block.class);
        when(floor.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(3,80,0,4,81,1));
        when(world.getBlockAt(3,80,0)).thenReturn(floor);
        race.onMove(player, at(0), at(4));
        assertEquals(reviewed, race.resetLocation(player));
        race.resetLocation(player).setYaw(0);
        assertEquals(90, race.resetLocation(player).getYaw());
    }
    @Test void missingReviewedResetFallsBackToSupportedPosition() {
        Race race = race(true, List.of(gate(2)));
        race.onMove(player, at(0), at(4));
        assertEquals(at(4), race.resetLocation(player));
    }
    @Test void ordinaryCheckpointAlsoUsesReviewedResetAndClearsMomentum() {
        Race race=race(false,List.of(gate(2)));
        Location reviewed=at(3);
        when(game.arena().settingLocation("checkpoint-spawn1",world)).thenReturn(reviewed);
        org.bukkit.block.Block floor=mock(org.bukkit.block.Block.class);
        when(floor.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(3,79,0,4,80,1));
        when(world.getBlockAt(3,79,0)).thenReturn(floor);
        race.onMove(player,at(0),at(2.5));assertEquals(reviewed,race.resetLocation(player));
        when(player.teleport(reviewed)).thenReturn(true);race.sendBack(player);
        verify(player,times(2)).setVelocity(new org.bukkit.util.Vector());
    }
    @Test void airborneCrossingWithoutSafeAuthoredPointRetainsPreviousReset() {
        Race race=race(true,List.of(gate(2)));when(player.isInsideVehicle()).thenReturn(false);when(player.isGliding()).thenReturn(true);
        when(game.arena().settingLocation("checkpoint-spawn1",world)).thenReturn(at(3));
        race.onMove(player,at(0),at(4));assertEquals(at(0),race.resetLocation(player));
    }
    @Test void intersectionRejectsDifferentWorldNonFiniteAndOutsideAxis() {
        assertTrue(Double.isNaN(RaceGateCrossing.entry(gate(2), at(0), new Location(mock(World.class), 4,80,.5))));
        assertTrue(Double.isNaN(RaceGateCrossing.entry(gate(2), at(Double.NaN), at(4))));
        assertTrue(Double.isNaN(RaceGateCrossing.entry(gate(2), new Location(world,0,90,.5),new Location(world,4,90,.5))));
        assertEquals(.5, RaceGateCrossing.entry(gate(2), at(0), at(4)));
    }
    @Test void upperBoundaryIsExcludedAndStationaryInsideAccepted() {
        assertTrue(Double.isNaN(RaceGateCrossing.entry(gate(2), at(3), at(3))));
        assertEquals(0, RaceGateCrossing.entry(gate(2), at(2.5), at(2.5)));
    }
}
