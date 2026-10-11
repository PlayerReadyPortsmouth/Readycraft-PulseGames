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

    @Test void restoreWorksOnSourceWithQueuePublishingDisabled() throws Exception {
        var locations=new YamlConfiguration();var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());
        var location=new Location(world,123.5,78,-45.5,90,12);locations.set(sender.getUniqueId().toString(),location);set(bridge,"returnLocations",locations);
        config.set("queues.enabled",false);
        var scheduler=bridge.getServer().getScheduler();
        doAnswer(i->{tasks.add(i.getArgument(1));return null;}).when(scheduler).runTaskLater(eq(bridge),any(Runnable.class),eq(20L));
        var wire=new Wire("test-only-not-a-deployment-secret-0123456789");
        bridge.onPluginMessageReceived(Wire.CHANNEL,sender,wire.encode("RESTORE",sender.getUniqueId(),UUID.randomUUID().toString()));
        assertEquals(1,tasks.size());tasks.forEach(Runnable::run);verify(sender).teleportAsync(location);
    }
    @Test void restoreForAnotherPlayerIsRejected() throws Exception {
        var wire=new Wire("test-only-not-a-deployment-secret-0123456789");
        bridge.onPluginMessageReceived(Wire.CHANNEL,sender,wire.encode("RESTORE",UUID.randomUUID(),"wrong-player"));
        assertTrue(tasks.isEmpty());verify(sender,never()).teleportAsync(any());
    }
}
