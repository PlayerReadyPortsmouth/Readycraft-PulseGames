package uk.co.playerready.pulsegames.games.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeComebackTest {
    @Test void aComebackAddsOneForgeTierUpToTheTop() {
        assertEquals(1, ForgeRates.withComeback(0, true));
        assertEquals(0, ForgeRates.withComeback(0, false));
        assertEquals(ForgeRates.MAX_LEVEL, ForgeRates.withComeback(ForgeRates.MAX_LEVEL, true));
        assertEquals(3, ForgeRates.withComeback(2, true));
    }
}
