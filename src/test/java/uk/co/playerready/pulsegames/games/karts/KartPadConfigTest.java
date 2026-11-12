package uk.co.playerready.pulsegames.games.karts;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;

class KartPadConfigTest {
    final Map<String, Cuboid> regions = Map.of("jump1", new Cuboid(0, 0, 0, 2, 2, 2),
            "jump2", new Cuboid(10, 0, 0, 12, 2, 2), "boost1", new Cuboid(20, 0, 0, 22, 2, 2));
    @Test void unconfiguredMapsKeepLegacyProfiles() {
        var config = KartPadConfig.load(Map.of("jump-power", 1.5), regions);
        assertFalse(config.configured());
        assertNull(config.jump(0)); assertNull(config.boost(0));
    }
    @Test void eachPadHasIndependentValidatedTuning() {
        var config = KartPadConfig.load(Map.of("jump1-launch-speed", "9", "jump1-gravity", 8,
                "jump2-launch-speed", 14, "jump2-air-ticks", 160, "jump2-yaw", -90,
                "boost1-factor", 1.8, "boost1-ticks", 60), regions);
        assertEquals(9, config.jump(0).launchSpeed());
        assertEquals(14, config.jump(1).launchSpeed());
        assertEquals(160, config.jump(1).maxAirTicks());
        assertEquals(-90, config.jump(1).yaw());
        assertEquals(.25, config.jump(0).airSteering());
        assertEquals(1.8, config.boost(0).factor());
        assertEquals(60, config.boost(0).ticks());
    }
    @Test void rejectsUnknownOrphanNonfiniteAndOutOfRangeSettings() {
        for (var settings : java.util.List.of(Map.<String,Object>of("jump1-gravity", Double.NaN),
                Map.<String,Object>of("jump1-gravity", Double.POSITIVE_INFINITY),
                Map.<String,Object>of("jump1-gravity", 0), Map.<String,Object>of("jump1-air-ticks", 200.1),
                Map.<String,Object>of("jump1-air-ticks", 30.5), Map.<String,Object>of("jump1-air-steering", 1),
                Map.<String,Object>of("boost1-factor", 3), Map.<String,Object>of("boost1-ticks", 0),
                Map.<String,Object>of("jump1-launch-speeed", 9), Map.<String,Object>of("jump3-launch-speed", 9)))
            assertNotNull(KartPadConfig.validationError(settings, regions), settings.toString());
    }
}
