package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartTrackAccessIntegrationTest {
    KartArcadeTest fixture;
    YamlConfiguration pluginConfig;
    YamlConfiguration settings;
    @BeforeEach void setup() throws Exception {
        fixture = new KartArcadeTest(); fixture.setup();
        pluginConfig = new YamlConfiguration(); settings = new YamlConfiguration();
        when(fixture.game.plugin().getConfig()).thenReturn(pluginConfig);
        when(fixture.game.arena().settings()).thenReturn(settings);
        var checkpoint = new Cuboid(-1, 64, 10, 1, 66, 10);
        var finish = new Cuboid(-1, 64, 20, 1, 66, 20);
        var regions = Map.of("checkpoint1", checkpoint, "finish", finish,
                "jump1", new Cuboid(-1, 64, 3, 1, 66, 3));
        when(fixture.game.arena().regions()).thenReturn(regions);
        when(fixture.game.arena().regionsByPrefix("checkpoint")).thenReturn(List.of(checkpoint));
        when(fixture.game.arena().region("finish")).thenReturn(finish);
    }
    @Test void configuredTrackRequiresAllHumanRacersAndLegacyMapRemainsAvailable() {
        var canary = fixture.kart(0, 0, 12).driver;
        var other = fixture.kart(3, 0, 12).driver;
        assertNull(fixture.logic.startProblem(List.of(canary)));
        settings.set("jump1-launch-speed", 9);
        assertNotNull(fixture.logic.startProblem(List.of(canary)));
        pluginConfig.set("karts.track-physics.enabled", true);
        assertNotNull(fixture.logic.startProblem(List.of(canary)), "empty allowlist is closed");
        pluginConfig.set("karts.track-physics.canary-players", List.of(canary.getUniqueId().toString()));
        assertNull(fixture.logic.startProblem(List.of(canary)));
        assertNotNull(fixture.logic.startProblem(List.of(canary, other)), "one canary cannot admit the whole lobby");
    }
    @Test void malformedPadProfileIsRejectedBeforeStartingEvenForAllowedRacer() {
        var racer = fixture.kart(0, 0, 12).driver;
        pluginConfig.set("karts.track-physics.enabled", true);
        pluginConfig.set("karts.track-physics.canary-players", List.of(racer.getUniqueId().toString()));
        settings.set("jump1-gravity", Double.NaN);
        assertTrue(fixture.logic.startProblem(List.of(racer)).contains("jump1-gravity"));
        settings.set("jump1-gravity", 8);
        settings.set("boost1-factor", 1.7);
        assertTrue(fixture.logic.startProblem(List.of(racer)).contains("requires region boost1"));
    }
    @Test void revokedTrialCleansUpBeforeForcedEndWithoutDependingOnEngineCallback() throws Exception {
        var kart = fixture.kart(0, 0, 12);
        when(fixture.game.alivePlayers()).thenReturn(List.of(kart.driver));
        pluginConfig.set("karts.track-physics.enabled", true);
        pluginConfig.set("karts.track-physics.canary-players", List.of(kart.driver.getUniqueId().toString()));
        fixture.field("advancedTrack", true);
        var before = kart.motion;
        pluginConfig.set("karts.track-physics.enabled", false);
        fixture.drive();
        verify(fixture.game).end(List.of(), "forced");
        assertEquals(before, kart.motion);
        assertTrue(fixture.karts.isEmpty());
        verify(kart.visual).close();
        verify(kart.seat).eject(); verify(kart.seat).remove();
        var order = inOrder(kart.seat, fixture.game);
        order.verify(kart.seat).remove();
        order.verify(fixture.game).end(List.of(), "forced");
        fixture.drive();
        verify(fixture.game, times(1)).end(anyList(), anyString());
    }
    @Test void finishedCanaryStillAuthorisesRemainingBotsToFinish() throws Exception {
        var canary = java.util.UUID.randomUUID();
        pluginConfig.set("karts.track-physics.enabled", true);
        pluginConfig.set("karts.track-physics.canary-players", List.of(canary.toString()));
        fixture.field("advancedTrack", true);
        fixture.field("trackHumanRacers", List.of(canary));
        when(fixture.game.alivePlayers()).thenReturn(List.of());
        fixture.drive();
        verify(fixture.game,never()).end(anyList(),anyString());
    }

}
