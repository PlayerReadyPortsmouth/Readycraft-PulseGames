package uk.co.playerready.pulsegames.core.bot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QueueFillPolicyTest {
    @Test void noHumansNeverStartsBotOnlyMatch() { assertEquals(0, QueueFillPolicy.desiredBots(0,2,8,4,7,24)); }
    @Test void targetsNormalRosterWithinCaps() {
        assertEquals(3, QueueFillPolicy.desiredBots(1,2,8,4,7,24));
        assertEquals(1, QueueFillPolicy.desiredBots(1,2,2,4,7,24));
        assertEquals(7, QueueFillPolicy.desiredBots(1,8,16,4,7,24));
    }
    @Test void insufficientCapacityDoesNotCreateStrandedPartialTeam() {
        assertEquals(0, QueueFillPolicy.desiredBots(1,8,16,8,7,6));
        assertEquals(0, QueueFillPolicy.desiredBots(2,8,4,4,7,24));
    }
    @Test void humansReplaceBotsRatherThanOverflow() {
        assertEquals(0, QueueFillPolicy.desiredBots(4,2,4,4,7,24));
        assertEquals(1, QueueFillPolicy.desiredBots(3,2,4,4,7,24));
    }
}
