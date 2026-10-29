package uk.co.playerready.pulsegames.games.karts;
import java.util.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;
class KartBotDriftTest {
 final KartPhysics.CollisionSpace floor=(x,y,z,w,h)->y>=-.00001;
 KartBotDriver driver(){return new KartBotDriver(new UUID(1,2));}
 KartPhysics.State start(){return new KartPhysics.State(0,0,0,0,6,0,true);}
 @Test void difficultyAndHazardsControlDrift() {
  var target=new KartBotDriver.Point(-20,0,30);var following=new KartBotDriver.Point(-40,0,60);
  assertFalse(driver().update(start(),target,following,BotDifficulty.EASY,floor,List.of()).drift());
  assertTrue(driver().update(start(),target,following,BotDifficulty.NORMAL,floor,List.of()).drift());
  KartPhysics.CollisionSpace edge=(x,y,z,w,h)->z>1 || y>=-.00001;
  assertFalse(driver().update(start(),target,following,BotDifficulty.HARD,edge,List.of()).drift());
  var traffic=new KartPhysics.State(0,0,2,0,0,0,true);
  assertFalse(driver().update(start(),target,following,BotDifficulty.HARD,floor,List.of(traffic)).drift());
  var airborne=new KartPhysics.State(0,2,0,0,6,-1,false);
  assertFalse(driver().update(airborne,target,following,BotDifficulty.HARD,floor,List.of()).drift());
 }
 @Test void sharedDriftSimulationCanCompleteRouteWithoutReset() {
  var points=List.of(new KartBotDriver.Point(0,0,25),new KartBotDriver.Point(-25,0,25),new KartBotDriver.Point(-25,0,0),new KartBotDriver.Point(0,0,0));
  for(var level:BotDifficulty.values())for(var style:KartStyle.values()) {
   var bot=driver();var drift=new KartDrift();var state=new KartPhysics.State(0,0,0,0,0,0,true);int reached=0,boost=0;boolean held=false;
   for(int tick=0;tick<2400 && reached<8;tick++) {
    var target=points.get(reached%4);if(Math.hypot(state.x()-target.x(),state.z()-target.z())<KartBotDriver.ARRIVAL_RADIUS){reached++;target=points.get(reached%4);}
    var d=bot.update(state,target,points.get((reached+1)%4),level,floor,List.of(),style);assertFalse(d.reset());held|=d.drift();
    var c=drift.update(d.drift(),d.input(),state,false);boost=Math.max(boost,c.boostTicks());
    state=KartPhysics.tick(state,c.input(),boost>0?1.55:1,floor,style,c.slipDegrees());if(boost>0)boost--;
   }
   assertEquals(8,reached,level+" "+style);if(level==BotDifficulty.EASY)assertFalse(held);
  }
 }
 @Test void driftBoostsAreNeverUnearnedAndBotsStillDriftThroughCorners() {
  // Mini-turbo now needs a full second of held drift, which bots rarely sustain: they keep drifting, but any boost
  // they do get must come from the shared charge and a safe release.
  var corners=List.of(new KartBotDriver.Point[]{new KartBotDriver.Point(-10,0,6),new KartBotDriver.Point(-20,0,12)},
    new KartBotDriver.Point[]{new KartBotDriver.Point(-30,0,20),new KartBotDriver.Point(-60,0,20)});
  int drifted=0;
  for(var level:List.of(BotDifficulty.NORMAL,BotDifficulty.HARD)) for(var corner:corners) {
   var bot=driver();var drift=new KartDrift();var state=start();int run=0;
   for(int tick=0;tick<200;tick++) {
    var d=bot.update(state,corner[0],corner[1],level,floor,List.of());assertFalse(d.reset());
    var c=drift.update(d.drift(),d.input(),state,false);
    if(d.drift()){run++;drifted++;}
    if(c.boostTicks()>0) {
     assertFalse(d.drift());assertTrue(run>=KartDrift.TIER_CHARGE[0],"unearned boost after "+run+" ticks");assertTrue(d.input().throttle()>0);
     assertTrue(KartPhysics.tick(state,c.input(),c.boostFactor(),floor,KartStyle.BALANCED,c.slipDegrees()).grounded());
    }
    if(!d.drift())run=0;
    state=KartPhysics.tick(state,c.input(),1,floor,KartStyle.BALANCED,c.slipDegrees());
   }
  }
  assertTrue(drifted>0,"bots still drift");
 }
 @Test void collisionQueryBudgetAndFreshHazardCheckDuringPreviewReuse() {
  var calls=new java.util.concurrent.atomic.AtomicInteger();var dangerous=new java.util.concurrent.atomic.AtomicBoolean();
  KartPhysics.CollisionSpace counted=(x,y,z,w,h)->{calls.incrementAndGet();return dangerous.get() ? z>1 || y>=-.00001 : y>=-.00001;};
  var bot=driver();var target=new KartBotDriver.Point(-10,0,6);var next=new KartBotDriver.Point(-20,0,12);
  assertTrue(bot.update(start(),target,next,BotDifficulty.HARD,counted,List.of()).drift());
  int first=calls.getAndSet(0);assertTrue(first<=900,"Initial collision-query budget: "+first);
  assertTrue(bot.update(start(),target,next,BotDifficulty.HARD,counted,List.of()).drift());
  int reused=calls.getAndSet(0);assertTrue(reused<first,"Stable preview should avoid full re-simulation");
  dangerous.set(true);assertFalse(bot.update(start(),target,next,BotDifficulty.HARD,counted,List.of()).drift());
  assertTrue(calls.get()<=900);
 }
 @Test void unsafeReleaseCancelsSharedCharge() {
  var bot=driver();var drift=new KartDrift();var target=new KartBotDriver.Point(-20,0,30);var next=new KartBotDriver.Point(-40,0,60);
  var d=bot.update(start(),target,next,BotDifficulty.HARD,floor,List.of());assertTrue(d.drift());
  for(int i=0;i<30;i++)drift.update(true,d.input(),start(),false);
  KartPhysics.CollisionSpace edge=(x,y,z,w,h)->z>1 || y>=-.00001;
  d=bot.update(start(),target,next,BotDifficulty.HARD,edge,List.of());assertFalse(d.drift());
  assertEquals(0,drift.update(d.drift(),d.input(),start(),false).boostTicks());
  bot.reset();assertEquals(0,bot.stuckTicks());
 }
 /** Holds a steady bend ahead of the kart, as a long sweeper does, and reports the longest drift. */
 int longestDrift(KartBotDriver bot,BotDifficulty level,boolean moveTarget) {
  var drift=new KartDrift();var state=new KartPhysics.State(0,0,0,0,8,0,true);int run=0,best=0;
  KartBotDriver.Point target=null;
  for(int tick=0;tick<160;tick++) {
   double yaw=Math.toRadians(state.yaw()+55);
   if(target==null||(moveTarget&&tick%10==0)||!moveTarget&&tick%40==0)
    target=new KartBotDriver.Point(state.x()-Math.sin(yaw)*16,0,state.z()+Math.cos(yaw)*16);
   double next=Math.toRadians(state.yaw()+110);
   var following=new KartBotDriver.Point(target.x()-Math.sin(next)*16,0,target.z()+Math.cos(next)*16);
   var d=bot.update(state,target,following,level,floor,List.of());
   run=d.drift()?run+1:0;best=Math.max(best,run);
   var c=drift.update(d.drift(),d.input(),state,false);
   state=KartPhysics.tick(state,c.input(),c.boostTicks()>0?c.boostFactor():1,floor,KartStyle.BALANCED,c.slipDegrees());
  }
  return best;
 }
 @Test void normalHoldsALongDriftAndASteadyBendDoesNotCancelItWhenTheTargetMoves() {}
}
