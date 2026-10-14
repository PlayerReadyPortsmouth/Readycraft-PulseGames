package uk.co.playerready.pulsegames.core.bot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QueueFillPolicyTest {
    @Test void noHumansNeverStartsBotOnlyMatch() { assertEquals(0, QueueFillPolicy.desiredBots(0,2,8,16,24)); }
    @Test void smallArenasFillCompletely() {
        assertEquals(1, QueueFillPolicy.desiredBots(1,2,2,16,24));
        assertEquals(2, QueueFillPolicy.desiredBots(1,2,3,16,24));
        assertEquals(3, QueueFillPolicy.desiredBots(1,2,4,16,24));
    }
    @Test void largerArenasAddHalfCapacityBotsAndRoundDown() {
        assertEquals(2, QueueFillPolicy.desiredBots(1,2,5,16,24));
        assertEquals(4, QueueFillPolicy.desiredBots(1,2,8,16,24));
        assertEquals(6, QueueFillPolicy.desiredBots(2,2,12,16,24));
        assertEquals(9, QueueFillPolicy.desiredBots(2,2,19,16,24));
    }
    @Test void largerMapMinimumNeverOverridesHalf() {
        assertEquals(0, QueueFillPolicy.desiredBots(1,8,12,16,24));
        assertEquals(3, QueueFillPolicy.desiredBots(1,4,4,16,24));
    }
    @Test void hardCapsNeverCreateAnUnstartablePartialRoster() {
        assertEquals(0, QueueFillPolicy.desiredBots(1,8,16,7,6));
        assertEquals(0, QueueFillPolicy.desiredBots(2,8,4,7,24));
        assertEquals(3, QueueFillPolicy.desiredBots(1,2,16,3,24));
        assertEquals(2, QueueFillPolicy.desiredBots(1,2,16,16,2));
    }
    @Test void everyRosterRespectsBotSeatLimitFreeSeatsAndMinimum() {
        for (int maximum = 2; maximum <= 64; maximum++) {
            for (int humans = 1; humans <= maximum; humans++) {
                for (int minimum = 2; minimum <= maximum; minimum++) {
                    int bots = QueueFillPolicy.desiredBots(humans, minimum, maximum, 15, 24);
                    assertTrue(bots <= (maximum <= 4 ? maximum : maximum / 2));
                    assertTrue(humans + bots <= maximum);
                    assertTrue(bots <= 15);
                    assertTrue(bots == 0 || humans + bots >= minimum);
                }
            }
        }
    }
    @Test void humansReplaceBotsRatherThanOverflow() {
        assertEquals(6, QueueFillPolicy.desiredBots(5,2,12,16,24));
        assertEquals(5, QueueFillPolicy.desiredBots(7,2,12,16,24));
        assertEquals(0, QueueFillPolicy.desiredBots(4,2,4,7,24));
        assertEquals(1, QueueFillPolicy.desiredBots(3,2,4,7,24));
        assertEquals(2, QueueFillPolicy.desiredBots(6,2,8,7,24));
    }
}
