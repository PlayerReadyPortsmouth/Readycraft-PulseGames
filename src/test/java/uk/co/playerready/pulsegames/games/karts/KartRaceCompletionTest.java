package uk.co.playerready.pulsegames.games.karts;
import java.util.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;
import static org.mockito.Mockito.*;
class KartRaceCompletionTest {
 GameInstance game;PulseKartsGame logic;Player a,b;
 @BeforeEach void setup() throws Exception {
  game=mock(GameInstance.class,RETURNS_DEEP_STUBS);when(game.state()).thenReturn(GameState.RUNNING);
  logic=new PulseKartsGame(game);a=racer("A");b=racer("B");when(game.players()).thenReturn(List.of(a,b));
  var m=PulseKartsGame.class.getDeclaredMethod("initialiseResults",List.class);m.setAccessible(true);m.invoke(logic,List.of(a,b));
 }
 Player racer(String name) throws Exception {
  var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getName()).thenReturn(name);
  var kart=new PulseKartsGame.Kart(p,mock(ArmorStand.class),()->{},new KartPhysics.State(0,64,0,0,0,0,true));
  var f=PulseKartsGame.class.getDeclaredField("karts");f.setAccessible(true);((Map<UUID,PulseKartsGame.Kart>)f.get(logic)).put(p.getUniqueId(),kart);return p;
 }
 void field(String name,Object value)throws Exception {var f=PulseKartsGame.class.getDeclaredField(name);f.setAccessible(true);f.set(logic,value);}
 void complete()throws Exception {var m=PulseKartsGame.class.getDeclaredMethod("completeRaceTick");m.setAccessible(true);m.invoke(logic);}
 @Test void firstFinisherSpectatesAndLastClosesRaceOnce()throws Exception {
  field("tick",10L);logic.onFinish(a);complete();verify(game).finishParticipant(a);verify(game,never()).end(anyList(),anyString());
  field("tick",11L);logic.onFinish(b);complete();complete();verify(game).end(List.of(a),"race-complete");
 }
 @Test void sameTickEntryFractionsBeatIterationOrder()throws Exception {
  field("tick",10L);field("finishFraction",.8);logic.onFinish(a);field("finishFraction",.2);logic.onFinish(b);complete();
  verify(game).end(List.of(b),"race-complete");
 }
 @Test void departedWinnerIsNotReplacedByRunnerUp()throws Exception {
  field("tick",10L);logic.onFinish(a);when(game.players()).thenReturn(List.of(b));logic.onQuit(a);
  field("tick",11L);logic.onFinish(b);complete();verify(game).end(List.of(),"race-complete");
 }
 @Test void timeoutWithoutFinishDrawsAndGraceCreditsRecordedWinner()throws Exception {
  logic.onTimeUp();verify(game).end(List.of(),"time-race");
 }
 @Test void graceEndsWithRemainingRacerUnfinished()throws Exception {
  field("tick",10L);logic.onFinish(a);field("tick",1210L);complete();verify(game).end(List.of(a),"race-complete");
 }
}
