package uk.co.playerready.pulsegames.games.karts;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartBulletTest {
 final List<KartBotDriver.Point> route=List.of(new KartBotDriver.Point(0,0,0),new KartBotDriver.Point(0,0,20),new KartBotDriver.Point(20,0,20),new KartBotDriver.Point(20,0,0));
 @Test void headsForTheEndOfTheNearestSegment() {
  assertEquals(1,KartBullet.nextWaypoint(route,0.5,5));
  assertEquals(2,KartBullet.nextWaypoint(route,10,20.5));
  assertEquals(3,KartBullet.nextWaypoint(route,20.5,10));
  assertEquals(0,KartBullet.nextWaypoint(route,10,-0.5),"the closing segment wraps to waypoint 1");
 }
 @Test void distanceToRouteLetsTheMatchRefuseABulletFarOffTheLine() {
  assertEquals(0,KartBullet.distanceToRoute(route,0,10),1e-9);
  assertEquals(3,KartBullet.distanceToRoute(route,3,10),1e-9);
  assertTrue(KartBullet.distanceToRoute(route,60,60)>KartBullet.MAX_OFF_ROUTE);
 }
 @Test void numbersMatchTheDesignNote() {
  assertEquals(160,KartBullet.TICKS);assertEquals(1.6,KartBullet.FACTOR);assertEquals(8*20,KartBullet.TICKS);
 }
}
