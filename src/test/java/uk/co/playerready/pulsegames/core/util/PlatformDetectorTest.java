package uk.co.playerready.pulsegames.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformDetectorTest {
    @Test void bedrockTapsAndJavaRightClicks() {
        assertEquals("tap", PlatformDetector.useVerb(true));
        assertEquals("right-click", PlatformDetector.useVerb(false));
    }

    @Test void missingPlayerIsJava() {
        assertFalse(PlatformDetector.isBedrock(null));
    }
}
