package uk.co.playerready.pulsegames.core.party;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PartyChatFilterTest {
    @Test void everydayChatPasses() {
        for (String ok : List.of("gg, nice round", "queue dropper?", "I have 3 diamonds", "classic assassin map", "join us at 5", "look at this. wow"))
            assertFalse(PartyChatFilter.blocked(ok, List.of()), ok);
    }

    @Test void abuseIsBlockedEvenWhenDisguised() {
        assertTrue(PartyChatFilter.blocked("you are a b!tch", List.of()));
        assertTrue(PartyChatFilter.blocked("SH1T", List.of()));
        assertTrue(PartyChatFilter.blocked("fuuuuck this", List.of()));
    }

    @Test void contactRoutesOffTheServerAreBlocked() {
        assertTrue(PartyChatFilter.blocked("add me discord.gg/abc", List.of()));
        assertTrue(PartyChatFilter.blocked("www.example.com", List.of()));
        assertTrue(PartyChatFilter.blocked("mail me bob@example.com", List.of()));
        assertTrue(PartyChatFilter.blocked("text 07700 900 123", List.of()));
    }

    @Test void configuredExtraWordsAreHonoured() {
        assertTrue(PartyChatFilter.blocked("you noob", List.of("Noob")));
        assertFalse(PartyChatFilter.blocked("you noob", List.of()));
    }
}
