package uk.co.playerready.pulsegames.core.content;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PulseContentBridgeTest {
    public interface ContentApi {
        int minigamesApiVersion();
        boolean isContentReady(Player player);
        boolean hasVisual(String id);
        boolean isVisualActive(Player player);
        AutoCloseable attachVisual(Plugin owner, String id, Player player, Collection<UUID> viewers);
    }
    private record Fixture(PulseContentBridge bridge, PulseGamesPlugin plugin, Plugin provider, ContentApi api, Player player) {}
    private Fixture fixture() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        Plugin provider = mock(Plugin.class, withSettings().extraInterfaces(ContentApi.class));
        ContentApi api = (ContentApi) provider;
        when(manager.getPlugin("PulseContent")).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);
        when(api.minigamesApiVersion()).thenReturn(1);
        when(api.hasVisual("props:barrel")).thenReturn(true);
        Player player = mock(Player.class);
        when(plugin.canUseNewFeatures(player)).thenReturn(true);
        when(api.isContentReady(player)).thenReturn(true);
        return new Fixture(new PulseContentBridge(plugin), plugin, provider, api, player);
    }
    @Test void missingAudienceOrCatalogueCannotStart() {
        Fixture f = fixture();
        assertNotNull(f.bridge.startProblem(List.of(), List.of(f.player)));
        assertNotNull(f.bridge.startProblem(List.of("props:barrel"), List.of()));
        verify(f.api, never()).minigamesApiVersion();
    }
    @Test void refusesOldProviderAndUnreadyOrUnapprovedPlayers() {
        Fixture f = fixture();
        when(f.api.minigamesApiVersion()).thenReturn(2);
        assertNotNull(f.bridge.startProblem(List.of("props:barrel"), List.of(f.player)));
        when(f.api.minigamesApiVersion()).thenReturn(1);
        when(f.api.isContentReady(f.player)).thenReturn(false);
        assertNotNull(f.bridge.startProblem(List.of("props:barrel"), List.of(f.player)));
        when(f.api.isContentReady(f.player)).thenReturn(true);
        when(f.plugin.canUseNewFeatures(f.player)).thenReturn(false);
        assertNotNull(f.bridge.startProblem(List.of("props:barrel"), List.of(f.player)));
    }
    @Test void requiresEveryViewerToBeReady() {
        Fixture f = fixture();
        Player second = mock(Player.class);
        when(f.plugin.canUseNewFeatures(second)).thenReturn(true);
        assertNotNull(f.bridge.startProblem(List.of("props:barrel"), List.of(f.player, second)));
        when(f.api.isContentReady(second)).thenReturn(true);
        assertNull(f.bridge.startProblem(List.of("props:barrel"), List.of(f.player, second)));
    }
    @Test void ownerAndAudienceReachProviderAndHandlesCloseExactlyOnce() throws Exception {
        Fixture f = fixture();
        UUID id = UUID.randomUUID();
        AutoCloseable actual = mock(AutoCloseable.class);
        when(f.api.attachVisual(f.plugin, "props:barrel", f.player, List.of(id))).thenReturn(actual);
        AutoCloseable handle = f.bridge.attach("props:barrel", f.player, List.of(id));
        handle.close();
        handle.close();
        f.bridge.close();
        verify(actual, times(1)).close();
        when(f.provider.isEnabled()).thenReturn(false);
        assertFalse(f.bridge.isActive(f.player));
    }
    @Test void providerAttachmentFailureCannotBeMistakenForSuccess() {
        Fixture f = fixture();
        when(f.api.attachVisual(any(), any(), any(), any())).thenThrow(new IllegalStateException("missing model"));
        assertThrows(IllegalStateException.class, () -> f.bridge.attach("props:barrel", f.player, List.of(UUID.randomUUID())));
    }
}
