package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
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
    @Test void eachTierReachesItsFractionOfTheKartMaximumOnAStraight() {
        double[] fraction={.78,.90,.98};
        for(var level:BotDifficulty.values()) {
            var bot=driver();var state=start();
            double sum=0;
            for(int i=0;i<400;i++) {
                state=KartPhysics.tick(state,bot.update(state,new KartBotDriver.Point(0,0,2000),null,level,floor,List.of()).input(),1,floor);
                if(i>=200)sum+=state.speed();
            }
            double want=fraction[level.ordinal()]*KartPhysics.MAX_SPEED;
            assertEquals(want,sum/200,.35,level.toString());
        }
    }
    @Test void cornersSlowByTheSofterTable() {
        double[][] table={{8,5.5,3},{8,5.5,3},{8,5.5,3}};double[] scale={.85,1,1.1};double[] bend={35,70,115};
        for(var level:BotDifficulty.values()) for(int row=0;row<3;row++) {
            double limit=table[level.ordinal()][row]*scale[level.ordinal()];double a=Math.toRadians(bend[row]);
            var target=new KartBotDriver.Point(-Math.sin(a)*40,0,Math.cos(a)*40);
            var fast=driver().update(new KartPhysics.State(0,0,0,0,limit+.6,0,true),target,null,level,floor,List.of());
            var slow=driver().update(new KartPhysics.State(0,0,0,0,limit-.6,0,true),target,null,level,floor,List.of());
            assertTrue(fast.input().throttle()<=0,level+" bend "+bend[row]+" over the limit "+limit);
            assertTrue(slow.input().throttle()>=0 && limit-.6<KartPhysics.MAX_SPEED,level+" bend "+bend[row]+" under the limit");
        }
    }
    @Test void rubberBandIsBoundedMonotonicTierScaledAndNeverPunishesALoneLeader() {
        var tuning=KartBotTuning.DEFAULT;
        for(var level:BotDifficulty.values()) {
            double previous=Double.MAX_VALUE;
            for(double gap=-400;gap<=150;gap+=5) {
                double pace=tuning.pace(gap,true,level);
                assertTrue(pace>=.92-1e-9 && pace<=1.08+1e-9,level+" "+gap);
                assertTrue(pace<=previous+1e-9,"non-increasing as the bot gets further ahead: "+level+" "+gap);
                previous=pace;
            }
            assertEquals(1,tuning.pace(0,true,level),1e-9);
            assertEquals(1,tuning.pace(-300,false,level),"no human, no banding");
            assertEquals(1,tuning.pace(400,true,level),"no human nearby: a runaway leader keeps full pace");
        }
        assertTrue(tuning.pace(-200,true,BotDifficulty.EASY)>tuning.pace(-200,true,BotDifficulty.HARD),"easy gets more help");
        assertTrue(tuning.pace(120,true,BotDifficulty.HARD)<tuning.pace(120,true,BotDifficulty.EASY),"hard gets more pressure");
        var off=new KartBotTuning(yaml("karts:\n  rubber-band:\n    enabled: false\n","karts"));
        assertEquals(1,off.pace(-200,true,BotDifficulty.EASY));
    }
    private static org.bukkit.configuration.ConfigurationSection yaml(String text,String path) {
        var config=new YamlConfiguration();
        try { config.loadFromString(text); } catch(Exception ex) { throw new IllegalStateException(ex); }
        return config.getConfigurationSection(path);
    }
    @Test void tuningFallsBackToDefaultsAndClampsNonsense() {
        var zero=new KartBotTuning(new YamlConfiguration());
        assertTrue(zero.strongItemsWhenBehind);assertEquals(.9,zero.speedFraction[1]);assertEquals(1.5,zero.laneWidth);
        var wild=new KartBotTuning(yaml("a:\n  lane-width: 9\n  speed-fraction:\n    hard: 7\n  rubber-band:\n    max-bonus: 3\n  strong-items-when-behind: false\n","a"));
        assertEquals(1.5,wild.laneWidth);assertEquals(1,wild.speedFraction[2]);assertEquals(.15,wild.maxBonus);assertFalse(wild.strongItemsWhenBehind);
    }
    @Test void laneOffsetsStayInsideTheClampAndTheSafeProbesShrinkThem() {
        KartPhysics.CollisionSpace corridor=(x,y,z,w,h)->y>=-.00001 && Math.abs(x)+w/2<=1.3;
        var context=new KartBotDriver.Context(1,false,false);
        boolean varied=false;
        for(int n=0;n<300;n++) for(int lap=1;lap<=4;lap++) {
            var bot=new KartBotDriver(new UUID(n*7919L,n*104729L+lap));
            double offset=bot.laneOffset(lap,KartBotTuning.DEFAULT);
            assertTrue(Math.abs(offset)<=1.5+1e-9);varied|=Math.abs(offset)>.5;
            var target=new KartBotDriver.Point(0,0,40);
            var open=bot.aimPoint(start(),target,null,5,context,floor,List.of(),0,40);
            assertTrue(Math.abs(open.x())<=1.5+1e-9);
            var narrow=bot.aimPoint(start(),target,null,5,context,corridor,List.of(),0,40);
            assertTrue(Math.abs(narrow.x())<=.76,"a lane the probes reject is halved or dropped: "+narrow.x());
        }
        assertTrue(varied,"bots do not all share one line");
    }
    @Test void aBotMovesOverToPassAKartJustAhead() {
        var slow=new KartPhysics.State(.2,0,5.5,0,0,0,true);
        var sides=new java.util.HashSet<Integer>();
        for(int n=0;n<40;n++) {
            var bot=new KartBotDriver(new UUID(n,n*31L));
            var aim=bot.aimPoint(start(),new KartBotDriver.Point(0,0,40),null,2,new KartBotDriver.Context(1,false,false),floor,List.of(slow),0,40);
            sides.add((int)Math.signum(aim.x()));
            assertTrue(aim.x()*1<=1.5+1e-9);
        }
        assertEquals(java.util.Set.of(-1),sides,"the kart sits at +x, so every bot swaps to the other side");
    }
    @Test void pdSteeringConvergesWithoutOscillatingOnASyntheticRoute() {
        var narrow=new KartBotDriver.Context(1,false,false,1,1,new KartBotTuning(yaml("a:\n  lane-width: 0.1\n","a")));
        for(var level:BotDifficulty.values()) {
            var bot=driver();var state=new KartPhysics.State(8,0,0,35,5,0,true);int flips=0,last=0;double error=0;
            var target=new KartBotDriver.Point(0,0,400);
            for(int i=0;i<500;i++) {
                var d=bot.update(state,target,null,level,floor,List.of(),KartStyle.BALANCED,narrow);
                state=KartPhysics.tick(state,d.input(),1,floor);
                double desired=Math.toDegrees(Math.atan2(-(target.x()-state.x()),target.z()-state.z()));
                error=((desired-state.yaw()+540)%360)-180;
                int sign=Math.abs(error)<3?0:(int)Math.signum(error);
                if(sign!=0){if(last!=0&&sign!=last)flips++;last=sign;}
            }
            assertTrue(flips<=1,level+" overshoots at most once, flips="+flips);
            assertTrue(Math.abs(error)<3,level+" ends aligned: "+error);
        }
    }
}
