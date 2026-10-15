package uk.co.playerready.pulsegames.games.karts;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class KartBotRouteTest {
    private Map<String,Object> settings() {
        Map<String,Object> s=new HashMap<>();
        s.put("bot-waypoint1","0,64,0");s.put("bot-waypoint2","0,64,20");
        s.put("bot-waypoint3","20,64,20");s.put("bot-waypoint4","20,64,0");
        s.put("checkpoint-spawn1","0,64,12");s.put("checkpoint-spawn2","12,64,20");return s;
    }
    private Map<String,Cuboid> gates() {return Map.of("checkpoint1",new Cuboid(-1,63,10,1,66,11),
        "checkpoint2",new Cuboid(10,63,19,11,66,21),"finish",new Cuboid(9,63,-1,10,66,1));}
    @Test void validatesClosedCircuitAndGateRecoveryTargets() {
        var route=KartBotRoute.load(settings(),gates(),null);
        assertEquals(4,route.points().size());assertEquals(1,route.gateWaypoint(0));
        assertEquals(2,route.gateWaypoint(1));assertEquals(0,route.gateWaypoint(2));
        route.points().get(0).setX(500);assertEquals(0,route.points().get(0).getX());
    }
    @Test void missingRouteDoesNotQualifyForBots() {assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(Map.of(),gates(),null));}
    @Test void gapNonFiniteAndMalformedNumberAreRejected() {
        var s=settings();s.remove("bot-waypoint2");assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(s,gates(),null));
        s.put("bot-waypoint2","NaN,64,20");assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(s,gates(),null));
        s.put("bot-waypoint2","0,64,20");s.put("bot-waypoint01","1,64,1");assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(s,gates(),null));
    }
    @Test void skippedAndReversedGatesAreRejected() {
        var g=new HashMap<>(gates());g.put("checkpoint1",gates().get("checkpoint2"));g.put("checkpoint2",gates().get("checkpoint1"));
        assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(settings(),g,null));
        g.put("checkpoint1",new Cuboid(100,63,100,101,65,101));assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(settings(),g,null));
    }
    @Test void gateReentryAndMissingRecoveryAreRejected() {
        var missing=settings();missing.remove("checkpoint-spawn1");assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(missing,gates(),null));
        var s=settings();s.put("bot-waypoint5","0,64,20");var finalSettings=s;
        assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(finalSettings,gates(),null));
    }
    @Test void recoveryMustFollowGateOnItsOwnRouteSegment() {
        var s=settings();s.put("checkpoint-spawn1","18,64,12");
        assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(s,gates(),null));
        s.put("checkpoint-spawn1","0,64,5");assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(s,gates(),null));
        s.put("checkpoint-spawn1","0,64,22");assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(s,gates(),null));
    }
    @Test void longUnreviewedSegmentsAreRejected() {
        var s=settings();s.put("bot-waypoint3","100,64,20");
        assertThrows(IllegalArgumentException.class,()->KartBotRoute.load(s,gates(),null));
    }
}
