package uk.co.playerready.pulsegames.games.elytra;
import org.junit.jupiter.api.Test;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ElytraReliabilityTest {
 @Test void fastFlightCrossesThinGateAndSafeAuthoredResetRelaunches() throws Exception {
  GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);World world=mock(World.class);Player player=mock(Player.class,RETURNS_DEEP_STUBS);
  when(game.world()).thenReturn(world);when(game.mode().id()).thenReturn("sprint");when(game.alivePlayers()).thenReturn(List.of(player));
  when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getLocation()).thenReturn(new Location(world,0,80,.5));
  when(game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of(new Cuboid(2,79,0,2,82,0)));
  when(game.arena().region("finish")).thenReturn(new Cuboid(10,79,0,10,82,0));
  Location launch=new Location(world,3.5,80,.5,90,0);when(game.arena().settingLocation("checkpoint-spawn1",world)).thenReturn(launch);
  when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
  Block air=mock(Block.class);when(air.isPassable()).thenReturn(true);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);
  var logic=new ElytraRacingGame(game);try(var items=mockConstruction(ItemStack.class)){logic.onStart();}
  logic.onMove(player,new Location(world,0,80,.5),new Location(world,5,80,.5));
  when(player.teleport(launch)).thenReturn(true);logic.onDeath(player,null);
  verify(player).teleport(launch);verify(player).setGliding(true);
  var velocity=org.mockito.ArgumentCaptor.forClass(org.bukkit.util.Vector.class);verify(player,times(3)).setVelocity(velocity.capture());
  assertEquals(.8,velocity.getValue().length(),.0001);assertTrue(velocity.getValue().getX()<0);
 }
}
