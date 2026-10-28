package uk.co.playerready.pulsegames.games.koth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HillControlTest {
    @Test void emptyHillIsEmpty() {
        assertEquals(HillControl.State.EMPTY, HillControl.resolve(List.<Integer>of()).state());
    }

    @Test void onePlayerOrOneWholeTeamHoldsTheHill() {
        var control = HillControl.resolve(List.of(2, 2, 2));
        assertEquals(HillControl.State.HELD, control.state());
        assertEquals(2, control.holder());
    }

    @Test void twoSidesContestAndNobodyHolds() {
        var control = HillControl.resolve(List.of(0, 1, 1));
        assertEquals(HillControl.State.CONTESTED, control.state());
        assertNull(control.holder());
    }

    @Test void rotationWrapsAndSingleHillStaysPut() {
        assertEquals(1, HillControl.next(0, 3));
        assertEquals(0, HillControl.next(2, 3));
        assertEquals(0, HillControl.next(0, 1));
    }
}
