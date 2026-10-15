package uk.co.playerready.pulsegames.games.blockparty;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DanceFloorTest {
    @Test void fourPatternBagIsVariedBoundedAndNeverRepeats() {
        var random=new Random(7); DanceFloor.Pattern previous=null;var seen=new HashSet<DanceFloor.Pattern>();
        for(int round=1;round<100;round++) {
            var layout=DanceFloor.create(24,24,round,false,previous,random);
            assertNotEquals(previous,layout.pattern());seen.add(layout.pattern());previous=layout.pattern();
            assertEquals(576,layout.colors().length);
            assertTrue(Arrays.stream(layout.colors()).allMatch(c->c>=0&&c<10));
            int same=0;for(int x=0;x<23;x++)for(int z=0;z<24;z++)if(layout.colors()[x*24+z]==layout.colors()[(x+1)*24+z])same++;
            assertTrue(same>200,"Connected patches, not single-block noise");
        }
        assertEquals(4,seen.size());
        assertThrows(IllegalArgumentException.class,()->DanceFloor.create(65,65,1,false,null,random));
    }
    @Test void tinyFloorCallsOnlyRealColourAndAllowsUnavoidableRepeat() {
        var call=DanceFloor.choose(1,1,new int[]{4},new boolean[]{true},List.of(0),2,4,new Random(1));
        assertEquals(4,call.color());assertEquals(2,call.seconds());
    }
    @Test void impossibleDisconnectedPlayersNeverGetAnUnreachableCall() {
        assertNull(DanceFloor.choose(3,1,new int[]{0,0,1},new boolean[]{true,false,true},List.of(0,2),2,-1,new Random(1)));
    }
    @Test void commonColourWorksAcrossDisconnectedIslandsAndAvoidsRepeat() {
        var call=DanceFloor.choose(5,1,new int[]{0,1,0,1,0},new boolean[]{true,true,false,true,true},List.of(0,4),2,0,new Random(1));
        assertEquals(1,call.color());
    }
    @Test void hundredsOfPatternsAdmitRealReachableCallsForOppositeCorners() {
        boolean[] clear=new boolean[576];Arrays.fill(clear,true);
        for(int seed=0;seed<200;seed++) {
            var random=new Random(seed);var layout=DanceFloor.create(24,24,12,true,null,random);
            var call=DanceFloor.choose(24,24,layout.colors(),clear,List.of(0,23,552,575),1,-1,random);
            assertNotNull(call,"seed "+seed);assertTrue(call.seconds()>=1&&call.seconds()<=10);
            assertTrue(Arrays.stream(layout.colors()).anyMatch(c->c==call.color()));
        }
    }
}
