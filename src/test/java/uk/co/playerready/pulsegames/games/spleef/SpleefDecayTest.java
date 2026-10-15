package uk.co.playerready.pulsegames.games.spleef;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SpleefDecayTest {
 private SpleefGame logic(List<Block> blocks) throws Exception {
  var g=mock(GameInstance.class);when(g.mode()).thenReturn(GameMode.ffa("decay","Decay",2,8));
  var logic=new SpleefGame(g,b->0);var f=SpleefGame.class.getDeclaredField("floorBlocks");f.setAccessible(true);f.set(logic,blocks);return logic;
 }
 @Test void decaySkipsOldHolesAndRetiresRemovedTiles() throws Exception {
  List<Block> pool=new ArrayList<>();List<Block> solid=new ArrayList<>();
  for(int i=0;i<15;i++){var b=mock(Block.class);when(b.isEmpty()).thenReturn(true);pool.add(b);}
  for(int i=0;i<5;i++){var b=mock(Block.class);when(b.isEmpty()).thenReturn(false);pool.add(b);solid.add(b);}
  var logic=logic(pool);logic.onSecond(30);logic.onSecond(31);
  for(var b:solid)verify(b,times(1)).setType(Material.AIR);
 }
 @Test void spentFloorHasBoundedScanCost() throws Exception {
  var air=mock(Block.class);when(air.isEmpty()).thenReturn(true);
  var pool=new ArrayList<>(Collections.nCopies(1000,air));logic(pool).onSecond(30);
  assertEquals(960,pool.size());verify(air,times(40)).isEmpty();verify(air,never()).setType(any());
 }
}
