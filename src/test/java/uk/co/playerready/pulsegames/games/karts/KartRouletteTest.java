package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartRouletteTest {
 @Test void runsForExactlyOnePointFiveSeconds() {
  int sum=0;for(int step:KartRoulette.STEPS)sum+=step;
  assertEquals(30,sum);assertEquals(30,KartRoulette.TOTAL_TICKS);
  assertFalse(KartRoulette.done(29));assertTrue(KartRoulette.done(30));
 }
 @Test void iconChangesNeverExceedFiveAHalfSecondSoNothingStrobes() {
  int changes=0,last=-100,minGap=100;
  for(int t=0;t<KartRoulette.TOTAL_TICKS;t++) if(KartRoulette.changesAt(t)){changes++;if(last>=0)minGap=Math.min(minGap,t-last);last=t;}
  assertEquals(5,changes,"five icon flicks between the first icon and the landing");
  assertTrue(minGap>=4,"never faster than 5 Hz, was every "+minGap);
  assertFalse(KartRoulette.changesAt(30),"the landing is not another flick");
 }
 @Test void iconIndexAdvancesWithEachChangeAndWrapsThePool() {
  assertEquals(0,KartRoulette.iconIndex(0,8));assertEquals(0,KartRoulette.iconIndex(3,8));assertEquals(1,KartRoulette.iconIndex(4,8));
  assertEquals(5,KartRoulette.iconIndex(29,8));
  assertEquals(2,KartRoulette.iconIndex(29,3),"five changes wrap a pool of three");
  assertEquals(0,KartRoulette.iconIndex(10,0));
 }
}
