package uk.co.playerready.pulsegames.core.game;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DuelQueueRulesTest {
 @Test void classicAndModernNeverShareAnInstance() throws Exception {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var manager=new InstanceManager(plugin);var type=mock(GameType.class);
  when(type.id()).thenReturn("duels");var classic=GameMode.ffa("legacy-classic","Classic",2,2);var modern=GameMode.ffa("modern-classic","Modern",2,2);
  var existing=mock(GameInstance.class);when(existing.type()).thenReturn(type);when(existing.mode()).thenReturn(classic);when(existing.joinable(1)).thenReturn(true);
  var field=InstanceManager.class.getDeclaredField("instances");field.setAccessible(true);@SuppressWarnings("unchecked") var list=(List<GameInstance>)field.get(manager);list.add(existing);
  assertSame(existing,manager.findOrCreate(type,classic,1));assertNull(manager.findOrCreate(type,modern,1));
 }
 @Test void specialDisciplinesRequireMapOptInAndCrystalPlacementBounds() {
  var plugin=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);var manager=new InstanceManager(plugin);var type=mock(GameType.class);when(type.id()).thenReturn("duels");
  when(plugin.getConfig().getInt("instances.max-concurrent",20)).thenReturn(20);var settings=new MemoryConfiguration();
  var arena=new Arena("map","duels","Map","template",List.of(),2,8,"0,80,0",null,List.of("0,80,0","5,80,0"),Map.of(),settings);
  when(plugin.arenas().forGame(eq("duels"),anyString())).thenReturn(List.of(arena));
  assertNull(manager.findOrCreate(type,GameMode.ffa("modern-mace","Mace",2,2),1));
  assertNull(manager.findOrCreate(type,GameMode.ffa("modern-crystal","Crystal",2,2),1));
  settings.set("crystal-enabled",true);assertNull(manager.findOrCreate(type,GameMode.ffa("modern-crystal","Crystal",2,2),1));
 }
}
