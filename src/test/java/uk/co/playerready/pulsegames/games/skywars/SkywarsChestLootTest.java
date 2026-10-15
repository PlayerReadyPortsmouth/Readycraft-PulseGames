package uk.co.playerready.pulsegames.games.skywars;
import org.junit.jupiter.api.Test;
import org.bukkit.block.Chest;
import org.bukkit.inventory.*;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameMode;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SkywarsChestLootTest {
 @Test void eachGeneratedLootEntryOccupiesUniqueSlot() throws Exception {
  GameInstance game=mock(GameInstance.class);GameMode mode=mock(GameMode.class);when(game.mode()).thenReturn(mode);when(mode.rulesId()).thenReturn("solo");
  var logic=new SkywarsGame(game);Chest chest=mock(Chest.class);Inventory inventory=mock(Inventory.class);when(chest.getBlockInventory()).thenReturn(inventory);when(inventory.getSize()).thenReturn(27);
  var fill=SkywarsGame.class.getDeclaredMethod("fillChest",Chest.class);fill.setAccessible(true);
  try(var items=mockConstruction(ItemStack.class)) {
   for(int trial=0;trial<100;trial++) {
    clearInvocations(inventory);fill.invoke(logic,chest);
    var slots=org.mockito.ArgumentCaptor.forClass(Integer.class);verify(inventory,atLeast(4)).setItem(slots.capture(),any(ItemStack.class));
    assertTrue(slots.getAllValues().size()<=6);assertEquals(slots.getAllValues().size(),new HashSet<>(slots.getAllValues()).size());
   }
  }
 }
}
