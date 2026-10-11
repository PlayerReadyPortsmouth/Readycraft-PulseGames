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
}
