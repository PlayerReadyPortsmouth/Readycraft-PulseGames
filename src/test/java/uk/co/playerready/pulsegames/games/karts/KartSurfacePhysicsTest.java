package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uk.co.playerready.pulsegames.games.karts.KartPose.Vec;
import static org.junit.jupiter.api.Assertions.*;

class KartSurfacePhysicsTest {
    private final KartPhysics.CollisionSpace empty=(x,y,z,w,h)->true;
    private KartSurface twisting() {
        List<KartSurface.Node> nodes=new ArrayList<>();
        for(int i=0;i<=16;i++) {
            double a=Math.PI*i/8;
            nodes.add(new KartSurface.Node(new Vec(0,10,i*4),new Vec(Math.sin(a),Math.cos(a),0),5));
        }
        return new KartSurface("twist",nodes);
    }
    @Test void rotatesThroughFloorWallAndCeilingWithoutChangingContactPlane() {
        var surface=twisting();
        assertEquals(1,surface.sample(0).pose().up().y(),1e-9);
        assertEquals(1,surface.sample(16).pose().up().x(),1e-9);
        assertEquals(-1,surface.sample(32).pose().up().y(),1e-9);
        assertEquals(1,surface.sample(64).pose().up().y(),1e-9);
        var state=new KartSurfacePhysics.State(0,0,0,12);
        KartSurfacePhysics.Step step=null;
        for(int i=0;i<45;i++) {
            step=KartSurfacePhysics.tick(surface,state,new KartPhysics.Input(1,0),1,empty,KartStyle.BALANCED);
            state=step.state();assertFalse(step.detached());assertEquals(10,step.chassis().y(),1e-9);
        }
        assertNotNull(step);assertTrue(step.pose().up().y()<-.8);
        var q=step.pose().rotation();var transformed=q.transform(new org.joml.Vector3f(0,1,0));
        assertEquals(step.pose().up().x(),transformed.x,1e-6);assertEquals(step.pose().up().y(),transformed.y,1e-6);
    }
    @Test void wallSteeringMovesVerticallyInItsLocalPlane() {
        var wall=twisting();
        var step=KartSurfacePhysics.tick(wall,new KartSurfacePhysics.State(16,0,0,8),new KartPhysics.Input(1,1),1,empty,KartStyle.BALANCED);
        assertTrue(step.chassis().y()>10);assertTrue(step.chassis().z()>16);
    }
    @Test void leavingWallPreservesVerticalMomentum() {
        var surface=twisting();
        var step=KartSurfacePhysics.tick(surface,new KartSurfacePhysics.State(16,3.79,-45,12),new KartPhysics.Input(1,0),1,empty,KartStyle.BALANCED);
        assertTrue(step.detached());assertFalse(step.chassis().grounded());assertTrue(Math.abs(step.chassis().verticalSpeed())>8);
        assertEquals(step.worldVelocity().y(),step.chassis().verticalSpeed());
    }
    @Test void reverseRetainsBodyHeadingAndMovesBackwards() {
        var step=KartSurfacePhysics.tick(twisting(),new KartSurfacePhysics.State(4,0,0,-2),new KartPhysics.Input(-1,0),1,empty,KartStyle.BALANCED);
        assertTrue(step.state().distance()<4);assertEquals(step.pose().yaw(),step.chassis().yaw());
    }
    @Test void leavingRibbonReleasesRatherThanClampingSteering() {
        var surface=twisting();
        var step=KartSurfacePhysics.tick(surface,new KartSurfacePhysics.State(1,3.79,-45,12),new KartPhysics.Input(1,0),1,empty,KartStyle.BALANCED);
        assertTrue(step.detached());assertTrue(step.state().lateral()>3.79);
    }
    @Test void sweptMotionStopsAtThinObstacle() {
        var surface=twisting();
        KartPhysics.CollisionSpace wall=(x,y,z,w,h)->z+w/2<=3;
        var state=new KartSurfacePhysics.State(0,0,0,24);
        for(int i=0;i<10;i++) {
            var step=KartSurfacePhysics.tick(surface,state,new KartPhysics.Input(1,0),2,wall,KartStyle.BALANCED);
            state=step.state();assertTrue(step.pose().position().z()+KartPhysics.BODY_LENGTH/2<=3);
        }
        assertEquals(0,state.speed());
    }
    @Test void onlyAttachesAtEntryWithForwardMotionAndCloseContact() {
        var s=twisting();
        assertNotNull(s.attach(new KartPhysics.State(0,10,0,0,5,0,true)));
        assertNull(s.attach(new KartPhysics.State(0,10,16,0,5,0,true)));
        assertNull(s.attach(new KartPhysics.State(0,12,0,0,5,0,true)));
        assertNull(s.attach(new KartPhysics.State(0,10,0,180,5,0,true)));
    }
    @Test void collisionFollowsInvertedBodyInsteadOfUprightBoundingBox() {
        KartPose p=twisting().pose(32,0,0);
        assertTrue(KartPhysics.poseOverlaps(p,new BoundingBox(-.5,8.5,31.5,.5,9.5,32.5)));
        assertFalse(KartPhysics.poseOverlaps(p,new BoundingBox(-.5,10,31.5,.5,11,32.5)));
    }
    @Test void contactUsesActualTiltedBodies() {
        KartPose wall=twisting().pose(16,0,0);
        assertTrue(KartPhysics.overlaps(wall,wall.translate(new Vec(0,.5,0))));
        assertFalse(KartPhysics.overlaps(wall,wall.translate(new Vec(0,2,0))));
        assertTrue(KartPhysics.overlaps(wall,wall.translate(new Vec(1.7,0,0))));
        assertFalse(KartPhysics.overlaps(wall,wall.translate(new Vec(1.8,0,0))));
    }
    private KartSurface verticalLoop() {
        List<KartSurface.Node> nodes=new ArrayList<>();
        nodes.add(new KartSurface.Node(new Vec(0,100,-4),new Vec(0,1,0),5));
        for(int i=0;i<=16;i++) {
            double a=2*Math.PI*i/16;
            nodes.add(new KartSurface.Node(new Vec(0,110-10*Math.cos(a),10*Math.sin(a)),
                    new Vec(0,Math.cos(a),-Math.sin(a)),5));
        }
        nodes.add(new KartSurface.Node(new Vec(0,100,4),new Vec(0,1,0),5));
        return new KartSurface("vertical-loop",nodes);
    }
    @Test void drivesActualVerticalLoopClimbsInvertsAndReturnsToHorizontalExit() {
        var loop=verticalLoop();var state=new KartSurfacePhysics.State(0,0,0,12);
        boolean climbed=false,inverted=false,descending=false,detached=false;
        KartSurfacePhysics.Step step=null;
        for(int tick=0;tick<160;tick++) {
            step=KartSurfacePhysics.tick(loop,state,new KartPhysics.Input(1,0),1,empty,KartStyle.BALANCED);
            if(step.chassis().y()>108&&step.pose().forward().y()>.9)climbed=true;
            if(step.chassis().y()>119&&step.pose().up().y()<-.9)inverted=true;
            if(step.chassis().y()<112&&step.pose().forward().y()<-.9)descending=true;
            if(step.detached()){detached=true;break;}
            assertTrue(step.state().distance()>state.distance());state=step.state();
        }
        assertTrue(climbed,"Must gain real world height on the climbing wall");
        assertTrue(inverted,"Chassis must point upside down at the loop top");
        assertTrue(descending,"Descending wall must move downwards");assertTrue(detached);
        assertNotNull(step);assertEquals(100,step.chassis().y(),1e-8);assertEquals(1,step.pose().up().y(),1e-8);
        assertEquals(0,step.worldVelocity().y(),1e-8);assertTrue(step.worldVelocity().z()>0);
    }
    @Test void loopBrakesToRestThenReversesAlongItsLocalClimbingDirection() {
        var loop=verticalLoop();
        double wallDistance=4+4*Math.sqrt(200-200*Math.cos(Math.PI/8));
        var state=new KartSurfacePhysics.State(wallDistance,0,0,1.1);
        var stopped=KartSurfacePhysics.tick(loop,state,new KartPhysics.Input(-1,0),1,empty,KartStyle.BALANCED);
        assertEquals(0,stopped.state().speed(),1e-9);assertEquals(state.distance(),stopped.state().distance(),1e-9);
        var reversed=KartSurfacePhysics.tick(loop,stopped.state(),new KartPhysics.Input(-1,0),1,empty,KartStyle.BALANCED);
        assertTrue(reversed.state().distance()<stopped.state().distance());
        assertTrue(reversed.chassis().y()<stopped.chassis().y());
        assertTrue(reversed.pose().forward().y()>.99,"Body keeps facing uphill while backing down");
    }
    @Test void headingWrapRetainsEquivalentPoseAndBoundedBotCorrection() {
        var state=new KartSurfacePhysics.State(16,0,1085,12);
        assertEquals(5,state.headingDegrees());
        var step=KartSurfacePhysics.tick(twisting(),new KartSurfacePhysics.State(16,0,179,12),new KartPhysics.Input(1,1),1,empty,KartStyle.BALANCED);
        assertTrue(step.state().headingDegrees()>=-180&&step.state().headingDegrees()<=180);
        var expected=twisting().pose(step.state().distance(),step.state().lateral(),step.state().headingDegrees());
        assertEquals(expected.forward().x(),step.pose().forward().x(),1e-8);
        assertEquals(expected.forward().y(),step.pose().forward().y(),1e-8);
    }
    @Test void rejectsMalformedAndDiscontinuousAuthoring() {
        assertThrows(IllegalArgumentException.class,()->KartSurfaceConfig.load(Map.of("antigrav-surface1","0,0,0,0,1,0,5;0,0,4,0,-1,0,5")));
        assertThrows(IllegalArgumentException.class,()->KartSurfaceConfig.load(Map.of("antigrav-surface1","NaN,0,0,0,1,0,5;0,0,4,0,1,0,5")));
        assertThrows(IllegalArgumentException.class,()->KartSurfaceConfig.load(Map.of("antigrav-surface1","0,0,0,0,1,0,5;0,0,100,0,1,0,5")));
        assertEquals(1,KartSurfaceConfig.load(Map.of("antigrav-surface1","0,0,0,0,1,0,5;0,0,4,0,1,0,5")).surfaces().size());
        assertFalse(KartSurfaceConfig.load(Map.of()).configured());
        for(String key:List.of("antigrav-surfaceX","antigrav-surface0","antigrav-surface2","antigrav-surface999999999999"))
            assertThrows(IllegalArgumentException.class,()->KartSurfaceConfig.load(Map.of(key,"0,0,0,0,1,0,5;0,0,4,0,1,0,5")));
        var map=new java.util.HashMap<String,Object>();
        for(int i=1;i<=12;i++)map.put("antigrav-surface"+i,"0,0,0,0,1,0,5;0,0,4,0,1,0,5");
        assertEquals("antigrav-surface10",KartSurfaceConfig.load(map).surfaces().get(9).id());
    }
    @Test void hugeFiniteCoordinatesCannotOverflowBlockIteration() {
        assertThrows(IllegalArgumentException.class,()->KartSurfaceConfig.load(Map.of("antigrav-surface1",
                "10000000000,64,0,0,1,0,5;10000000000,64,4,0,1,0,5")));
    }

}
