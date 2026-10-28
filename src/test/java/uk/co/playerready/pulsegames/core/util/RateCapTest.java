package uk.co.playerready.pulsegames.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateCapTest {
    @Test void fireworksAreCappedAtThreeASecond() {
        RateCap cap = new RateCap(3, 1000);
        int shown = 0;
        for (int i = 0; i < 12; i++) if (cap.allow(5000 + i)) shown++; // twelve winners in the same tick
        assertEquals(3, shown);
        assertTrue(cap.allow(6001), "the window slides on");
    }

    @Test void oneLightningPerFiveSeconds() {
        RateCap cap = new RateCap(1, 5000);
        assertTrue(cap.allow(0));
        assertFalse(cap.allow(4999));
        assertTrue(cap.allow(5000));
    }

    @Test void emotesShowTwoAtATime() {
        RateCap cap = new RateCap(2, 1000);
        assertTrue(cap.allow(0));
        assertTrue(cap.allow(10));
        assertFalse(cap.allow(20));
        assertTrue(cap.allow(1000));
    }
}
