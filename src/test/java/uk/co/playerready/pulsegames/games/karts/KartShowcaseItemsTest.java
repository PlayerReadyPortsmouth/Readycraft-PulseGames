package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.*;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartShowcaseItemsTest {
    KartArcadeTest f;
    @BeforeEach void setup() throws Exception { f=new KartArcadeTest(); f.setup(); set("showcaseEnabled",true); f.tick(100); }
    void set(String name,Object value) throws Exception { var field=PulseKartsGame.class.getDeclaredField(name);field.setAccessible(true);field.set(f.logic,value); }
    void use(PulseKartsGame.Kart kart) throws Exception { var m=PulseKartsGame.class.getDeclaredMethod("useItem",PulseKartsGame.Kart.class);m.setAccessible(true);m.invoke(f.logic,kart); }
    void pulses() throws Exception { var m=PulseKartsGame.class.getDeclaredMethod("tickPulses");m.setAccessible(true);m.invoke(f.logic); }
    @Test void repairRecoversFromSpinWithoutAddingSpeedAndRespectsDisabledGate() throws Exception {
        var kart=f.kart(0,0,2);kart.item=PulseKartsGame.Item.REPAIR_KIT;kart.spinUntil=110;kart.slowUntil=130;
        set("showcaseEnabled",false);use(kart);assertEquals(110,kart.spinUntil);assertNotNull(kart.item);
        set("showcaseEnabled",true);use(kart);assertEquals(0,kart.spinUntil);assertEquals(0,kart.slowUntil);
        assertEquals(140,kart.bumpImmuneUntil);assertEquals(2,kart.motion.speed());assertNull(kart.item);
    }
    @Test void pulseTelegraphsThenHitsNearbyRacersButHonoursShieldsAndRange() throws Exception {
        var owner=f.kart(0,0,0);var victim=f.kart(3,0,0);var shield=f.kart(4,0,0);var far=f.kart(7,0,0);
        shield.shieldUntil=200;owner.item=PulseKartsGame.Item.PULSE_WAVE;use(owner);
        f.tick(109);pulses();assertEquals(0,victim.slowUntil);
        f.tick(110);pulses();assertEquals(140,victim.slowUntil);assertEquals(0,owner.slowUntil);
        assertEquals(0,shield.slowUntil);assertEquals(0,shield.shieldUntil);assertEquals(0,far.slowUntil);
        assertNull(owner.pulseOrigin);
    }
    @Test void pulseCannotHitThroughWallsAndRevocationCancelsPendingWave() throws Exception {
        var owner=f.kart(0,0,0);var victim=f.kart(3,0,0);owner.item=PulseKartsGame.Item.PULSE_WAVE;use(owner);
        when(f.game.world().rayTraceBlocks(any(Location.class),any(Vector.class),anyDouble(),eq(FluidCollisionMode.ALWAYS),eq(true)))
                .thenReturn(new RayTraceResult(new Vector(1,64,0)));
        f.tick(110);pulses();assertEquals(0,victim.slowUntil);
        owner.item=PulseKartsGame.Item.PULSE_WAVE;use(owner);set("showcaseEnabled",false);f.tick(120);pulses();assertNull(owner.pulseOrigin);
    }
    @Test void magnetNeedsCollectableBoxesAndCannotBeRefreshedWhileActive() throws Exception {
        var kart=f.kart(0,0,0);kart.item=PulseKartsGame.Item.MAGNET;use(kart);assertNotNull(kart.item);
        set("boxes",new KartItemBoxes(List.of(new KartArcadeConfig.Pt(3,64,0))));use(kart);
        assertEquals(220,kart.magnetUntil);assertNull(kart.item);
        kart.item=PulseKartsGame.Item.MAGNET;f.tick(110);use(kart);assertEquals(220,kart.magnetUntil);assertNotNull(kart.item);
    }
    @Test void resetCancelsArmedWaveAndMagnet() throws Exception {
        var kart=f.kart(0,0,0);kart.pulseOrigin=new Location(f.game.world(),0,64,0);kart.pulseAt=110;kart.magnetUntil=220;
        Location spawn=new Location(f.game.world(),0,64,0);
        when(f.game.spawnFor(kart.driver)).thenReturn(spawn);
        var m=PulseKartsGame.class.getDeclaredMethod("reset",PulseKartsGame.Kart.class);m.setAccessible(true);m.invoke(f.logic,kart);
        assertNull(kart.pulseOrigin);assertEquals(0,kart.pulseAt);assertEquals(0,kart.magnetUntil);
    }
    @Test void activeMagnetCannotStealABoxWhileHoldingAnItemOrRolling() throws Exception {
        var boxes=new KartItemBoxes(List.of(new KartArcadeConfig.Pt(3,64,0)));set("boxes",boxes);
        set("displays",mock(KartBoxDisplays.class));var kart=f.kart(0,0,0);kart.magnetUntil=220;
        var collect=PulseKartsGame.class.getDeclaredMethod("collectBox",PulseKartsGame.Kart.class,KartPhysics.State.class,KartPhysics.State.class);
        collect.setAccessible(true);kart.item=PulseKartsGame.Item.TURBO;collect.invoke(f.logic,kart,kart.motion,kart.motion);
        assertTrue(boxes.available(0,100));kart.item=null;kart.rollStart=99;collect.invoke(f.logic,kart,kart.motion,kart.motion);
        assertTrue(boxes.available(0,100));kart.rollStart=-1;
        try(var stacks=mockConstruction(org.bukkit.inventory.ItemStack.class)) {
            collect.invoke(f.logic,kart,kart.motion,kart.motion);
            assertFalse(boxes.available(0,100));assertEquals(100,kart.rollStart);
        }
    }
    @Test void extendedBoxReachRequiresVisibilityAndStillAllowsOnlyOneClaim() {
        var boxes=new KartItemBoxes(List.of(new KartArcadeConfig.Pt(3,64,0)));
        var state=f.state(0,0,0);
        assertEquals(-1,boxes.collect(state,state,100));
        assertEquals(-1,boxes.collect(state,state,100,3.5,i->false));
        assertEquals(0,boxes.collect(state,state,100,3.5,i->true));
        assertEquals(-1,boxes.collect(state,state,100,3.5,i->true));
        assertEquals(0,boxes.collect(state,state,180,3.5,i->true));
    }
    @Test void showcasePoolAddsThreeItemsWithoutChangingDefaultPool() {
        assertEquals(8,KartItemBalance.roulettePool().length);
        assertEquals(11,KartItemBalance.roulettePool(true).length);
        for(var tier:KartItemBalance.Tier.values()) {
            var weights=KartItemBalance.weights(KartStyle.BALANCED,tier,Set.of(),true);
            assertEquals(100,Arrays.stream(weights).sum());
            var seen=EnumSet.noneOf(PulseKartsGame.Item.class);
            for(int n=0;n<100;n++)seen.add(KartItemBalance.choose(KartStyle.BALANCED,n,tier,Set.of(),true));
            assertTrue(seen.containsAll(List.of(PulseKartsGame.Item.REPAIR_KIT,PulseKartsGame.Item.PULSE_WAVE,PulseKartsGame.Item.MAGNET)));
        }
    }
}
