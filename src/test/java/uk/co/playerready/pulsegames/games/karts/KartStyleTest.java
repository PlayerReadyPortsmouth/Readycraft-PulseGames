package uk.co.playerready.pulsegames.games.karts;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartStyleTest {
    private final KartPhysics.CollisionSpace floor=(x,y,z,w,h)->y>=0;
    private KartPhysics.State start(){return new KartPhysics.State(0,0,0,0,0,0,true);}
    private KartPhysics.State run(KartStyle style,int ticks,KartPhysics.Input input){
        var state=start();for(int i=0;i<ticks;i++)state=KartPhysics.tick(state,input,1,floor,style);return state;
    }
    @Test void originalPhysicsEntryPointIsExactlyBalanced(){
        var input=new KartPhysics.Input(1,.3);
        assertEquals(KartPhysics.tick(start(),input,1,floor),KartPhysics.tick(start(),input,1,floor,KartStyle.BALANCED));
    }
    @Test void lightAcceleratesFasterButDoesNotExceedBalancedTopSpeed(){
        var forward=new KartPhysics.Input(1,0);
        assertTrue(run(KartStyle.LIGHT,10,forward).speed()>run(KartStyle.BALANCED,10,forward).speed());
        for(var style:KartStyle.values())assertTrue(run(style,200,forward).speed()<=12);
        assertEquals(12,run(KartStyle.LIGHT,200,forward).speed());
        assertEquals(11.04,run(KartStyle.TRICKSTER,200,forward).speed(),1e-9);
    }
    @Test void heavyTradesAccelerationAndSteeringForBumpResistance(){
        var input=new KartPhysics.Input(1,.25);
        assertTrue(run(KartStyle.HEAVY,10,input).speed()<run(KartStyle.BALANCED,10,input).speed());
        assertTrue(run(KartStyle.LIGHT,10,input).yaw()>run(KartStyle.BALANCED,10,input).yaw());
        assertTrue(run(KartStyle.HEAVY,10,input).yaw()<run(KartStyle.BALANCED,10,input).yaw());
        assertEquals(1.35,KartStyle.HEAVY.bumpPower());assertEquals(.7,KartStyle.HEAVY.incomingDurationMultiplier());
    }
}
