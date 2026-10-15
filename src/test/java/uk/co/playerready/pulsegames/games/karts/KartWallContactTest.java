package uk.co.playerready.pulsegames.games.karts;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KartWallContactTest {
    final KartPhysics.Input coast = new KartPhysics.Input(0, 0);
    final KartPhysics.CollisionSpace floor = (x,y,z,w,h) -> y >= -.00001;
    final KartPhysics.CollisionSpace wall = (x,y,z,w,h) -> y >= -.00001 && x >= 0;
    KartPhysics.State at(float yaw, double speed) {
        return new KartPhysics.State(0,0,0,yaw,speed,0,true);
    }
    @Test void shallowScrapeSlidesLosesSpeedAndNeverEntersWall() {
        var initial=at(15,10);
        var free=KartPhysics.tick(initial,coast,1,floor);
        var slide=KartPhysics.tick(initial,coast,1,wall);
        assertEquals(0,slide.x(),.000001);assertTrue(slide.z()>0);
        assertTrue(slide.speed()>0);assertTrue(slide.speed()<free.speed());
        assertTrue(Math.hypot(slide.x(),slide.z())<Math.hypot(free.x(),free.z()));
        for(int i=0;i<20;i++) {
            double before=slide.speed();slide=KartPhysics.tick(slide,coast,1,wall);
            assertTrue(wall.clearAt(slide.x(),slide.y(),slide.z(),slide.yaw()));
            assertTrue(slide.speed()<=before);
        }
    }
    @Test void frontalAndSteepHitsStillStop() {
        for(float yaw:new float[]{90,65,45}) {
            var state=KartPhysics.tick(at(yaw,10),coast,1,wall);
            assertEquals(0,state.speed());assertEquals(0,state.x());assertEquals(0,state.z());
        }
    }
    @Test void blockedCornerCannotSlideOrGainSpeed() {
        KartPhysics.CollisionSpace corner=(x,y,z,w,h)->y>=-.00001 && x>=0 && z<=0;
        var result=KartPhysics.tick(at(15,10),coast,1,corner);
        assertEquals(0,result.speed());assertEquals(0,result.x());assertEquals(0,result.z());
    }
    @Test void reverseScrapePreservesDirectionAndLosesMagnitude() {
        var result=KartPhysics.tick(at(-15,-3),coast,1,wall);
        assertEquals(0,result.x());assertTrue(result.z()<0);
        assertTrue(result.speed()<0);assertTrue(Math.abs(result.speed())<2.75);
    }
    @Test void wallAtEndOfSlideStopsRemainingSubsteps() {
        KartPhysics.CollisionSpace corner=(x,y,z,w,h)->y>=-.00001 && x>=0 && z<=.15;
        var result=KartPhysics.tick(at(15,10),coast,1,corner);
        assertEquals(0,result.speed());assertTrue(result.z()<=.15);assertEquals(0,result.x());
    }
    @Test void driftSlipUsesMovementHeadingWithoutChangingChassisOrBypassingWall() {
        var result=KartPhysics.tick(at(30,10),coast,1,wall,KartStyle.BALANCED,20);
        assertEquals(30,result.yaw());assertEquals(0,result.x());assertTrue(result.z()>0);assertTrue(result.speed()>0);
        assertEquals(KartPhysics.tick(at(30,10),coast,1,wall,KartStyle.BALANCED,20),
                KartPhysics.tick(at(30,10),coast,1,wall,KartStyle.BALANCED,200));
        assertEquals(KartPhysics.tick(at(30,10),coast,1,wall),
                KartPhysics.tick(at(30,10),coast,1,wall,KartStyle.BALANCED,Double.NaN));
    }
    @Test void mirroredWallSlidesAlongOtherAxis() {
        KartPhysics.CollisionSpace wallZ=(x,y,z,w,h)->y>=-.00001 && z<=0;
        var result=KartPhysics.tick(at(75,10),coast,1,wallZ);
        assertTrue(result.x()<0);assertEquals(0,result.z());assertTrue(result.speed()>0);
    }
}
