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
 @Test void normalAndHardEarnBoostOnlyThroughSharedChargeAndSafeRelease() {
  for(var level:List.of(BotDifficulty.NORMAL,BotDifficulty.HARD)) {
   var bot=driver();var drift=new KartDrift();var state=start();
   var target=new KartBotDriver.Point(-10,0,6);var next=new KartBotDriver.Point(-20,0,12);
   int chargeTicks=0;boolean released=false;
   for(int tick=0;tick<200;tick++) {
    var d=bot.update(state,target,next,level,floor,List.of());assertFalse(d.reset());
    var c=drift.update(d.drift(),d.input(),state,false);
    if(d.drift())chargeTicks++;
    if(c.boostTicks()>0) {
     assertFalse(d.drift());assertTrue(chargeTicks>=12);assertTrue(d.input().throttle()>0);
     var boosted=KartPhysics.tick(state,c.input(),1.55,floor,KartStyle.BALANCED,c.slipDegrees());
     assertTrue(boosted.grounded());assertTrue(boosted.speed()>=state.speed());released=true;break;
    }
    state=KartPhysics.tick(state,c.input(),1,floor,KartStyle.BALANCED,c.slipDegrees());
   }
   assertTrue(released,"Expected earned shared boost for "+level);
  }
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
}
