package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartPlaceTrackerTest {
 @Test void firstStablePlaceIsSilentThenGainsAndLossesAnnounceOnlyOnceStable() {
  var tracker=new KartPlaceTracker();
  for(int t=0;t<=30;t+=10)assertEquals(KartPlaceTracker.Change.NONE,tracker.update(5,t),"settling at 5th is silent");
  assertEquals(KartPlaceTracker.Change.NONE,tracker.update(4,40),"just changed");
  assertEquals(KartPlaceTracker.Change.GAINED,tracker.update(4,50));
  assertEquals(KartPlaceTracker.Change.NONE,tracker.update(4,60));
 }
 @Test void aFlickerBetweenPlacesNeverSounds() {
  var tracker=new KartPlaceTracker();
  for(int t=0;t<=30;t+=10)tracker.update(5,t);
  for(int t=40;t<=200;t+=10)assertEquals(KartPlaceTracker.Change.NONE,tracker.update(t%20==0?4:5,t));
 }
 @Test void announcementsAreAtLeastThreeSecondsApart() {
  var tracker=new KartPlaceTracker();
  for(int t=0;t<=30;t+=10)tracker.update(5,t);
  tracker.update(4,40);assertEquals(KartPlaceTracker.Change.GAINED,tracker.update(4,50));
  tracker.update(3,60);assertEquals(KartPlaceTracker.Change.NONE,tracker.update(3,70),"stable but only 20 ticks after the last sound");
  assertEquals(KartPlaceTracker.Change.NONE,tracker.update(3,100));
  assertEquals(KartPlaceTracker.Change.GAINED,tracker.update(3,110));
  tracker.update(6,120);tracker.update(6,130);assertEquals(KartPlaceTracker.Change.NONE,tracker.update(6,140));
  assertEquals(KartPlaceTracker.Change.LOST,tracker.update(6,170));
 }
}
