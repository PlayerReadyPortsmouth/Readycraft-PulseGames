package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsenetwork.AnvilPromptLogic.*;

class AnvilPromptLogicTest {
    private static AnvilPromptLogic name() { return new AnvilPromptLogic(Rule.playerName()); }

    @Test void validTextInTheResultSlotIsAcceptedTrimmedOnce() {
        var l = name();
        assertEquals(Outcome.ACCEPT, l.click(RESULT_SLOT, "  .Kit_1 "));
        assertEquals(".Kit_1", l.value());
        assertEquals(State.DONE, l.state());
        assertEquals(Outcome.NONE, l.click(RESULT_SLOT, "Other"));
        assertEquals(Outcome.NONE, l.close());
    }
    @Test void otherSlotsAndEmptyOrBadTextNeverAccept() {
        var l = name();
        assertEquals(Outcome.NONE, l.click(0, "Kit"));
        assertEquals(Outcome.NONE, l.click(1, "Kit"));
        assertEquals(Outcome.NONE, l.click(30, "Kit"));
        assertEquals(Outcome.REPROMPT, l.click(RESULT_SLOT, "")); l.reopened();
        assertEquals(Outcome.REPROMPT, l.click(RESULT_SLOT, "bad name")); l.reopened();
        assertEquals(Outcome.REPROMPT, l.click(RESULT_SLOT, "x".repeat(18)));
        assertNotNull(l.error());
        assertNull(l.value());
    }
    @Test void repromptSurvivesTheCloseOfTheOldWindowAndStillAcceptsLater() {
        var l = name();
        assertEquals(Outcome.REPROMPT, l.click(RESULT_SLOT, "no way"));
        assertEquals(Outcome.NONE, l.close());          // the old window closing while we re-open
        l.reopened();
        assertEquals(State.OPEN, l.state());
        assertEquals(Outcome.ACCEPT, l.click(RESULT_SLOT, "Kit"));
    }
    @Test void escapeCancelsOnceAndNothingAcceptsAfter() {
        var l = name();
        assertEquals(Outcome.CANCEL, l.close());
        assertEquals(Outcome.NONE, l.close());
        assertEquals(Outcome.NONE, l.click(RESULT_SLOT, "Kit"));
    }
    @Test void tooManyBadTriesCancels() {
        var l = name();
        for (int i = 1; i < MAX_ATTEMPTS; i++) { assertEquals(Outcome.REPROMPT, l.click(RESULT_SLOT, "!")); l.reopened(); }
        assertEquals(Outcome.CANCEL, l.click(RESULT_SLOT, "!"));
        assertEquals(State.CANCELLED, l.state());
    }
    @Test void freeTextRuleRejectsControlCharactersAndLongText() {
        var r = Rule.text(10);
        assertTrue(r.check("hello there").isPresent());
        assertTrue(r.check("h\u0007i").isPresent());
        assertTrue(r.check("hello").isEmpty());
    }
}
