package uk.co.playerready.pulsegames.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.core.ui.AnvilPromptState.Kind.*;

class AnvilPromptStateTest {
    private static AnvilPromptState digits() {
        return new AnvilPromptState("Level", t -> t.matches("\\d+") ? null : "Numbers only.");
    }

    @Test void acceptsCleanedTextOnce() {
        var s = digits();
        var first = s.submit("  42 ");
        assertEquals(ACCEPTED, first.kind());
        assertEquals("42", first.text());
        assertEquals(AnvilPromptState.Phase.DONE, s.phase());
        assertEquals(IGNORED, s.submit("43").kind());
        assertFalse(s.cancel());
    }

    @Test void rejectionKeepsThePromptOpenForAnotherGo() {
        var s = digits();
        var bad = s.submit("abc");
        assertEquals(REJECTED, bad.kind());
        assertEquals("Numbers only.", bad.error());
        assertTrue(s.open());
        assertEquals(1, s.rejections());
        assertEquals(ACCEPTED, s.submit("7").kind());
    }

    @Test void blankPlaceholderAndOverlongAreRejected() {
        var s = digits();
        assertEquals(REJECTED, s.submit("   ").kind());
        assertEquals(REJECTED, s.submit("Level").kind());
        assertEquals(REJECTED, s.submit("1".repeat(AnvilPromptState.MAX_LENGTH + 1)).kind());
        assertEquals(REJECTED, s.submit(null).kind());
        assertTrue(s.open());
    }

    @Test void cancelWinsOnlyOnceAndStopsLaterInput() {
        var s = digits();
        assertTrue(s.cancel());
        assertFalse(s.cancel());
        assertEquals(IGNORED, s.submit("5").kind());
        assertEquals(AnvilPromptState.Phase.CANCELLED, s.phase());
    }

    @Test void cleanStripsControlCharactersAndCollapsesSpaces() {
        assertEquals("a b", AnvilPromptState.clean("a\u0007  \t b "));
    }
}
