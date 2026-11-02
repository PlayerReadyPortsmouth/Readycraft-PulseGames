package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KartTrackFeaturesTest {
    @Test void disabledAndEmptyAllowlistNeverEnableExperimentalPhysics() {
        var config = new MemoryConfiguration();
        var player = UUID.randomUUID();
        assertFalse(KartTrackFeatures.allowed(config, List.of(player)));
        config.set("karts.track-physics.enabled", true);
        assertFalse(KartTrackFeatures.allowed(config, List.of(player)));
        assertFalse(KartTrackFeatures.allowed(config, List.of()));
    }
    @Test void mixedRaceRequiresEveryHumanAndBroadIsExplicit() {
        var config = new MemoryConfiguration();
        var canary = UUID.randomUUID();
        var other = UUID.randomUUID();
        config.set("karts.track-physics.enabled", true);
        config.set("karts.track-physics.canary-players", List.of(canary.toString()));
        assertTrue(KartTrackFeatures.allowed(config, List.of(canary)));
        assertFalse(KartTrackFeatures.allowed(config, List.of(canary, other)));
        config.set("karts.track-physics.broad", true);
        assertTrue(KartTrackFeatures.allowed(config, List.of(canary, other)));
        config.set("karts.track-physics.enabled", false);
        assertFalse(KartTrackFeatures.allowed(config, List.of(canary)));
    }
}
