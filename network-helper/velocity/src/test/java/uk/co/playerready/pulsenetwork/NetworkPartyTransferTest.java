package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetworkPartyTransferTest {
    static final String KEY = "test-only-not-a-deployment-secret-0123456789";
    @SuppressWarnings("unchecked")
    static <T> T field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(object);
    }
    static void invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        var method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(object, args);
    }
    @Test void groupJoinWaitsForEveryArrivalAndKeepsDistinctReturnOrigins() throws Exception { exercise(false); }
    @Test void failedGroupReturnsAConnectionThatSucceedsLate() throws Exception { exercise(true); }
    private void exercise(boolean failEarly) throws Exception {
        ProxyServer proxy = mock(ProxyServer.class);
        VelocityBridge bridge = new VelocityBridge(proxy, mock(Logger.class), Path.of("unused"));
        VelocityBridgeTest.set(bridge, "wire", new Wire(KEY));
        VelocityBridgeTest.set(bridge, "backends", Set.of("survival", "lobby", "minigamestest"));
        VelocityBridgeTest.set(bridge, "queueServers", Set.of("minigamestest"));
        Player leader = player(proxy), friend = player(proxy);
        ServerConnection leaderOrigin = connection(leader, "survival");
        connection(friend, "lobby");
        NetworkParties parties = field(bridge, "parties");
        parties.invite(leader.getUniqueId(), friend.getUniqueId()); parties.accept(friend.getUniqueId());
        QueueDirectory queues = field(bridge, "queues");
        String token = queues.update("minigamestest", "arena", "Game", 1, 12).token();
        RegisteredServer target = mock(RegisteredServer.class);
        when(proxy.getServer("minigamestest")).thenReturn(Optional.of(target));
        ConnectionRequestBuilder first = mock(ConnectionRequestBuilder.class), second = mock(ConnectionRequestBuilder.class);
        when(leader.createConnectionRequest(target)).thenReturn(first); when(friend.createConnectionRequest(target)).thenReturn(second);
        CompletableFuture<ConnectionRequestBuilder.Result> firstFuture = new CompletableFuture<>(), secondFuture = new CompletableFuture<>();
        when(first.connect()).thenReturn(firstFuture); when(second.connect()).thenReturn(secondFuture);
        invoke(bridge, "join", new Class<?>[]{Player.class, String.class}, leader, token);
        Map<UUID, Object> pending = field(bridge, "pending");
        Object request = pending.get(leader.getUniqueId());
        assertSame(request, pending.get(friend.getUniqueId()));
        if (failEarly) {
            firstFuture.completeExceptionally(new IllegalStateException("connection rejected"));
            assertTrue(pending.isEmpty());
            RegisteredServer home = mock(RegisteredServer.class);
            when(proxy.getServer("lobby")).thenReturn(Optional.of(home));
            ConnectionRequestBuilder returnBuilder = mock(ConnectionRequestBuilder.class);
            when(friend.createConnectionRequest(home)).thenReturn(returnBuilder);
            var successful = mock(ConnectionRequestBuilder.Result.class);
            when(successful.isSuccessful()).thenReturn(true);
            when(returnBuilder.connect()).thenReturn(CompletableFuture.completedFuture(successful));
            ServerConnection lateDestination = connection(friend, "minigamestest");
            secondFuture.complete(successful);
            verify(friend).createConnectionRequest(home);
            verify(lateDestination, never()).sendPluginMessage(any(), any(byte[].class));
            return;
        }
        ServerConnection destination = connection(leader, "minigamestest");
        invoke(bridge, "deliver", new Class<?>[]{Player.class, request.getClass()}, leader, request);
        verify(destination, never()).sendPluginMessage(any(), any(byte[].class));
        connection(friend, "minigamestest");
        invoke(bridge, "deliver", new Class<?>[]{Player.class, request.getClass()}, leader, request);
        ArgumentCaptor<byte[]> packet = ArgumentCaptor.forClass(byte[].class);
        verify(destination).sendPluginMessage(any(), packet.capture());
        Wire.Message message = new Wire(KEY).decode(packet.getValue()).orElseThrow();
        assertEquals("GROUPJOIN", message.kind());
        assertEquals(leader.getUniqueId() + "," + friend.getUniqueId(), message.fields().get(2));
        Map<UUID, Object> visits = field(bridge, "visits");
        assertEquals("survival", origin(visits.get(leader.getUniqueId())));
        assertEquals("lobby", origin(visits.get(friend.getUniqueId())));
        var acknowledgement = new com.velocitypowered.api.event.connection.PluginMessageEvent(destination, leader,
                MinecraftChannelIdentifier.from(Wire.CHANNEL), new Wire(KEY).encode("ACK", leader.getUniqueId(), message.fields().getFirst()));
        bridge.message(acknowledgement);
        assertTrue(pending.isEmpty(), "Leader acknowledgement commits the entire group atomically");
        firstFuture.completeExceptionally(new IllegalStateException("late callback"));
        assertEquals(2, visits.size(), "Late failure cannot cancel an already acknowledged group");
        Map<String, Long> cancelled = field(bridge, "cancelledGroups");
        assertTrue(cancelled.isEmpty());
    }
    private String origin(Object visit) throws Exception {
        var method = visit.getClass().getDeclaredMethod("origin"); method.setAccessible(true); return (String) method.invoke(visit);
    }
    private Player player(ProxyServer proxy) {
        Player player = mock(Player.class); UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.hasPermission(anyString())).thenReturn(true);
        when(proxy.getPlayer(id)).thenReturn(Optional.of(player)); return player;
    }
    private ServerConnection connection(Player player, String name) {
        ServerConnection connection = mock(ServerConnection.class);
        when(connection.getServerInfo()).thenReturn(new ServerInfo(name, new InetSocketAddress("localhost", 25566)));
        when(connection.getPlayer()).thenReturn(player);
        when(player.getCurrentServer()).thenReturn(Optional.of(connection)); return connection;
    }
}
