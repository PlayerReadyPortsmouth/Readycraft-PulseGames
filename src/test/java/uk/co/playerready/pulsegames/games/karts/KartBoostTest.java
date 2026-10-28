package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartBoostTest {
 @Test void largestActiveFactorWinsAndSourcesNeverStack() {
  var boost=new KartBoost();
  boost.apply(KartBoost.Source.TURBO,1.55,0,40);boost.apply(KartBoost.Source.DRIFT,1.40,0,20);
  assertEquals(1.55,boost.multiplier(10));
  boost.apply(KartBoost.Source.PANEL,1.70,0,10);
  assertEquals(1.70,boost.multiplier(5));assertEquals(1.55,boost.multiplier(10));assertEquals(1,boost.multiplier(40));
 }
 @Test void reapplyingExtendsByMaximumNotAddition() {
  var boost=new KartBoost();
  boost.apply(KartBoost.Source.TURBO,1.55,0,40);boost.apply(KartBoost.Source.TURBO,1.40,10,40);
  assertEquals(50,boost.until());assertEquals(1.55,boost.multiplier(45));
  boost.apply(KartBoost.Source.TURBO,1.55,20,5);assertEquals(50,boost.until());
 }
 @Test void expiredSourceRestartsWithItsNewFactorAndClearEmptiesEverything() {
  var boost=new KartBoost();boost.apply(KartBoost.Source.DRIFT,1.70,0,10);
  boost.apply(KartBoost.Source.DRIFT,1.40,10,10);assertEquals(1.40,boost.multiplier(12));
  boost.clear();assertEquals(0,boost.until());assertFalse(boost.active(12));
  boost.apply(KartBoost.Source.STAR,1.2,0,200);boost.clear(KartBoost.Source.STAR);assertFalse(boost.active(1));
 }
 @Test void ignoresNonBoostsAndNonPositiveDurations() {
  var boost=new KartBoost();boost.apply(KartBoost.Source.PANEL,1,0,10);boost.apply(KartBoost.Source.PANEL,Double.NaN,0,10);boost.apply(KartBoost.Source.PANEL,1.5,0,0);
  assertFalse(boost.active(1));
 }
}
