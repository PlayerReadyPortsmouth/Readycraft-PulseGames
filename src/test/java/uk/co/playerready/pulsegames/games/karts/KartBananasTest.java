package uk.co.playerready.pulsegames.games.karts;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartBananasTest {
 final UUID owner=new UUID(0,1),other=new UUID(0,2);
 KartPhysics.State kart(double x,double z,float yaw){return new KartPhysics.State(x,64,z,yaw,12,0,true);}
 @Test void dropsBehindTheKartAlongItsHeading() {
  var bananas=new KartBananas();var b=bananas.drop(owner,kart(0,0,0),2.5,0,new ArrayList<>());
  assertEquals(0,b.x(),1e-9);assertEquals(-2.5,b.z(),1e-9);
  var east=bananas.drop(owner,kart(0,0,90),2.5,0,new ArrayList<>());
  assertEquals(2.5,east.x(),1e-9);assertEquals(0,east.z(),1e-9,"yaw 90 faces -x so behind is +x");
 }
 @Test void armsAfterTenTicksAndSparesItsOwnerForTwenty() {
  var bananas=new KartBananas();bananas.drop(owner,kart(0,0,0),2.5,100,new ArrayList<>());
  var at=kart(0,-2.5,0);
  assertNull(bananas.touched(at,at,other,105),"not armed yet");
  assertNotNull(bananas.touched(at,at,other,110));
  assertNull(bananas.touched(at,at,owner,115),"owner is safe for 20 ticks");
  assertNotNull(bananas.touched(at,at,owner,120));
 }
 @Test void aFastKartSweepingPastIsStillHitButASideStepIsNot() {
  var bananas=new KartBananas();bananas.drop(owner,kart(0,0,0),2.5,0,new ArrayList<>());
  assertNotNull(bananas.touched(kart(0,-3.4,0),kart(0,-1.6,0),other,50));
  assertNull(bananas.touched(kart(2.0,-3.4,0),kart(2.0,-1.6,0),other,50),"2 blocks to the side");
  var air=new KartPhysics.State(0,68,-2.5,0,12,0,false);
  assertNull(bananas.touched(air,air,other,50),"flying over it");
 }
 @Test void expiresAfterThirtySecondsAndIsCappedAtTwentyFourLive() {
  var bananas=new KartBananas();bananas.drop(owner,kart(0,0,0),2.5,0,new ArrayList<>());
  assertTrue(bananas.expire(599).isEmpty());assertEquals(1,bananas.expire(600).size());assertTrue(bananas.live().isEmpty());
  var evicted=new ArrayList<KartBananas.Banana>();KartBananas.Banana first=null;
  for(int i=0;i<30;i++){var b=bananas.drop(owner,kart(i*5,0,0),2.5,i,evicted);if(first==null)first=b;}
  assertEquals(24,bananas.live().size());assertEquals(6,evicted.size());assertEquals(first,evicted.get(0),"oldest goes first");
  assertEquals(24,bananas.clear().size());assertTrue(bananas.live().isEmpty());
 }
 @Test void aConsumedBananaIsGone() {
  var bananas=new KartBananas();var b=bananas.drop(owner,kart(0,0,0),2.5,0,new ArrayList<>());
  bananas.remove(b);assertNull(bananas.touched(kart(0,-2.5,0),kart(0,-2.5,0),other,50));
 }
}
