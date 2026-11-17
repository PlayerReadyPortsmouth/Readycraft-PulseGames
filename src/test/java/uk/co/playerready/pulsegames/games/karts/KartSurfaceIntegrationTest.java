package uk.co.playerready.pulsegames.games.karts;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartSurfaceIntegrationTest {
    private record Fixture(GameInstance game,PulseKartsGame logic,Player player,YamlConfiguration config,YamlConfiguration settings) {}
    private Fixture fixture() {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var config=new YamlConfiguration();var settings=new YamlConfiguration();
        settings.set("antigrav-surface1","0,64,0,0,1,0,5;0,64,8,0,1,0,5");
        when(game.plugin().getConfig()).thenReturn(config);when(game.arena().settings()).thenReturn(settings);
        when(game.arena().regions()).thenReturn(Map.of());
        when(game.arena().region("finish")).thenReturn(new Cuboid(0,64,20,4,68,21));
        when(game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of(new Cuboid(0,64,10,4,68,11)));
        return new Fixture(game,new PulseKartsGame(game),player,config,settings);
    }
    private void enable(Fixture f) {
        f.config().set("karts.track-physics.enabled",true);
        f.config().set("karts.track-physics.canary-players",List.of(f.player().getUniqueId().toString()));
    }
    @Test void surfaceTrackRequiresEveryHumanInTrialAndSupportedPoseBackend() {
        var f=fixture();
        assertTrue(f.logic().startProblem(List.of(f.player())).contains("allowlist"));
        enable(f);assertNull(f.logic().startProblem(List.of(f.player())));
        var outsider=mock(Player.class);when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(f.logic().startProblem(List.of(f.player(),outsider)).contains("allowlist"));
        when(f.game().plugin().content().kartPoseStartProblem(eq(PulseKartsGame.MODEL),anyList())).thenReturn("Pose adapter unavailable");
        assertEquals("Pose adapter unavailable",f.logic().startProblem(List.of(f.player())));
    }
    @Test void ordinaryTrackDoesNotNeedTrialOrPoseBackend() {
        var f=fixture();f.settings().set("antigrav-surface1",null);
        assertNull(f.logic().startProblem(List.of(f.player())));
        verify(f.game().plugin().content(),never()).kartPoseStartProblem(anyString(),anyList());
    }
    @Test void malformedSurfacePreventsStart() {
        var f=fixture();enable(f);f.settings().set("antigrav-surface1","0,0,0,0,1,0,5;0,0,8,0,-1,0,5");
        assertNotNull(f.logic().startProblem(List.of(f.player())));
    }
    @Test void revokingTrialStopsRunningAdvancedRaceBeforeMovement() throws Exception {
        var f=fixture();enable(f);when(f.game().state()).thenReturn(GameState.RUNNING);
        when(f.game().alivePlayers()).thenReturn(List.of(f.player()));
        var field=PulseKartsGame.class.getDeclaredField("advancedTrack");field.setAccessible(true);field.set(f.logic(),true);
        f.config().set("karts.track-physics.enabled",false);
        var drive=PulseKartsGame.class.getDeclaredMethod("drive");drive.setAccessible(true);drive.invoke(f.logic());
        verify(f.game()).end(List.of(),"forced");
    }
    @Test void lostPoseCapabilityCancelsWithoutRaceRewards() throws Exception {
        var f=fixture();enable(f);when(f.game().state()).thenReturn(GameState.RUNNING);
        when(f.game().alivePlayers()).thenReturn(List.of(f.player()));
        var surfaces=PulseKartsGame.class.getDeclaredField("surfaces");surfaces.setAccessible(true);
        surfaces.set(f.logic(),KartSurfaceConfig.load(f.settings().getValues(false)));
        var tick=PulseKartsGame.class.getDeclaredField("tick");tick.setAccessible(true);tick.setLong(f.logic(),19);
        when(f.game().plugin().content().kartPoseStartProblem(anyString(),anyList())).thenReturn("Proxy capability lost");
        var drive=PulseKartsGame.class.getDeclaredMethod("drive");drive.setAccessible(true);drive.invoke(f.logic());
        verify(f.game()).end(List.of(),"forced");
    }

}
