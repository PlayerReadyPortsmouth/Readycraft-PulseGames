package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;

class KartBotDriverTest {
    private final KartPhysics.CollisionSpace floor=(x,y,z,w,h)->y>=-.00001;
    private KartPhysics.State start() { return new KartPhysics.State(0,0,0,0,0,0,true); }
    private KartBotDriver driver() { return new KartBotDriver(new UUID(1,2)); }
    @Test void completesRectangleUsingOrdinaryPhysics() {
        var points=List.of(new KartBotDriver.Point(0,0,25),new KartBotDriver.Point(-25,0,25),
                new KartBotDriver.Point(-25,0,0),new KartBotDriver.Point(0,0,0));
        for (var level:BotDifficulty.values()) {
            var bot=driver();var state=start();int reached=0;
            for(int tick=0;tick<2400 && reached<8;tick++) {
                var target=points.get(reached%4);
                if(Math.hypot(state.x()-target.x(),state.z()-target.z())<KartBotDriver.ARRIVAL_RADIUS) {reached++;target=points.get(reached%4);}
                var decision=bot.update(state,target,points.get((reached+1)%4),level,floor,List.of());
                assertFalse(decision.reset());
                state=KartPhysics.tick(state,decision.input(),1,floor);
            }
            assertEquals(8,reached,level.toString());
        }
    }
    @Test void steersAroundFreestandingObstacleWithoutReset() {
        KartPhysics.CollisionSpace obstacle=(x,y,z,w,h)->y>=-.00001
                && (Math.abs(x)>=2+w/2 || z+w/2<=12 || z-w/2>=14);
        var bot=driver();var state=start();
        for(int tick=0;tick<600 && state.z()<25;tick++) {
            var d=bot.update(state,new KartBotDriver.Point(0,0,30),null,BotDifficulty.NORMAL,obstacle,List.of());
            assertFalse(d.reset());state=KartPhysics.tick(state,d.input(),1,obstacle);
        }
        assertTrue(state.z()>25,"Obstacle should be navigated, not simply waited out");
    }
    @Test void wallAndBlockedReverseEventuallyRequestOrdinaryReset() {
        var bot=driver();var state=start();boolean reset=false,recovered=false;
        KartPhysics.CollisionSpace boxed=(x,y,z,w,h)->y>=-.00001 && Math.abs(x)<.1 && Math.abs(z)<.1;
        for(int tick=0;tick<300;tick++) {
            var d=bot.update(state,new KartBotDriver.Point(0,0,20),null,BotDifficulty.NORMAL,boxed,List.of());
            recovered|=bot.recovering();reset|=d.reset();
        }
        assertTrue(recovered);assertTrue(reset);bot.reset();assertEquals(0,bot.stuckTicks());
    }
    @Test void brakesBeforeEdgeOrStationaryTrafficAndDoesNotUseTurbo() {
        var state=new KartPhysics.State(0,0,0,0,8,0,true);
        var target=new KartBotDriver.Point(0,0,30);
        KartPhysics.CollisionSpace edge=(x,y,z,w,h)->z>1 || y>=-.00001;
        var d=driver().update(state,target,null,BotDifficulty.HARD,edge,List.of());
        assertTrue(d.input().throttle()<0);assertFalse(d.useTurboSafe());
        var car=new KartPhysics.State(0,0,2,0,0,0,true);
        d=driver().update(state,target,null,BotDifficulty.HARD,floor,List.of(car));
        assertTrue(d.input().throttle()<0);assertFalse(d.useTurboSafe());
    }
    @Test void identityAndInputsProduceDeterministicDrivingAndDifficultyChangesPace() {
        double easy=straight(BotDifficulty.EASY), hard=straight(BotDifficulty.HARD);
        assertTrue(hard>easy+10);assertEquals(hard,straight(BotDifficulty.HARD));
    }
    private double straight(BotDifficulty level) {
        var bot=driver();var state=start();
        for(int i=0;i<200;i++) {
            var d=bot.update(state,new KartBotDriver.Point(0,0,1000),null,level,floor,List.of());
            state=KartPhysics.tick(state,d.input(),1,floor);
            assertTrue(state.speed()<=KartPhysics.MAX_SPEED);
        }
        return state.z();
    }
}
