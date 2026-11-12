package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartConfiguredPadsIntegrationTest {
    private KartArcadeTest fixture;
    @BeforeEach void setup() throws Exception {
        fixture = new KartArcadeTest();
        fixture.setup();
        when(fixture.game.everyone()).thenReturn(List.of());
        when(fixture.game.plugin().content().kartActive(any())).thenReturn(true);
    }
    void configure(Map<String, Object> settings, Map<String, Cuboid> regions) throws Exception {
        fixture.field("arcade", KartArcadeConfig.load(settings, regions));
        fixture.field("padProfiles", KartPadConfig.load(settings, regions));
    }
    void touch(PulseKartsGame.Kart kart, KartPhysics.State before, KartPhysics.State after) throws Exception {
        kart.motion = after;
        fixture.call("touchPads", new Class<?>[]{PulseKartsGame.Kart.class, KartPhysics.State.class, KartPhysics.State.class},
                kart, before, after);
    }
    @Test void drivingConfiguredJumpProducesLongFlightAndLanding() throws Exception {
        configure(Map.of("jump1-launch-speed", 9, "jump1-gravity", 8),
                Map.of("jump1", new Cuboid(-1, 64, 3, 1, 66, 5)));
        var kart = fixture.kart(0, 2.8, 12);
        kart.input = new KartPhysics.Input(1, 0);
        fixture.drive();
        assertTrue(kart.air.configuredFlight());
        double apex = kart.motion.y(); int ticks = 0;
        while (kart.air.flying() && ticks++ < 120) {
            fixture.drive(); apex = Math.max(apex, kart.motion.y());
        }
        assertTrue(ticks >= 43 && ticks <= 46, "flight duration: " + ticks);
        assertTrue(apex > 68.7 && apex < 69.1, "apex: " + apex);
        assertTrue(kart.motion.grounded());
        assertTrue(kart.motion.z() > 28, "preserves forward momentum through flight");
        assertFalse(kart.boost.active(KartBoost.Source.TRICK, fixture.logicTick()));
    }
    @Test void lipEntryCanLaunchAfterLosingGroundButAirborneOverflightCannot() throws Exception {
        configure(Map.of("jump1-launch-speed", 14), Map.of("jump1", new Cuboid(-1, 64, 3, 1, 66, 3)));
        var kart = fixture.kart(0, 2.8, 12);
        var before = kart.motion;
        var after = new KartPhysics.State(0, 64, 3.4, 0, 12, -1, false);
        touch(kart, before, after);
        assertEquals(14, kart.motion.verticalSpeed());
        var airborne = fixture.kart(0, 2.8, 12);
        touch(airborne, new KartPhysics.State(0, 64, 2.8, 0, 12, -1, false), after);
        assertFalse(airborne.air.flying());
    }
    @Test void configuredBoostUsesItsOwnFactorDurationAndDirection() throws Exception {
        configure(Map.of("boost1-factor", 1.8, "boost1-ticks", 60, "boost1-yaw", 0),
                Map.of("boost1", new Cuboid(-1, 64, 3, 1, 66, 5)));
        var kart = fixture.kart(0, 2.8, 12); kart.input = new KartPhysics.Input(1, 0);
        fixture.drive();
        assertEquals(1.8, kart.boost.multiplier(1)); assertEquals(61, kart.boost.until());
        fixture.drive(); assertEquals(61, kart.boost.until());
        var wrongWay = fixture.kart(0, 6.2, 12);
        touch(wrongWay, new KartPhysics.State(0, 64, 6.2, 180, 12, 0, true),
                new KartPhysics.State(0, 64, 5.8, 180, 12, 0, true));
        assertFalse(wrongWay.boost.active(fixture.logicTick()));
    }
    @Test void verticalSurfacePanelsBoostAndWorldUpJumpPadsDoNotDetachRider() throws Exception {
        configure(Map.of("boost1-factor", 1.7, "jump1-launch-speed", 12),
                Map.of("boost1", new Cuboid(-1, 68, -1, 1, 68, 1),
                        "jump1", new Cuboid(-1, 68, -1, 1, 68, 1)));
        var kart = fixture.kart(0, 0, 12);
        var up = new KartPose.Vec(1, 0, 0);
        // An already attached wall pose; complete surface entry/exit validation is exercised by surface tests.
        kart.surface = mock(KartSurface.class);
        kart.pose = new KartPose(new KartPose.Vec(0, 68.4, 0), new KartPose.Vec(0, 0, 1), up,
                new KartPose.Vec(0, 1, 0));
        touch(kart, new KartPhysics.State(0, 67.8, 0, 0, 12, 0, true),
                new KartPhysics.State(0, 68.4, 0, 0, 12, 0, true));
        assertEquals(1.7, kart.boost.multiplier(0));
        assertFalse(kart.air.flying(), "world-up jump profiles cannot launch from a wall");
    }
    @Test void resetClearsProfileFlightSurfaceOrientationAndBoosts() throws Exception {
        var kart = fixture.kart(0, 0, 12);
        var profile = new KartPadConfig.JumpProfile(9, 8, 100, 3, 40, null, .25);
        kart.motion = kart.air.launch(kart.motion, profile, true);
        kart.boost.apply(KartBoost.Source.PANEL, 1.8, 0, 100);
        var up = new KartPose.Vec(1, 0, 0);
        kart.surface = mock(KartSurface.class);
        kart.surfaceMotion = new KartSurfacePhysics.State(4, 0, 0, 12);
        kart.detachedFlight = true;
        kart.pose = new KartPose(new KartPose.Vec(0, 68, 0), new KartPose.Vec(0, 0, 1), up,
                new KartPose.Vec(0, 1, 0));
        var grid = new org.bukkit.Location(fixture.game.world(), 0, 64, 0);
        when(fixture.game.spawnFor(kart.driver)).thenReturn(grid);
        fixture.logic.onDeath(kart.driver, null);
        assertFalse(kart.air.flying()); assertEquals(24, kart.air.gravity());
        assertNull(kart.surface); assertNull(kart.surfaceMotion); assertNull(kart.pose); assertFalse(kart.detachedFlight);
        assertFalse(kart.boost.active(1)); assertEquals(0, kart.motion.speed());
        assertEquals(64, kart.motion.y()); assertEquals(20, kart.surfaceReady);
    }
    @Test void detachedSurfaceFlightPreservesWorldMomentumUntilLanding() throws Exception {
        var kart = fixture.kart(0, 0, 20);
        kart.motion = new KartPhysics.State(0, 70, 0, 0, 20, 4, false);
        kart.detachedFlight = true;
        var types = new Class<?>[]{PulseKartsGame.Kart.class, KartPhysics.Input.class, double.class, double.class};
        kart.motion = (KartPhysics.State) fixture.call("moveChassis", types, kart, new KartPhysics.Input(0, 0), 1d, 0d);
        assertEquals(20, kart.motion.speed(), 1e-9, "no instant ground-speed cap on release");
        assertEquals(2.8, kart.motion.verticalSpeed(), 1e-9, "world gravity resumes");
        assertEquals(1, kart.motion.z(), 1e-9);
        int guard = 0;
        while (!kart.motion.grounded() && guard++ < 100)
            kart.motion = (KartPhysics.State) fixture.call("moveChassis", types, kart, new KartPhysics.Input(0, 0), 1d, 0d);
        assertTrue(kart.motion.grounded()); assertFalse(kart.detachedFlight);
        kart.motion = (KartPhysics.State) fixture.call("moveChassis", types, kart, new KartPhysics.Input(0, 0), 1d, 0d);
        assertTrue(kart.motion.speed() <= KartPhysics.MAX_SPEED, "normal control resumes after landing");
    }
}
