package uk.co.playerready.pulsegames.core.game;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PublicGameAccessTest {
 @Test void publicGamesDoNotWidenOtherPreviewOrFeatureAccess() {
  var plugin=mock(PulseGamesPlugin.class); var config=new YamlConfiguration();
  config.set("games.public",List.of("quake","dodgeball"));
  config.set("games.preview",List.of("dropper"));
  when(plugin.getConfig()).thenReturn(config);
  when(plugin.gamePublic(any())).thenCallRealMethod();
  when(plugin.gameAvailable(any())).thenCallRealMethod();
  when(plugin.canUseNewFeatures(any())).thenCallRealMethod();
  for (String id:List.of("quake","dodgeball","dropper","unreleased")) {
   var type=mock(GameType.class);when(type.id()).thenReturn(id);when(type.wip()).thenReturn(true);
   assertEquals(List.of("quake","dodgeball").contains(id),plugin.gamePublic(type));
   assertEquals(!id.equals("unreleased"),plugin.gameAvailable(type));
  }
  var player=mock(org.bukkit.entity.Player.class);when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
  assertFalse(plugin.canUseNewFeatures(player));
 }
}
