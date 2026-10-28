package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
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
        assertEquals(12,normal.speed());assertEquals(18,boost.speed());assertEquals(-3.5,reverse.speed());
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
        assertEquals(3.6,KartPhysics.tick(start(10),new KartPhysics.Input(1,0),.3,floor).speed(),1e-9);
        var state=KartPhysics.tick(start(0),new KartPhysics.Input(Double.NaN,Double.POSITIVE_INFINITY),Double.NaN,floor);
        assertEquals(0,state.speed());assertEquals(0,state.yaw());
    }
    private World corridorWorld() {
        World world=mock(World.class);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(invocation->{
            int x=invocation.getArgument(0),y=invocation.getArgument(1),z=invocation.getArgument(2);
            Block block=mock(Block.class);boolean solid=y<0 || x<=-2 || x>=1;
            when(block.isPassable()).thenReturn(!solid);
            when(block.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1));return block;
        });return world;
    }
    @Test void realWorldOrientedBodyFitsTwoBlockCorridorButDiagonalDoesNot() {
        var space=KartPhysics.world(corridorWorld());
        assertTrue(space.clearAt(0,0,0,0));assertTrue(space.clearAt(0,0,0,90));
        assertFalse(space.clearAt(0,0,0,45));assertFalse(space.clear(0,0,0,KartPhysics.WIDTH,KartPhysics.HEIGHT));
        var state=start(8);
        for(int i=0;i<20;i++)state=KartPhysics.tick(state,new KartPhysics.Input(1,0),1,space);
        assertTrue(state.z()>9);assertEquals(12,state.speed());
    }
    @Test void turningCannotRotateBodyThroughWall() {
        var space=KartPhysics.world(corridorWorld());var state=start(3);
        for(int i=0;i<20;i++) {
            state=KartPhysics.tick(state,new KartPhysics.Input(1,1),1,space);
            assertTrue(space.clearAt(state.x(),state.y(),state.z(),state.yaw()));
        }
        assertTrue(state.yaw()<45);
    }
    @Test void kartOverlapUsesActualRotationAndVerticalSeparation() {
        var a=start(0);
        assertFalse(KartPhysics.overlaps(a,new KartPhysics.State(1.6,0,0,0,0,0,true)));
        assertTrue(KartPhysics.overlaps(a,new KartPhysics.State(1.6,0,0,45,0,0,true)));
        assertFalse(KartPhysics.overlaps(a,new KartPhysics.State(0,2,0,0,0,0,true)));
        assertFalse(KartPhysics.overlaps(a,new KartPhysics.State(1.5,0,0,0,0,0,true)));
    }
    @Test void botCanFollowNarrowStraightUsingRotatedBodyProbes() {
        var space=KartPhysics.world(corridorWorld());var state=start(0);
        var bot=new KartBotDriver(new java.util.UUID(1,2));
        for(int i=0;i<80;i++) {
            var d=bot.update(state,new KartBotDriver.Point(0,0,50),null,
                    uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL,space,java.util.List.of());
            assertFalse(d.reset());state=KartPhysics.tick(state,d.input(),1,space);
        }
        assertTrue(state.z()>20);assertEquals(0,state.x(),.01);
    }
    @Test void settledFloorOriginNeverSinksIntoSolidOrSlabLightSample() {
        for(double surface:new double[]{64,64.5}) {
            World world=mock(World.class);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            Block air=mock(Block.class);when(air.isPassable()).thenReturn(true);
            java.util.Map<String,Block> cached=new java.util.HashMap<>();
            when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(invocation->{
                int x=invocation.getArgument(0),y=invocation.getArgument(1),z=invocation.getArgument(2);
                if(y>=surface)return air;
                return cached.computeIfAbsent(x+":"+y+":"+z,key->{
                    Block solid=mock(Block.class);
                    when(solid.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,Math.min(y+1,surface),z+1));return solid;
                });
            });
            var space=KartPhysics.world(world);
            assertFalse(space.clearAt(0,surface-0.000001,0,0));
            var state=new KartPhysics.State(0,surface,0,0,0,0,true);
            for(int tick=0;tick<400;tick++) {
                state=KartPhysics.tick(state,new KartPhysics.Input(0,0),1,space);
                assertTrue(state.y()>=surface,"Chassis must not sink into surface at tick "+tick);
                assertEquals(surface,state.y(),0,"No artificial levitation");
            }
        }
    }
    @Test void launchImpulseLiftsAnApexOfAboutTwoBlocksAndLandsAfterAboutAThirdOfASecond() {
        var state=KartPhysics.tick(new KartPhysics.State(0,0,0,0,12,KartAir.launchSpeed(12,1),false),new KartPhysics.Input(1,0),1,floor);
        assertTrue(state.y()>0,"lifts off while the chassis still touches the pad");assertFalse(state.grounded());
        double apex=state.y();int airTicks=1;
        while(!state.grounded() && airTicks<100){state=KartPhysics.tick(state,new KartPhysics.Input(1,0),1,floor);apex=Math.max(apex,state.y());airTicks++;}
        assertEquals(2.0,apex,.25);assertTrue(airTicks>=15 && airTicks<=19,"air ticks "+airTicks);
        assertEquals(0,state.y(),1e-3);assertTrue(state.z()>8,"carries speed forward: "+state.z());
    }
    @Test void aCeilingStopsTheAscentInsteadOfClipping() {
        KartPhysics.CollisionSpace lowCeiling=(x,y,z,w,h)->y>=-0.00001 && y+h<=3;
        var state=new KartPhysics.State(0,0,0,0,12,KartAir.launchSpeed(12,1),false);
        for(int i=0;i<40;i++){state=KartPhysics.tick(state,new KartPhysics.Input(1,0),1,lowCeiling);assertTrue(state.y()+KartPhysics.HEIGHT<=3+1e-6);}
    }
    @Test void restingAndFallingStatesStillBehaveExactlyAsBefore() {
        var rest=KartPhysics.tick(start(0),new KartPhysics.Input(0,0),1,floor);assertEquals(0,rest.y(),1e-3);assertTrue(rest.grounded());
        var falling=KartPhysics.tick(new KartPhysics.State(0,5,0,0,0,0,false),new KartPhysics.Input(0,0),1,floor);
        assertTrue(falling.y()<5 && falling.verticalSpeed()<0);
    }
}
