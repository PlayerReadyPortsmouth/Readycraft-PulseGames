package uk.co.playerready.pulsegames.games.villagerdefense;
import org.junit.jupiter.api.Test;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.util.BoundingBox;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class VillagerSpawnTest {
 @Test void safeFloorRequiredAndUnloadedChunksNeverRead() {
  World w=mock(World.class);Block air=mock(Block.class),floor=mock(Block.class);
  when(w.getMinHeight()).thenReturn(-64);when(w.getMaxHeight()).thenReturn(320);
  var regions=List.of(new Cuboid(0,64,0,0,64,0));
  assertTrue(VSpawnSearch.choose(w,regions,new Random(1)).isEmpty());verify(w,never()).getBlockAt(anyInt(),anyInt(),anyInt());
  when(w.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(w.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);when(air.isPassable()).thenReturn(true);
  assertTrue(VSpawnSearch.choose(w,regions,new Random(1)).isEmpty());
  when(w.getBlockAt(0,63,0)).thenReturn(floor);when(floor.getBoundingBox()).thenReturn(new BoundingBox(0,63,0,1,64,1));
  assertTrue(VSpawnSearch.choose(w,regions,new Random(1)).isPresent());
  assertTrue(VSpawnSearch.choose(w,List.of(),new Random(1)).isEmpty());
 }
 @Test void failedSpawnRetainsPendingUntilBoundedFailureAndCleanupInvalidatesCallbacks() throws Exception {
  GameInstance game=mock(GameInstance.class);when(game.world()).thenReturn(mock(World.class));
  var logic=new VillagerDefenseGame(game);set(logic,"waveActive",true);set(logic,"pendingSpawns",1);set(logic,"mayor",mock(Villager.class));
  List<Runnable> retry=new ArrayList<>();doAnswer(i->{retry.add(i.getArgument(1));return null;}).when(game).runLater(anyLong(),any(Runnable.class));
  var spawn=VillagerDefenseGame.class.getDeclaredMethod("spawnQueued",int.class);spawn.setAccessible(true);spawn.invoke(logic,0);
  logic.onSecond(1);verify(game,never()).end(anyList(),anyString());
  for(int i=0;i<5;i++)retry.get(i).run();
  assertEquals(5,retry.size());verify(game).forceEnd();verify(game.world(),never()).spawnEntity(any(),any());
  logic.onCleanup();retry.get(0).run();assertEquals(5,retry.size());
 }
 private void set(Object target,String name,Object value)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
