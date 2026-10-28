package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartAirTest {
 KartPhysics.State ground(double speed){return new KartPhysics.State(0,0,0,0,speed,0,true);}
 @Test void launchSpeedGrowsWithSpeedAndPowerAndIsBounded() {
  assertEquals(5,KartAir.launchSpeed(0,1),1e-9);assertEquals(10,KartAir.launchSpeed(12,1),1e-9);
  assertEquals(12.5,KartAir.launchSpeed(99,1),1e-9);assertEquals(5,KartAir.launchSpeed(12,.5),1e-9);
  assertEquals(15,KartAir.launchSpeed(12,1.5),1e-9);assertEquals(5,KartAir.launchSpeed(Double.NaN,Double.NaN),1e-9);
  assertTrue(KartAir.launchSpeed(6,1)<KartAir.launchSpeed(12,1));
 }
 @Test void launchNeedsGroundAndSpeedAndCannotRestartAFlight() {
  var air=new KartAir();
  assertEquals(ground(2),air.launch(ground(2),1));assertFalse(air.flying());
  assertFalse(air.launch(new KartPhysics.State(0,5,0,0,12,0,false),1).verticalSpeed()>0);
  var up=air.launch(ground(12),1);assertTrue(air.flying());assertEquals(10,up.verticalSpeed(),1e-9);assertFalse(up.grounded());
  assertEquals(0,air.launch(ground(12),1).verticalSpeed(),"second launch while flying is ignored");
 }
 @Test void trickNeedsFourAirTicksOnlyOnceAndAwardsOnlyOnLanding() {
  var air=new KartAir();air.launch(ground(12),1);
  assertFalse(air.tick(false,true).trickStarted(),"too early");
  air.tick(false,false);air.tick(false,false);
  var start=air.tick(false,true);assertTrue(start.trickStarted());assertEquals(30,start.spinDegrees(),1e-9);
  assertFalse(air.tick(false,true).trickStarted(),"one trick per flight");
  KartAir.Step last=null;for(int i=0;i<10;i++)last=air.tick(false,false);
  assertEquals(0,last.spinDegrees(),1e-9,"spin completes back to the true heading");
  var land=air.tick(true,false);assertTrue(land.landed());assertTrue(land.landedWithTrick());assertEquals(0,land.spinDegrees());
  assertFalse(air.flying());
 }
 @Test void landingWithoutATrickGivesNoBonusAndFirstTouchingTickIsNotALanding() {
  var air=new KartAir();air.launch(ground(12),1);
  assertFalse(air.tick(true,false).landed(),"still on the pad block");
  for(int i=0;i<5;i++)air.tick(false,false);
  var land=air.tick(true,false);assertTrue(land.landed());assertFalse(land.landedWithTrick());
 }
 @Test void airTimeIsCappedSoNobodyFliesOutOfTheArena() {
  var air=new KartAir();air.launch(ground(12),1);
  for(int i=1;i<=KartAir.MAX_AIR_TICKS;i++)assertFalse(air.tick(false,false).capped(),"tick "+i);
  assertTrue(air.tick(false,false).capped());
 }
 @Test void nonRampFlightsAndCancelledFlightsNeverTrick() {
  var air=new KartAir();assertEquals(KartAir.Step.IDLE,air.tick(false,true));
  air.launch(ground(12),1);air.cancel();assertEquals(KartAir.Step.IDLE,air.tick(false,true));
 }
}
