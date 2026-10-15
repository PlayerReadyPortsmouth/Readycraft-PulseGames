package uk.co.playerready.pulsegames.games.karts;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartBumpsTest {
    private KartPhysics.State at(double x,double y,double z,float yaw,double speed) {
        return new KartPhysics.State(x,y,z,yaw,speed,0,true);
    }
    private KartPhysics.State straight(double x,double z,double speed){return at(x,0,z,0,speed);}
    @Test void catchesFastSweptRearImpactAndIdentifiesOnlyCarBehindAsAttacker(){
        var a=straight(0,-3,18);var an=straight(0,1,18);var b=straight(0,0,4);var bn=straight(0,.2,4);
        assertTrue(KartBumps.rearImpact(a,an,b,bn));assertFalse(KartBumps.rearImpact(b,bn,a,an));
    }
    @Test void sidesOvertakingHeadOnReverseAndExistingOverlapDoNotHit(){
        var b=straight(0,0,4);var bn=straight(0,.2,4);
        assertFalse(KartBumps.rearImpact(straight(1,-3,12),straight(1,0,12),b,bn));
        assertFalse(KartBumps.rearImpact(at(0,0,-3,180,12),at(0,0,0,180,12),b,bn));
        assertFalse(KartBumps.rearImpact(straight(0,-3,-12),straight(0,0,-12),b,bn));
        assertFalse(KartBumps.rearImpact(straight(0,-1,12),straight(0,0,12),b,bn));
    }
    @Test void requiresClosingSpeedAndHeightOverlap(){
        assertFalse(KartBumps.rearImpact(straight(0,-2,5),straight(0,-1.8,5),straight(0,0,4),straight(0,0,4)));
        assertFalse(KartBumps.rearImpact(at(0,2,-3,0,12),at(0,2,0,0,12),straight(0,0,4),straight(0,.2,4)));
    }
    @Test void usesVictimHeadingAndSweptLateralPosition(){
        assertTrue(KartBumps.rearImpact(at(3,0,0,90,12),at(0,0,0,90,12),at(0,0,0,90,0),at(0,0,0,90,0)));
        // Crossing sideways only: forward distance never reaches the rear plane.
        assertFalse(KartBumps.rearImpact(straight(3,-2,12),straight(0,-2,12),straight(0,0,0),straight(0,0,0)));
    }
}
