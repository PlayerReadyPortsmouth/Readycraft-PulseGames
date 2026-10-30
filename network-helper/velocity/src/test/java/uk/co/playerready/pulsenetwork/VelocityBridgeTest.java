package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VelocityBridgeTest {
    static final String KEY = "test-only-not-a-deployment-secret-0123456789";
    ProxyServer proxy; VelocityBridge bridge; Player sender, remote; ServerConnection source;
    Wire wire = new Wire(KEY);
    static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    @BeforeEach void setup() throws Exception {
        proxy = mock(ProxyServer.class); bridge = new VelocityBridge(proxy, mock(Logger.class), Path.of("unused"));
        set(bridge,"wire", new Wire(KEY)); set(bridge,"backends",Set.of("survival","minigamestest")); set(bridge,"queueServers",Set.of("minigamestest"));
        sender = player(); remote = player(); source = connection(sender,"survival"); connection(remote,"minigamestest");
        when(proxy.getAllPlayers()).thenReturn(List.of(sender,remote));
    }
    Player player() { Player p=mock(Player.class); when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getUsername()).thenReturn("Test");return p; }
    ServerConnection connection(Player p,String name) {
        ServerConnection c=mock(ServerConnection.class);when(c.getServerInfo()).thenReturn(new ServerInfo(name,new InetSocketAddress("localhost",25566)));
        when(c.getPlayer()).thenReturn(p);when(p.getCurrentServer()).thenReturn(Optional.of(c));return c;
    }
    PluginMessageEvent message(String kind,String...fields) {
        return new PluginMessageEvent(source,sender,MinecraftChannelIdentifier.from(Wire.CHANNEL),wire.encode(kind,sender.getUniqueId(),fields));
    }
    @Test void reconnectToGameResumesReturnTicketWithoutForcingReturn() throws Exception {
        Class<?> visitClass = Class.forName(VelocityBridge.class.getName() + "$Visit");
        var ctor = visitClass.getDeclaredConstructors()[0]; ctor.setAccessible(true);
        String request = UUID.randomUUID().toString();
        var visits = new java.util.concurrent.ConcurrentHashMap<UUID, Object>();
        visits.put(sender.getUniqueId(), ctor.newInstance("survival", "minigamestest", request));
        set(bridge, "visits", visits);
        set(bridge, "reconnectUntil", new java.util.concurrent.ConcurrentHashMap<>(Map.of(sender.getUniqueId(), System.currentTimeMillis() + 30_000)));
        source = connection(sender, "minigamestest");
        var event = mock(com.velocitypowered.api.event.player.ServerPostConnectEvent.class);
        when(event.getPlayer()).thenReturn(sender);
        bridge.connected(event);
        var packet = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(source).sendPluginMessage(any(), packet.capture());
        var message = new Wire(KEY).decode(packet.getValue()).orElseThrow();
        assertEquals("RESUMEVISIT", message.kind());
        assertEquals(List.of(request), message.fields());
        verify(sender, never()).createConnectionRequest(any());
        assertFalse(visits.isEmpty());
        bridge.message(message("RESUMEACK", request));
        var resumeField = VelocityBridge.class.getDeclaredField("resuming"); resumeField.setAccessible(true);
        assertTrue(((Map<?, ?>) resumeField.get(bridge)).isEmpty());
    }
    @Test void approvedChatDeliveredOnlyRemotelyAndReplayRejected() {
        var event=message("CHAT","Hello"); bridge.message(event); bridge.message(event);
        assertFalse(event.getResult().isAllowed());verify(remote,times(1)).sendMessage(any(Component.class));verify(sender,never()).sendMessage(any(Component.class));
    }
    @Test void clientOriginSpoofAlwaysConsumedAndNeverRelayed() {
        var event=new PluginMessageEvent(sender,source,MinecraftChannelIdentifier.from(Wire.CHANNEL),wire.encode("CHAT",sender.getUniqueId(),"spoof"));
        bridge.message(event); assertFalse(event.getResult().isAllowed());verify(remote,never()).sendMessage(any(Component.class));
    }
    @Test void wrongCarrierIdentityAndOldBackendConnectionRejected() {
        var wrong=new PluginMessageEvent(source,sender,MinecraftChannelIdentifier.from(Wire.CHANNEL),wire.encode("CHAT",remote.getUniqueId(),"spoof"));
        bridge.message(wrong);connection(sender,"minigamestest");bridge.message(message("CHAT","late packet"));
        verify(remote,never()).sendMessage(any(Component.class));
    }
    @Test void onlyAllowedQueueOriginsCanAdvertiseAndWithdrawalExpiresClick() throws Exception {
        bridge.message(message("QUEUE","abc_duels","Duels","1","4"));verify(remote,never()).sendMessage(any(Component.class));
        source=connection(sender,"minigamestest");bridge.message(message("QUEUE","abc_duels","Duels","1","4"));
        verify(remote).sendMessage(any(Component.class));
        var field=VelocityBridge.class.getDeclaredField("queues");field.setAccessible(true);var directory=(QueueDirectory)field.get(bridge);
        String token=directory.list().getFirst().token();bridge.message(message("WITHDRAW","abc_duels"));assertTrue(directory.find(token).isEmpty());
    }

    @Test void chatHoldDoesNotDisableQueueAnnouncements() throws Exception {
        set(bridge,"chatBackends",Set.of("minigamestest"));
        bridge.message(message("CHAT","must stay local"));verify(remote,never()).sendMessage(any(Component.class));
        source=connection(sender,"minigamestest");bridge.message(message("QUEUE","game1","Duels","1","4"));
        verify(remote).sendMessage(any(Component.class));
    }
    @Test void authenticatedReturnUsesSavedOriginAndRejectsWrongRequest() throws Exception {
        Class<?> visitClass=Class.forName(VelocityBridge.class.getName()+"$Visit");
        var ctor=visitClass.getDeclaredConstructors()[0];ctor.setAccessible(true);
        String request=UUID.randomUUID().toString();
        var visits=new java.util.concurrent.ConcurrentHashMap<UUID,Object>();visits.put(sender.getUniqueId(),ctor.newInstance("survival","minigamestest",request));set(bridge,"visits",visits);
        var target=mock(RegisteredServer.class);when(proxy.getServer("survival")).thenReturn(Optional.of(target));
        var builder=mock(ConnectionRequestBuilder.class);when(sender.createConnectionRequest(target)).thenReturn(builder);
        var result=mock(ConnectionRequestBuilder.Result.class);when(result.isSuccessful()).thenReturn(true);
        when(builder.connect()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(result));
        source=connection(sender,"minigamestest");bridge.message(message("RETURN","wrong"));verify(sender,never()).createConnectionRequest(any());
        bridge.message(message("RETURN",request));verify(sender).createConnectionRequest(target);assertTrue(visits.isEmpty());
        bridge.message(message("RETURN",request));verify(sender,times(1)).createConnectionRequest(target);
    }
    @Test void returnCannotBeRequestedByDifferentBackend() throws Exception {
        Class<?> cls=Class.forName(VelocityBridge.class.getName()+"$Visit");var ctor=cls.getDeclaredConstructors()[0];ctor.setAccessible(true);
        String request=UUID.randomUUID().toString();var visits=new java.util.concurrent.ConcurrentHashMap<UUID,Object>();visits.put(sender.getUniqueId(),ctor.newInstance("survival","minigamestest",request));set(bridge,"visits",visits);
        bridge.message(message("RETURN",request));verify(sender,never()).createConnectionRequest(any());assertFalse(visits.isEmpty());
    }

    @Test void paperRecipientsAreDeliveredThroughTheirNativeIgnoreAdapter() throws Exception {
        set(bridge,"paperBackends",Set.of("minigamestest"));
        var target=remote.getCurrentServer().orElseThrow();
        bridge.message(message("CHAT","hello"));
        verify(remote,never()).sendMessage(any(Component.class));
        var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(target).sendPluginMessage(eq(MinecraftChannelIdentifier.from(Wire.CHANNEL)),bytes.capture());
        var decoded=new Wire(KEY).decode(bytes.getValue()).orElseThrow();assertEquals("REMOTECHAT",decoded.kind());assertEquals(remote.getUniqueId(),decoded.player());assertEquals(sender.getUniqueId().toString(),decoded.fields().get(1));
    }
    @Test void failedReturnCompletingAfterDisconnectKeepsOriginForReconnect() throws Exception {
        Class<?> cls=Class.forName(VelocityBridge.class.getName()+"$Visit");var ctor=cls.getDeclaredConstructors()[0];ctor.setAccessible(true);
        String request=UUID.randomUUID().toString();var visits=new java.util.concurrent.ConcurrentHashMap<UUID,Object>();
        visits.put(sender.getUniqueId(),ctor.newInstance("survival","minigamestest",request));set(bridge,"visits",visits);
        var target=mock(RegisteredServer.class);when(proxy.getServer("survival")).thenReturn(Optional.of(target));
        var builder=mock(ConnectionRequestBuilder.class);when(sender.createConnectionRequest(target)).thenReturn(builder);
        var future=new java.util.concurrent.CompletableFuture<ConnectionRequestBuilder.Result>();when(builder.connect()).thenReturn(future);
        source=connection(sender,"minigamestest");bridge.message(message("RETURN",request));
        var disconnect=mock(com.velocitypowered.api.event.connection.DisconnectEvent.class);when(disconnect.getPlayer()).thenReturn(sender);bridge.quit(disconnect);
        future.completeExceptionally(new IllegalStateException("disconnected"));assertTrue(visits.containsKey(sender.getUniqueId()));
        source=connection(sender,"survival");var connected=mock(com.velocitypowered.api.event.player.ServerPostConnectEvent.class);when(connected.getPlayer()).thenReturn(sender);bridge.connected(connected);
        var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);verify(source).sendPluginMessage(eq(MinecraftChannelIdentifier.from(Wire.CHANNEL)),bytes.capture());
        assertEquals("RESTORE",new Wire(KEY).decode(bytes.getValue()).orElseThrow().kind());assertTrue(visits.isEmpty());
    }

    @Test void globalRelayShowsRoleTagAndStyledNameStaticallyAndFlattensForBedrock() throws Exception {
        set(bridge, "lpMeta", (IdentityResolver.Meta) (id, key) -> key.equals("pulse.role") ? "dev" : key.equals("pulse.namestyle") ? "rainbow" : null);
        bridge.message(message("CHAT", "hello"));
        var line = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(remote).sendMessage(line.capture());
        assertEquals("[Global · survival] [DEV] Test: hello", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line.getValue()));
        assertEquals(line.getValue(), bridge.relayLine(sender, "survival", "hello", false), "static frame: identical every time");
        assertNotEquals(line.getValue(), bridge.relayLine(sender, "survival", "hello", true), "Bedrock gets one flat colour");
    }
    @Test void globalRelayWithoutRoleIsAsBefore() {
        bridge.message(message("CHAT", "hello"));
        var line = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(remote).sendMessage(line.capture());
        assertEquals("[Global · survival] Test: hello", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line.getValue()));
    }
    @Test void paperRecipientsGetRoleStyleAndLevelFieldsThatRenderLikeTheProxyLine() throws Exception {
        set(bridge,"paperBackends",Set.of("minigamestest"));
        set(bridge,"lpMeta",(IdentityResolver.Meta)(id,key)->key.equals("pulse.role")?"dev":key.equals("pulse.namestyle")?"sunset":null);
        var target=remote.getCurrentServer().orElseThrow();
        bridge.message(message("CHAT","hello"));
        var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(target).sendPluginMessage(eq(MinecraftChannelIdentifier.from(Wire.CHANNEL)),bytes.capture());
        var fields=new Wire(KEY).decode(bytes.getValue()).orElseThrow().fields();
        assertEquals(List.of("survival",sender.getUniqueId().toString(),"Test","hello","dev","sunset",""),fields);
        for(boolean bedrock:new boolean[]{false,true})
            assertEquals(bridge.relayLine(sender,"survival","hello",bedrock),RemoteChat.line("survival",RemoteChat.identity(fields,NameStyle.Tags.DEFAULT),"hello",bedrock,NameStyle.Tags.DEFAULT));
    }
}
