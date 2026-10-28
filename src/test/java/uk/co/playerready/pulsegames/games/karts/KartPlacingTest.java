package uk.co.playerready.pulsegames.games.karts;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartPlacingTest {
 final UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
 @Test void progressFirstThenNearerTheNextGate() {
  var entries=List.of(new KartPlacing.Entry(a,5,40),new KartPlacing.Entry(b,5,12),new KartPlacing.Entry(c,7,90));
  assertEquals(List.of(c,b,a),KartPlacing.order(entries));
  assertEquals(1,KartPlacing.place(c,entries));assertEquals(3,KartPlacing.place(a,entries));
 }
 @Test void tiesAndNonFiniteDistancesAreDeterministic() {
  var tie=List.of(new KartPlacing.Entry(b,2,Double.NaN),new KartPlacing.Entry(a,2,Double.NaN));
  assertEquals(KartPlacing.order(tie),KartPlacing.order(List.of(tie.get(1),tie.get(0))));
  assertEquals(List.of(a,b),KartPlacing.order(List.of(new KartPlacing.Entry(a,2,3),new KartPlacing.Entry(b,2,Double.POSITIVE_INFINITY))));
 }
 @Test void unknownRacerGetsLastPlaceInTheField() {
  assertEquals(2,KartPlacing.place(c,List.of(new KartPlacing.Entry(a,1,1),new KartPlacing.Entry(b,1,2))));
  assertEquals(1,KartPlacing.place(c,List.of()));
 }
}
