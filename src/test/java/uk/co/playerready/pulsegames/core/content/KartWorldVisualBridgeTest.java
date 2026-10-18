package uk.co.playerready.pulsegames.core.content;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class KartWorldVisualBridgeTest {
 public interface WorldApi {
  int worldVisualsApiVersion(); boolean hasVisual(String id); boolean isContentReady(Player p);
  AutoCloseable createWorldVisual(Plugin owner,String id,Location at,Collection<UUID> viewers,float scale);
  boolean isWorldVisualActive(Plugin owner,AutoCloseable handle);
 }
 @Test void versionAndPackReadinessFailClosed() {
  var game=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
  var provider=mock(Plugin.class,withSettings().extraInterfaces(WorldApi.class));
  when(game.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);when(provider.isEnabled()).thenReturn(true);
  var api=(WorldApi)provider;var bridge=new PulseContentBridge(game);var player=mock(Player.class);
  assertNotNull(bridge.worldVisualStartProblem("readycraft:kart_pickup_box",List.of(player)));
  when(api.worldVisualsApiVersion()).thenReturn(1);when(api.hasVisual(anyString())).thenReturn(true);
  assertNotNull(bridge.worldVisualStartProblem("readycraft:kart_pickup_box",List.of(player)));
  when(api.isContentReady(player)).thenReturn(true);assertNull(bridge.worldVisualStartProblem("readycraft:kart_pickup_box",List.of(player)));
 }
 @Test void rawHandleStaysOwnerScopedAndClosesOnlyOnce() throws Exception {
  var game=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
  var provider=mock(Plugin.class,withSettings().extraInterfaces(WorldApi.class));
  when(game.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);when(provider.isEnabled()).thenReturn(true);
  var api=(WorldApi)provider;when(api.worldVisualsApiVersion()).thenReturn(1);var raw=mock(AutoCloseable.class);
  var at=new Location(null,0,64,0);var viewers=List.of(UUID.randomUUID());
  when(api.createWorldVisual(game,"readycraft:kart_pickup_box",at,viewers,1f)).thenReturn(raw);
  when(api.isWorldVisualActive(game,raw)).thenReturn(true);
  var bridge=new PulseContentBridge(game);var visual=bridge.createWorldVisual("readycraft:kart_pickup_box",at,viewers);
  assertTrue(visual.active());visual.close();assertFalse(visual.active());bridge.close();verify(raw,times(1)).close();
 }
}
