package uk.co.playerready.pulsegames.core.kit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class KitSelectionTest {
 @Test void lockedMissingAndUnknownKitsCannotBeSelected() {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var player=mock(Player.class);
  var menu=new KitSelectionMenu(plugin);
  assertFalse(menu.select(player,"kit-tank"));
  when(plugin.economy().isUnlocked(player,"kit-tank")).thenReturn(true);
  when(plugin.kits().get("tank")).thenReturn(null);
  assertFalse(menu.select(player,"kit-tank"));assertFalse(menu.select(player,"kit-op"));
  verify(plugin.economy(),never()).equip(any(),any(),any());
 }
 @Test void ownedKitAndMapDefaultPersistWithoutRefillingInventory() {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var player=mock(Player.class);
  when(plugin.economy().isUnlocked(player,"kit-archer")).thenReturn(true);
  when(plugin.kits().get("archer")).thenReturn(mock(Kit.class));
  var menu=new KitSelectionMenu(plugin);assertTrue(menu.select(player,"kit-archer"));
  verify(plugin.economy()).equip(player,Cosmetic.Category.KIT,"kit-archer");
  assertTrue(menu.select(player,null));verify(plugin.economy()).equip(player,Cosmetic.Category.KIT,null);
  verify(player,never()).getInventory();verify(plugin.economy(),never()).spend(any(),anyInt());
 }
}
