package uk.co.playerready.pulsegames.games.karts;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KartRaceResultsTest {
    final UUID first=new UUID(0,1), second=new UUID(0,2), third=new UUID(0,3);
    KartRaceResults race(int grace){return new KartRaceResults(List.of(first,second,third),grace);}
    @Test void firstFinishStartsDefaultGraceNotImmediateEnd() {
        var race=new KartRaceResults(List.of(first,second));
        assertFalse(race.shouldEnd(1000));assertTrue(race.finish(first,100,.8));
        assertFalse(race.shouldEnd(100));assertEquals(30,race.remainingSeconds(100));
        assertFalse(race.shouldEnd(699));assertEquals(1,race.remainingSeconds(699));assertTrue(race.shouldEnd(700));assertFalse(race.finish(second,700,0));
    }
    @Test void sameTickCrossingsUseFractionNotControllerIterationOrder() {
        var race=race(30);race.finish(first,100,.9);race.finish(second,100,.1);race.finish(third,101,0);
        assertEquals(List.of(second,first,third),race.results().stream().map(KartRaceResults.Result::player).toList());
        assertEquals(second,race.winner().orElseThrow());assertEquals(List.of(second),race.winners());assertTrue(race.shouldEnd(101));
    }
    @Test void exactTieIsVisibleAndOnlyTiedFirstPlaceReceivesWin() {
        var race=race(30);race.finish(second,100,.5);race.finish(first,100,.5);race.finish(third,101,.1);
        assertEquals(first,race.winner().orElseThrow());assertEquals(List.of(first,second),race.winners());
        assertEquals(List.of(1,1,3),race.results().stream().map(KartRaceResults.Result::place).toList());
        assertTrue(race.results().get(0).tied());assertTrue(race.results().get(1).tied());assertFalse(race.results().get(2).tied());
    }
    @Test void duplicateAndDepartedFinishersCannotRewriteResultsOrPromoteRunnerUp() {
        var race=race(30);assertTrue(race.finish(first,100,.5));assertFalse(race.finish(first,99,0));race.depart(first);
        race.finish(second,102,.2);race.depart(third);
        assertEquals(first,race.winner().orElseThrow());assertEquals(2,race.results().size());assertTrue(race.shouldEnd(102));
        assertFalse(race.finish(third,103,0));assertThrows(UnsupportedOperationException.class,()->race.results().clear());
    }
    @Test void graceIsClampedAndDeadlineDoesNotOverflow() {
        var low=race(Integer.MIN_VALUE);low.finish(first,10,0);assertFalse(low.shouldEnd(109));assertTrue(low.shouldEnd(110));
        var high=race(Integer.MAX_VALUE);high.finish(first,Long.MAX_VALUE-100,0);assertEquals(1150,high.remainingTicks(Long.MAX_VALUE-50));
        assertFalse(high.shouldEnd(Long.MAX_VALUE));
    }
    @Test void invalidCrossingsNeverStartTimerAndNoFinishMeansNoWinner() {
        var race=race(30);assertThrows(IllegalArgumentException.class,()->race.finish(first,0,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->race.finish(first,-1,0));
        assertThrows(IllegalArgumentException.class,()->race.finish(first,0,1.1));
        race.depart(first);race.depart(second);race.depart(third);
        assertFalse(race.hasFinishers());assertTrue(race.winner().isEmpty());assertFalse(race.shouldEnd(10000));
    }
}
