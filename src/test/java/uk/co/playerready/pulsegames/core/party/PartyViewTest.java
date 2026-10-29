package uk.co.playerready.pulsegames.core.party;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameState;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyViewTest {
    @Test void leaderComesFirstThenAlphabetical() {
        var zed = new PartyView.Person(UUID.randomUUID(), "zed");
        var amy = new PartyView.Person(UUID.randomUUID(), "Amy");
        var lead = new PartyView.Person(UUID.randomUUID(), "Yara");
        var bob = new PartyView.Person(UUID.randomUUID(), "bob");
        assertEquals(List.of(lead, amy, bob, zed), PartyView.orderMembers(lead.id(), List.of(zed, amy, lead, bob)));
    }

    @Test void queueLineIsPlainAndChildFriendly() {
        assertTrue(PartyView.queueLine(null, null).startsWith("Not queued"));
        assertEquals("Waiting for Spleef to start", PartyView.queueLine("Spleef", GameState.COUNTDOWN));
        assertEquals("Playing Spleef", PartyView.queueLine("Spleef", GameState.RUNNING));
    }
}
