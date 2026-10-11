package uk.co.playerready.pulsenetwork;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.*;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LocalChatGamesTest {
    PaperBridge plugin; LocalChatGames games; Player player; PluginManager manager;
    static void set(Object object,String name,Object value)throws Exception {var f=LocalChatGames.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    @BeforeEach void setup()throws Exception {
        plugin=mock(PaperBridge.class);Server server=mock(Server.class);manager=mock(PluginManager.class);player=mock(Player.class);
        when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(manager);when(manager.getPlugins()).thenReturn(new Plugin[0]);
        when(server.getOnlinePlayers()).thenReturn((Collection)List.of(player));when(player.getName()).thenReturn("Tester");
        var config=new YamlConfiguration();config.set("chat-games.enabled",true);config.set("chat-games.minimum-players",1);config.set("chat-games.external-provider-plugins",List.of("PulseAddon"));when(plugin.getConfig()).thenReturn(config);
        games=new LocalChatGames(plugin);set(games,"next",0L);
    }
    @Test void oneLocalWinnerOnly()throws Exception {
        games.tick();set(games,"answer","diamond");assertFalse(games.answer(player,"wrong"));
        assertTrue(games.answer(player," DIAMOND "));assertFalse(games.answer(player,"diamond"));verify(player,times(2)).sendMessage(any(Component.class));
    }
    @Test void existingChatGamesAndPulseAddonPreventCompetingRounds() {
        Plugin existing=mock(Plugin.class);when(existing.isEnabled()).thenReturn(true);when(existing.getName()).thenReturn("ChatGames-Premium");when(manager.getPlugins()).thenReturn(new Plugin[]{existing});
        games.tick();verify(player,never()).sendMessage(any(Component.class));
        when(existing.getName()).thenReturn("PulseAddon");games.tick();verify(player,never()).sendMessage(any(Component.class));
    }
    @Test void expiredAnswerCannotWin()throws Exception {
        set(games,"answer","diamond");set(games,"deadline",0L);assertFalse(games.answer(player,"diamond"));
    }
}
