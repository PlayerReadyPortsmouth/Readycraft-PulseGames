package uk.co.playerready.pulsegames.games.karts;
import java.util.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;
class KartBotArcadeContextTest {
 final KartBotDriver.Point target=new KartBotDriver.Point(0,0,60),after=new KartBotDriver.Point(0,0,90);
 KartBotDriver bot(){return new KartBotDriver(new UUID(3,4));}
 KartPhysics.State at(double z,double speed,boolean grounded){return new KartPhysics.State(0,0,z,0,speed,0,grounded);}
 @Test void withoutAContextABotBrakesBeforeAGapButOverAJumpPadItCarriesOn() {
  KartPhysics.CollisionSpace gap=(x,y,z,w,h)->y>=-.00001 || (z>8 && z<14);
  var cautious=bot().update(at(4,8,true),target,after,BotDifficulty.HARD,gap,List.of());
  assertTrue(cautious.input().throttle()<=0,"unsupported ground ahead: it slows down");
  var jumping=bot().update(at(4,8,true),target,after,BotDifficulty.HARD,gap,List.of(),KartStyle.BALANCED,new KartBotDriver.Context(1,true,false));
  assertEquals(1,jumping.input().throttle(),"the gap is the point of a jump pad");assertFalse(jumping.reset());
 }
 @Test void inTheAirABotHoldsThrottleAndSteeringAndNeverDriftsOrResets() {
  KartPhysics.CollisionSpace gap=(x,y,z,w,h)->y>=-.00001 || (z>8 && z<14);
  for(var level:BotDifficulty.values()) {
   var bot=bot();
   for(int i=0;i<200;i++) {
    var d=bot.update(new KartPhysics.State(0,2,10,0,12,-3,false),target,after,level,gap,List.of(),KartStyle.BALANCED,new KartBotDriver.Context(1,false,true));
    assertEquals(1,d.input().throttle());assertEquals(0,d.input().steering());assertFalse(d.drift());assertFalse(d.reset());
   }
   assertEquals(0,bot.stuckTicks(),"flying is not being stuck");
  }
 }
 @Test void aBoostedBotRaisesItsTargetSpeedInsteadOfBrakingAgainstItsOwnBoost() {
  KartPhysics.CollisionSpace floor=(x,y,z,w,h)->y>=-.00001;
  var plain=bot().update(at(0,12,true),target,after,BotDifficulty.EASY,floor,List.of());
  assertEquals(-1,plain.input().throttle(),"12 b/s is over an easy bot's normal ceiling");
  var boosted=bot().update(at(0,12,true),target,after,BotDifficulty.EASY,floor,List.of(),KartStyle.BALANCED,new KartBotDriver.Context(1.4,false,false));
  assertEquals(1,boosted.input().throttle());
  var tight=bot().update(at(0,12,true),new KartBotDriver.Point(40,0,0),after,BotDifficulty.HARD,floor,List.of(),KartStyle.BALANCED,new KartBotDriver.Context(1.4,false,false));
  assertTrue(tight.input().throttle()<=0,"a boost does not make a bot ignore a sharp corner");
 }
 @Test void contextIsClampedSoANonsenseScaleCannotRunAwayTheTargetSpeed() {
  KartPhysics.CollisionSpace floor=(x,y,z,w,h)->y>=-.00001;
  var wild=bot().update(at(0,30,true),target,after,BotDifficulty.HARD,floor,List.of(),KartStyle.BALANCED,new KartBotDriver.Context(99,false,false));
  assertEquals(-1,wild.input().throttle(),"clamped to 2x: 30 b/s is still over the ceiling");
 }
 @Test void paceScaleIsClampedAndTheKartsOwnMaximumIsNeverExceeded() {
  KartPhysics.CollisionSpace floor=(x,y,z,w,h)->y>=-.00001;
  var pushed=bot().update(at(0,12.4,true),target,after,BotDifficulty.HARD,floor,List.of(),KartStyle.BALANCED,new KartBotDriver.Context(1,false,false,5,1,null));
  assertEquals(-1,pushed.input().throttle(),"even at the top pace a bot never aims above MAX_SPEED");
  var trickster=bot().update(at(0,11.5,true),target,after,BotDifficulty.HARD,floor,List.of(),KartStyle.TRICKSTER,new KartBotDriver.Context(1,false,false,1.2,1,null));
  assertEquals(-1,trickster.input().throttle(),"a slower style has a lower ceiling too");
 }
}
