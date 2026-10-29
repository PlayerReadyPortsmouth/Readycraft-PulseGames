package uk.co.playerready.pulsegames.core.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JoinHintTest {
    @Test void nothingWaitingSaysNothing() {
        assertNull(JoinHint.line(null, 1, false, false));
        assertNull(JoinHint.line("Spleef", 1.0, false, false));
    }

    @Test void featuredMultiplierIsNamed() {
        assertEquals("<gold>Today: Spleef pays x1.5. Quick Play?", JoinHint.line("Spleef", 1.5, false, false));
        assertTrue(JoinHint.line("Spleef", 2, false, true).contains("x2"));
    }

    @Test void bonusesAloneStillShow() {
        assertTrue(JoinHint.line(null, 1, true, false).contains("challenges"));
        assertTrue(JoinHint.line(null, 1, false, true).contains("first win"));
    }
}
