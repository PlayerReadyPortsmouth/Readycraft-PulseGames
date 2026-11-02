package uk.co.playerready.pulsegames.core.content;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartPoseBridgeTest {
    public interface PoseApi {
        int kartApiVersion(); int kartPoseApiVersion();
        boolean isKartPoseReady(String id, Player viewer);
        void setKartPose(Plugin owner, Entity anchor,double x,double y,double z,double w);
    }
    @Test void gatesEveryViewerAndPassesOwnerAndQuaternion() {
        var game=mock(PulseGamesPlugin.class,RETURNS_DEEP_STUBS);
        var provider=mock(Plugin.class,withSettings().extraInterfaces(PoseApi.class));
        when(game.getServer().getPluginManager().getPlugin("PulseContent")).thenReturn(provider);
        when(provider.isEnabled()).thenReturn(true);
        var api=(PoseApi)provider;when(api.kartApiVersion()).thenReturn(1);
        var bridge=new PulseContentBridge(game);var viewer=mock(Player.class);
        assertNotNull(bridge.kartPoseStartProblem("readycraft:racing_kart",List.of(viewer)));
        when(api.kartPoseApiVersion()).thenReturn(1);
        assertNotNull(bridge.kartPoseStartProblem("readycraft:racing_kart",List.of(viewer)));
        when(api.isKartPoseReady("readycraft:racing_kart",viewer)).thenReturn(true);
        assertNull(bridge.kartPoseStartProblem("readycraft:racing_kart",List.of(viewer)));
        var anchor=mock(Entity.class);bridge.setKartPose(anchor,0,0,0,1);
        verify(api).setKartPose(game,anchor,0,0,0,1);
        assertThrows(IllegalArgumentException.class,()->bridge.setKartPose(anchor,0,0,Double.NaN,1));
    }
}
