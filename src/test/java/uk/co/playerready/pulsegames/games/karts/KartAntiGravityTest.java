package uk.co.playerready.pulsegames.games.karts;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartAntiGravityTest {
 static KartArcadeConfig.Pt p(double x,double y,double z){return new KartArcadeConfig.Pt(x,y,z);}
 static double d(KartArcadeConfig.Pt a,KartArcadeConfig.Pt b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
 @Test void ribbonStartsAndEndsOnTheRailAndPassesThroughEveryControlPoint() {
  var rail=List.of(p(0,0,0),p(10,2,0),p(20,8,5),p(30,12,5));
  var ribbon=KartAntiGravity.ribbon(rail,1.5);
  assertEquals(rail.get(0),ribbon.get(0));
  assertTrue(d(rail.get(3),ribbon.get(ribbon.size()-1))<1e-9);
  for(var control:rail)assertTrue(ribbon.stream().anyMatch(q->d(q,control)<1e-9),"through "+control);
 }
 @Test void ribbonSamplesAreCloseTogetherSoNoGlowingGaps() {
  var ribbon=KartAntiGravity.ribbon(List.of(p(0,0,0),p(20,0,0),p(20,0,20)),1.5);
  for(int i=1;i<ribbon.size();i++)assertTrue(d(ribbon.get(i-1),ribbon.get(i))<=1.6,"gap at "+i);
  assertTrue(ribbon.size()>25);
 }
 @Test void degenerateRailsAreSafe() {
  assertTrue(KartAntiGravity.ribbon(List.of(),1.5).isEmpty());
  assertEquals(1,KartAntiGravity.ribbon(List.of(p(1,2,3)),1.5).size());
  assertTrue(KartAntiGravity.ribbon(List.of(p(0,0,0),p(5,0,0)),0).isEmpty());
  assertTrue(KartAntiGravity.ribbon(List.of(p(0,0,0),p(5,0,0)),Double.NaN).isEmpty());
 }
 @Test void numbersMatchTheDesignNote() {
  assertEquals(1.10,KartAntiGravity.SPEED_FACTOR);assertEquals(1.30,KartAntiGravity.BUMP_FACTOR);
  assertEquals(15,KartAntiGravity.BUMP_TICKS);
 }
}
