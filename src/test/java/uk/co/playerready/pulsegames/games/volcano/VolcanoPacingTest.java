package uk.co.playerready.pulsegames.games.volcano;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class VolcanoPacingTest {
 @Test void acceleratesWithoutZeroOrNegativeIntervals() {
  assertEquals(4, VolcanoGame.riseInterval(4,30,5));
  assertEquals(3, VolcanoGame.riseInterval(4,30,30));
  assertEquals(2, VolcanoGame.riseInterval(4,30,60));
  assertEquals(1, VolcanoGame.riseInterval(4,30,90));
  assertEquals(1, VolcanoGame.riseInterval(4,30,300));
  assertEquals(1, VolcanoGame.riseInterval(-3,0,10));
 }
 @Test void graceEndsAtFiveSecondsAndFloodDoesNotOverlap() {
  var f=new VolcanoFloodBudgetTest.Fixture();
  f.logic.onSecond(4);verifyNoInteractions(f.world);
  f.logic.onSecond(5);verify(f.world,times(1024)).getBlockAt(anyInt(),eq(64),anyInt());
  f.logic.onSecond(6);verify(f.world,times(1024)).getBlockAt(anyInt(),eq(64),anyInt());
 }
}
