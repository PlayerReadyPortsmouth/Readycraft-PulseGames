package uk.co.playerready.pulsegames.games.karts;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartItemBoxesTest {
 static KartPhysics.State at(double x,double y,double z){return new KartPhysics.State(x,y,z,0,12,0,true);}
 KartItemBoxes boxes(){return new KartItemBoxes(List.of(new KartArcadeConfig.Pt(0,64,10),new KartArcadeConfig.Pt(0,64,30)));}
 @Test void collectedByProximityWithinOnePointTwoBlocksOnly() {
  var b=boxes();
  assertEquals(-1,b.collect(at(2.5,64,9.9),at(2.5,64,10.1),1),"2.5 blocks to the side is too far");
  assertEquals(-1,b.collect(at(1.4,64,9.9),at(1.4,64,10.1),1),"1.4 blocks is outside the radius");
  assertEquals(0,b.collect(at(1.0,64,9.9),at(1.0,64,10.1),1));
 }
 @Test void aFastKartCannotTunnelThroughThePickupRadius() {
  var b=boxes();
  assertEquals(0,b.collect(at(0,64,9.6),at(0,64,10.4),1),"0.8-block step straddling the box");
  var fast=boxes();
  assertEquals(0,fast.collect(at(0,64,9.0),at(0,64,11.0),1),"sweep passes through the box between ticks");
  assertEquals(-1,boxes().collect(at(0,64,12.0),at(0,64,14.0),1),"stops short");
 }
 @Test void respawnsAfterFourSecondsGloballyWithATenTickFadeIn() {
  var b=boxes();assertEquals(0,b.collect(at(0,64,10),at(0,64,10),100));
  assertFalse(b.available(0,100));assertFalse(b.available(0,179));assertTrue(b.available(0,180));
  assertEquals(-1,b.collect(at(0,64,10),at(0,64,10),150),"nobody can take it while it is away");
  assertEquals(0,b.scale(0,100));assertEquals(0,b.scale(0,169));
  assertEquals(.5,b.scale(0,175),1e-9);assertEquals(1,b.scale(0,180));assertEquals(1,b.scale(1,100));
  assertEquals(0,b.collect(at(0,64,10),at(0,64,10),180));
 }
 @Test void aKartTakesOnlyTheNearestBoxAndHighFlyersMissIt() {
  var close=new KartItemBoxes(List.of(new KartArcadeConfig.Pt(0,64,10),new KartArcadeConfig.Pt(0.5,64,10)));
  assertEquals(1,close.collect(at(0.6,64,10),at(0.6,64,10),1));assertTrue(close.available(0,1));
  assertEquals(-1,boxes().collect(at(0,70,9.9),at(0,70,10.1),1),"six blocks up");
 }
 @Test void resetMakesEveryBoxAvailableAgain() {
  var b=boxes();b.collect(at(0,64,10),at(0,64,10),5);b.reset();assertTrue(b.available(0,5));
 }
}
