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
        set(bridge,"restores",new HashMap<>());set(bridge,"wire",new Wire("test-only-not-a-deployment-secret-0123456789"));
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
    @Test void localDeliveryClearsViewersButGlobalRelayStillFiresOnce() throws Exception {
        config.set("chat.local-system-delivery",true);when(sender.displayName()).thenReturn(Component.text("Ann"));
        var viewers=new HashSet<net.kyori.adventure.audience.Audience>(List.of(sender,other));when(event.viewers()).thenReturn(viewers);
        try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(sender,other));
            bridge.chat(event);tasks.forEach(Runnable::run);}
        assertTrue(viewers.isEmpty());verify(other,times(1)).sendMessage(any(Component.class));
        verify(sender,times(1)).sendPluginMessage(eq(bridge),eq(Wire.CHANNEL),any(byte[].class));
    }
    @Test void localDeliveryOffLeavesEventAlone() throws Exception {
        var viewers=new HashSet<net.kyori.adventure.audience.Audience>(List.of(sender,other));when(event.viewers()).thenReturn(viewers);
        dispatch();assertEquals(2,viewers.size());
        verify(other,never()).sendMessage(any(Component.class));
    }
    @Test void restrictedViewersRemainLocal() {
        when(event.viewers()).thenReturn(new HashSet<>(List.of(sender)));dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
    }
    @Test void LateCancellationAndMutePermissionPreventRelay() {
        try(var bk=mockStatic(Bukkit.class)){bk.when(Bukkit::getOnlinePlayers).thenReturn(List.of(sender,other));bridge.chat(event);}when(event.isCancelled()).thenReturn(true);tasks.forEach(Runnable::run);verify(sender,never()).sendPluginMessage(any(),any(),any());
        when(event.isCancelled()).thenReturn(false);when(sender.hasPermission("pulsenetwork.chat")).thenReturn(false);dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
    }
    @Test void localModeAndVanishNeverRelay() {
        config.set("chat.global-by-default",false);dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
        config.set("chat.global-by-default",true);var hidden=mock(org.bukkit.metadata.MetadataValue.class);when(hidden.asBoolean()).thenReturn(true);when(sender.getMetadata("vanished")).thenReturn(List.of(hidden));dispatch();verify(sender,never()).sendPluginMessage(any(),any(),any());
    }

    @Test void restoreWorksOnSourceWithQueuePublishingDisabled() throws Exception {
        var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(world.getName()).thenReturn("survival");
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
        var location=new Location(world,123.5,78,-45.5,90,12);var locations=new ReturnLocations();locations.put(sender.getUniqueId(),location);set(bridge,"returnLocations",locations);
        when(sender.getWorld()).thenReturn(world);when(sender.teleport(any(Location.class))).thenReturn(true);
        when(world.getChunkAtAsync(any(Location.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mock(Chunk.class)));
        var feet=mock(org.bukkit.block.Block.class);var head=mock(org.bukkit.block.Block.class);var floor=mock(org.bukkit.block.Block.class);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(feet);when(world.getBlockAt(any(Location.class))).thenReturn(feet);when(feet.isPassable()).thenReturn(true);when(head.isPassable()).thenReturn(true);
        when(feet.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(head);when(feet.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(floor);when(floor.getType()).thenReturn(Material.STONE);
        config.set("queues.enabled",false);
        var scheduler=bridge.getServer().getScheduler();
        doAnswer(i->{tasks.add(i.getArgument(1));return null;}).when(scheduler).runTaskLater(eq(bridge),any(Runnable.class),eq(20L));
        var wire=new Wire("test-only-not-a-deployment-secret-0123456789");
        try(var bukkit=mockStatic(Bukkit.class);var safety=mockStatic(PaperBridge.class)){
            safety.when(()->PaperBridge.safe(any(Location.class))).thenReturn(true);
            bukkit.when(()->Bukkit.getWorld("survival")).thenReturn(world);
            bridge.onPluginMessageReceived(Wire.CHANNEL,sender,wire.encode("RESTORE",sender.getUniqueId(),UUID.randomUUID().toString()));
            assertEquals(1,tasks.size());for(int i=0;i<tasks.size();i++)tasks.get(i).run();verify(sender).teleport(location);
        }
    }
    @Test void restoreForAnotherPlayerIsRejected() throws Exception {
        var wire=new Wire("test-only-not-a-deployment-secret-0123456789");
        bridge.onPluginMessageReceived(Wire.CHANNEL,sender,wire.encode("RESTORE",UUID.randomUUID(),"wrong-player"));
        assertTrue(tasks.isEmpty());verify(sender,never()).teleportAsync(any());
    }
    @Test void remoteChatRendersStyledWhenExtraFieldsArePresentAndPlainWhenNot() throws Exception {
        set(bridge,"tags",NameStyle.Tags.DEFAULT);var wire=new Wire("test-only-not-a-deployment-secret-0123456789");
        var identity=new NameStyle.Identity("Ann","dev","ocean",7);
        bridge.onPluginMessageReceived(Wire.CHANNEL,sender,wire.encode("REMOTECHAT",sender.getUniqueId(),RemoteChat.fields("survival",UUID.randomUUID(),identity,"hello")));
        bridge.onPluginMessageReceived(Wire.CHANNEL,sender,wire.encode("REMOTECHAT",sender.getUniqueId(),"survival",UUID.randomUUID().toString(),"Ann","hello"));
        var line=org.mockito.ArgumentCaptor.forClass(Component.class);verify(sender,times(2)).sendMessage(line.capture());
        assertEquals(RemoteChat.line("survival",identity,"hello",false,NameStyle.Tags.DEFAULT),line.getAllValues().get(0));
        assertEquals("[Global \u00b7 survival] [DEV] [7] Ann: hello",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line.getAllValues().get(0)));
        assertEquals("[Global \u00b7 survival] Ann: hello",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line.getAllValues().get(1)));
    }
}
