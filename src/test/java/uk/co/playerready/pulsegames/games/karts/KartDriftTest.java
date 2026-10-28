package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartDriftTest {
 private final KartPhysics.State moving = new KartPhysics.State(0,1,0,0,8,0,true);
 private final KartPhysics.Input turn = new KartPhysics.Input(1,1);
 @Test void sustainedDriftRewardsOnlyReleaseAndCannotStackCharge() {
  var drift=new KartDrift();
  for(int i=0;i<50;i++) assertEquals(0,drift.update(true,turn,moving,false).boostTicks());
  assertEquals(30,drift.update(false,turn,moving,false).boostTicks());
  assertEquals(0,drift.update(false,turn,moving,false).boostTicks());
 }
 @Test void briefTapGivesNoBoostButMediumCornerDoes() {
  var drift=new KartDrift();drift.update(true,turn,moving,false);
  assertEquals(0,drift.update(false,turn,moving,false).boostTicks());
  for(int i=0;i<20;i++)drift.update(true,turn,moving,false);
  assertEquals(20,drift.update(false,turn,moving,false).boostTicks());
 }
 @Test void brakingSpinAndDirectionChangeCancelCharge() {
  for(int mode=0;mode<3;mode++) {
   var drift=new KartDrift();for(int i=0;i<50;i++)drift.update(true,turn,moving,false);
   drift.update(true,new KartPhysics.Input(mode==0?-1:1,mode==1?-1:1),moving,mode==2);
   assertEquals(0,drift.update(false,turn,moving,false).boostTicks());
  }
 }
 @Test void collisionCancelCannotRearmUntilRelease() {
  var drift=new KartDrift();for(int i=0;i<50;i++)drift.update(true,turn,moving,false);
  drift.cancelCharge();for(int i=0;i<50;i++)drift.update(true,turn,moving,false);
  assertEquals(0,drift.update(false,turn,moving,false).boostTicks());
 }
 @Test void stationaryAndAirborneCannotCharge() {
  for(var motion:new KartPhysics.State[]{new KartPhysics.State(0,1,0,0,0,0,true),new KartPhysics.State(0,1,0,0,8,0,false)}) {
   var drift=new KartDrift();for(int i=0;i<80;i++)drift.update(true,turn,motion,false);
   assertEquals(0,drift.update(false,turn,moving,false).boostTicks());
  }
 }
 @Test void tiersArriveAtOneTwoPointFiveAndFourSecondsWithIncreasingBoost() {
  int[][] expect={{19,0,1},{20,20,1},{49,20,1},{50,30,2},{79,30,2},{80,40,3},{300,40,3}};
  double lastFactor=1;
  for(int[] row:expect) {
   var drift=new KartDrift();for(int i=0;i<row[0];i++)drift.update(true,turn,moving,false);
   assertEquals(row[2]==1&&row[1]==0?0:row[2],drift.tier(),"tier after "+row[0]);
   var release=drift.update(false,turn,moving,false);
   assertEquals(row[1],release.boostTicks(),"boost after "+row[0]);
   if(row[1]>0){assertTrue(release.boostFactor()>=lastFactor);lastFactor=release.boostFactor();}
   else assertEquals(1,release.boostFactor());
  }
  assertEquals(1.40,KartDrift.TIER_BOOST_FACTOR[0]);assertEquals(1.70,KartDrift.TIER_BOOST_FACTOR[2]);
 }
}
