package uk.co.playerready.pulsegames.games.deathrun;
import org.junit.jupiter.api.Test;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DeathRunReliabilityTest {
 @Test void pairsMustMatchNotJustHaveSameCount() {
  GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var logic=new DeathRunGame(game);var box=new Cuboid(0,0,0,0,0,0);
  when(game.arena().regions()).thenReturn(Map.of("trap1",box,"trigger2",box));assertNotNull(logic.startProblem(List.of()));
  when(game.arena().regions()).thenReturn(Map.of("trap1",box,"trigger1",box));assertNull(logic.startProblem(List.of()));
 }
 @Test void activeTrapCannotRefireAndCleanupRestoresOnce() throws Exception {
  GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);World world=mock(World.class);when(game.world()).thenReturn(world);
  var logic=new DeathRunGame(game);var f=DeathRunGame.class.getDeclaredField("traps");f.setAccessible(true);f.set(logic,List.of(new Cuboid(0,64,0,0,64,0)));
  Block block=mock(Block.class);BlockData data=mock(BlockData.class);Location loc=new Location(world,0,64,0);
  when(world.getBlockAt(0,64,0)).thenReturn(block);when(world.getBlockAt(any(Location.class))).thenReturn(block);
  when(block.getLocation()).thenReturn(loc);when(block.getType()).thenReturn(Material.STONE);when(block.getBlockData()).thenReturn(data);
  when(game.arena().settings().getInt("trap-cooldown",5)).thenReturn(-10);
  var fire=DeathRunGame.class.getDeclaredMethod("fireTrap",int.class,Player.class);fire.setAccessible(true);
  List<Runnable> callbacks=new ArrayList<>();doAnswer(i->{callbacks.add(i.getArgument(1));return null;}).when(game).runLater(anyLong(),any(Runnable.class));
  fire.invoke(logic,0,mock(Player.class));fire.invoke(logic,0,mock(Player.class));assertEquals(1,callbacks.size());
  f=DeathRunGame.class.getDeclaredField("trapCooldown");f.setAccessible(true);assertTrue(((Map<Integer,Long>)f.get(logic)).get(0)>System.currentTimeMillis());
  logic.onCleanup();callbacks.get(0).run();verify(block,times(1)).setBlockData(data);
 }
}
