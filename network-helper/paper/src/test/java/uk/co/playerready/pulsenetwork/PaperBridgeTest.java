package uk.co.playerready.pulsenetwork;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PaperBridgeTest {
    PaperBridge bridge; Player sender, other; AsyncChatEvent event; List<Runnable> tasks; YamlConfiguration config;
    static void set(Object object,String name,Object value)throws Exception {var f=PaperBridge.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    @BeforeEach void setup()throws Exception {
        bridge=mock(PaperBridge.class,CALLS_REAL_METHODS);set(bridge,"global",new HashMap<>());set(bridge,"lastChat",new HashMap<>());
        set(bridge,"wire",new Wire("test-only-not-a-deployment-secret-0123456789"));
        config=new YamlConfiguration();config.set("chat.enabled",true);config.set("chat.global-by-default",true);doReturn(config).when(bridge).getConfig();
        Server server=mock(Server.class);BukkitScheduler scheduler=mock(BukkitScheduler.class);doReturn(server).when(bridge).getServer();when(server.getScheduler()).thenReturn(scheduler);
        tasks=new ArrayList<>();doAnswer(i->{tasks.add(i.getArgument(1));return null;}).when(scheduler).runTask(eq(bridge),any(Runnable.class));
        sender=mock(Player.class);other=mock(Player.class);when(sender.getUniqueId()).thenReturn(UUID.randomUUID());when(sender.isOnline()).thenReturn(true);when(sender.hasPermission("pulsenetwork.chat")).thenReturn(true);
        event=mock(AsyncChatEvent.class);when(event.getPlayer()).thenReturn(sender);when(event.message()).thenReturn(Component.text("local answer"));when(event.viewers()).thenReturn(new HashSet<>(List.of(sender,other)));
    }
    void dispatch() {try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(sender,other));bridge.chat(event);tasks.forEach(Runnable::run);}}
    @Test void publicMessageRelayedWithoutChangingLocalEvent() {
        dispatch();verify(sender).sendPluginMessage(eq(bridge),eq(Wire.CHANNEL),any(byte[].class));
        verify(event,never()).setCancelled(anyBoolean());verify(event,never()).message(any());verify(event,never()).renderer(any());
    }
    @Test void restrictedViewersRemainLocal() {
        when(event.viewers()).thenReturn(new HashSet<>(List.of(sender)));dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
    }
    @Test void LateCancellationAndMutePermissionPreventRelay() {
        bridge.chat(event);when(event.isCancelled()).thenReturn(true);tasks.forEach(Runnable::run);verify(sender,never()).sendPluginMessage(any(),any(),any());
        when(event.isCancelled()).thenReturn(false);when(sender.hasPermission("pulsenetwork.chat")).thenReturn(false);dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
    }
    @Test void localModeAndVanishNeverRelay() {
        config.set("chat.global-by-default",false);dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
        config.set("chat.global-by-default",true);var hidden=mock(org.bukkit.metadata.MetadataValue.class);when(hidden.asBoolean()).thenReturn(true);when(sender.getMetadata("vanished")).thenReturn(List.of(hidden));dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
    }
}
