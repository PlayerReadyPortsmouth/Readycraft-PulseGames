package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReadyVoteTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test void startsOnlyWhenEveryHumanIsReadyAndMinimumMet() {
        assertTrue(ReadyVote.shouldStart(List.of(a, b), Set.of(a, b), 2, 2));
        assertFalse(ReadyVote.shouldStart(List.of(a, b), Set.of(a), 2, 2));
        assertFalse(ReadyVote.shouldStart(List.of(a), Set.of(a), 1, 2));
        assertFalse(ReadyVote.shouldStart(List.of(), Set.of(), 2, 2));
    }

    @Test void readyNeverLengthensTheCountdown() {
        assertEquals(5, ReadyVote.shortenedCountdown(30, 5));
        assertEquals(3, ReadyVote.shortenedCountdown(3, 5));
    }
}
