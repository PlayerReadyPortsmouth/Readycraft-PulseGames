package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KartPhysicsTest {
    private final KartPhysics.CollisionSpace floor = (x,y,z,w,h) -> y >= -0.00001;
    private KartPhysics.State start(double speed) { return new KartPhysics.State(0,0,0,0,speed,0,true); }
    private KartPhysics.State run(KartPhysics.State state, KartPhysics.Input input, double multiplier, int ticks) {
        for (int i=0;i<ticks;i++) state=KartPhysics.tick(state,input,multiplier,floor);
        return state;
    }
    @Test void collisionFootprintContainsModelAtEveryRotation() {
        assertTrue(KartPhysics.WIDTH >= Math.hypot(1.5, 1.875));
    }
    @Test void acceleratesAndCapsBoostAndReverseSpeeds() {
        var normal=run(start(0),new KartPhysics.Input(1,0),1,100);
        var boost=run(start(0),new KartPhysics.Input(1,0),1.5,100);
        var reverse=run(start(0),new KartPhysics.Input(-1,0),1,100);
        assertEquals(10,normal.speed());assertEquals(15,boost.speed());assertEquals(-3.5,reverse.speed());
        assertTrue(boost.z()>normal.z());assertTrue(reverse.z()<0);
    }
    @Test void brakesBeforeReversingAndCoastsToRest() {
        var brake=KartPhysics.tick(start(10),new KartPhysics.Input(-1,0),1,floor);
        assertEquals(8.9,brake.speed(),1e-9);
        assertEquals(0,run(start(10),new KartPhysics.Input(0,0),1,50).speed());
        assertTrue(run(start(10),new KartPhysics.Input(-1,0),1,30).speed()<0);
    }
    @Test void steersFromKartHeadingAndReversesSteeringWhenBacking() {
        var forward=KartPhysics.tick(start(5),new KartPhysics.Input(1,1),1,floor);
        var reverse=KartPhysics.tick(start(-2),new KartPhysics.Input(-1,1),1,floor);
        assertTrue(forward.yaw()>0);assertTrue(forward.x()<0);assertTrue(forward.z()>0);
        assertTrue(reverse.yaw()<0);assertTrue(reverse.z()<0);
        assertEquals(0,KartPhysics.tick(start(0),new KartPhysics.Input(0,1),1,floor).yaw());
    }
    @Test void boostedMotionCannotTunnelThroughThinWall() {
        KartPhysics.CollisionSpace wall=(x,y,z,w,h)-> y>=-0.00001 && (z+w/2<=2 || z-w/2>=2.05);
        var state=start(20);
        for(int i=0;i<10;i++) state=KartPhysics.tick(state,new KartPhysics.Input(1,0),2,wall);
        assertTrue(state.z()+KartPhysics.WIDTH/2<=2);assertEquals(0,state.speed());
    }
    @Test void climbsSlabButNotFullBlockOrLowCeiling() {
        KartPhysics.CollisionSpace slab=(x,y,z,w,h)->y>=-0.00001 && (z+w/2<=2 || y>=.5-0.00001);
        var state=start(5);
        for(int i=0;i<12;i++)state=KartPhysics.tick(state,new KartPhysics.Input(1,0),1,slab);
        assertTrue(state.z()>2);assertEquals(.5,state.y(),.001);
        KartPhysics.CollisionSpace low=(x,y,z,w,h)->slab.clear(x,y,z,w,h)&&y+h<=2;
        state=start(5);
        for(int i=0;i<12;i++)state=KartPhysics.tick(state,new KartPhysics.Input(1,0),1,low);
        assertTrue(state.z()+KartPhysics.WIDTH/2<=2);assertEquals(0,state.speed());
    }
    @Test void fallsOffEdgeAndLandsWithoutTunnelling() {
        KartPhysics.CollisionSpace pit=(x,y,z,w,h)->y>=-3 && (z-w/2>=1 || y>=-0.00001);
        var state=start(8);
        for(int i=0;i<50;i++)state=KartPhysics.tick(state,new KartPhysics.Input(1,0),1,pit);
        assertEquals(-3,state.y(),.001);assertTrue(state.grounded());assertEquals(0,state.verticalSpeed());
    }
    @Test void unloadedWorldIsRejectedBeforeAnyBlockRead() {
        World world=mock(World.class);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertFalse(KartPhysics.world(world).clear(15.8,64,0,KartPhysics.WIDTH,KartPhysics.HEIGHT));
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void slowMultiplierCapsExistingSpeedAndInputsStayFinite() {
        assertEquals(3,KartPhysics.tick(start(10),new KartPhysics.Input(1,0),.3,floor).speed(),1e-9);
        var state=KartPhysics.tick(start(0),new KartPhysics.Input(Double.NaN,Double.POSITIVE_INFINITY),Double.NaN,floor);
        assertEquals(0,state.speed());assertEquals(0,state.yaw());
    }
}
