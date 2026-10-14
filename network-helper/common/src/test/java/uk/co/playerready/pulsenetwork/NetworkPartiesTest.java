package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NetworkPartiesTest {
    @Test void invitationAcceptanceAndBackendIndependentMembership() {
        NetworkParties parties = new NetworkParties();
        UUID leader = UUID.randomUUID(), friend = UUID.randomUUID();
        parties.invite(leader, friend); parties.accept(friend);
        assertEquals(leader, parties.leader(friend));
        assertEquals(List.of(leader, friend), parties.members(friend));
        assertThrows(IllegalArgumentException.class, () -> parties.invite(friend, UUID.randomUUID()));
    }
    @Test void staleInviteCannotJoinTwoParties() {
        NetworkParties parties = new NetworkParties();
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        parties.invite(one, two);
        parties.invite(two, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> parties.accept(two));
        assertEquals(two, parties.leader(two));
    }
    @Test void leaderDeparturePromotesMemberAndInvalidatesInvitations() {
        NetworkParties parties = new NetworkParties();
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID(), invited = UUID.randomUUID();
        parties.invite(leader, member); parties.accept(member); parties.invite(leader, invited);
        parties.leave(leader);
        assertEquals(member, parties.leader(member));
        assertThrows(IllegalArgumentException.class, () -> parties.accept(invited));
    }
    @Test void capacityRecheckedWhenAcceptingAndNoPartialMembership() {
        NetworkParties parties = new NetworkParties();
        UUID leader = UUID.randomUUID(), excess = UUID.randomUUID();
        parties.invite(leader, excess);
        for (int i = 0; i < 7; i++) { UUID next = UUID.randomUUID(); parties.invite(leader, next); parties.accept(next); }
        assertThrows(IllegalArgumentException.class, () -> parties.accept(excess));
        assertNull(parties.leader(excess));
        assertEquals(8, parties.members(leader).size());
    }
}
