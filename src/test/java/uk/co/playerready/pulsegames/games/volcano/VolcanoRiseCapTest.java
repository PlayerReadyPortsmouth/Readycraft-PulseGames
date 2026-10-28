package uk.co.playerready.pulsegames.games.volcano;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolcanoRiseCapTest {
    @Test void lateRiseNeverGetsFasterThanTheMinimumInterval() {
        // Raw pacing reaches one block a second after 90 s; the default cap holds it at two.
        assertEquals(2, VolcanoGame.cappedInterval(VolcanoGame.riseInterval(4, 30, 90), 2));
        assertEquals(2, VolcanoGame.cappedInterval(1, 2));
        assertEquals(4, VolcanoGame.cappedInterval(4, 2), "early pacing is untouched");
    }

    @Test void capCanBeRelaxedToTheOldBehaviourAndNeverGoesBelowOne() {
        assertEquals(1, VolcanoGame.cappedInterval(1, 1));
        assertEquals(1, VolcanoGame.cappedInterval(1, 0));
        assertEquals(1, VolcanoGame.cappedInterval(1, -5));
    }
}
