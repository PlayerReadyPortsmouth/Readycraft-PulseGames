package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsenetwork.PresenceTracker.Line.*;

class PresenceTrackerTest {
    static final class Fake extends Clock {
        Instant now = Instant.parse("2026-10-30T10:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { Fake parent = this; return new Clock() {
            public ZoneId getZone() { return zone; } public Clock withZone(ZoneId z) { return parent.withZone(z); } public Instant instant() { return parent.now; } }; }
        @Override public Instant instant() { return now; }
    }
    final Fake clock = new Fake();
    final PresenceTracker tracker = new PresenceTracker(new PresenceTracker.Rules(60_000, 20_000, 120_000), clock);
    final UUID a = UUID.randomUUID();

    @Test void normalSessionProducesJoinAndLeave() {
        assertEquals(JOIN, tracker.join(a, false)); clock.advance(30);
        assertEquals(LEAVE, tracker.leave(a, false));
    }
    @Test void shortStayHasNoLeaveLine() {
        tracker.join(a, false); clock.advance(19);
        assertEquals(NONE, tracker.leave(a, false));
    }
    @Test void rejoinWithinTwoMinutesIsSilentThenNormalAgain() {
        tracker.join(a, false); clock.advance(60); tracker.leave(a, false); clock.advance(119);
        assertEquals(NONE, tracker.join(a, false)); clock.advance(60);
        assertEquals(LEAVE, tracker.leave(a, false)); clock.advance(121);
        assertEquals(JOIN, tracker.join(a, false));
    }
    @Test void quickHoppingGivesOneLinePerWindowAndCountsTheRest() {
        tracker.join(a, false); clock.advance(5);
        assertEquals(SWITCH, tracker.hop(a, false)); clock.advance(5);
        assertEquals(NONE, tracker.hop(a, false)); clock.advance(5);
        assertEquals(NONE, tracker.hop(a, false));
        assertEquals(2, tracker.suppressedSwitches(a));
        clock.advance(50);
        assertEquals(SWITCH, tracker.hop(a, false));
        assertEquals(0, tracker.suppressedSwitches(a));
    }
    @Test void cooldownSurvivesLeavingAndRejoining() {
        tracker.join(a, false); tracker.hop(a, false); clock.advance(25); tracker.leave(a, false); tracker.join(a, false); clock.advance(5);
        assertEquals(NONE, tracker.hop(a, false));
    }
    @Test void hiddenPlayersAreAlwaysSilent() {
        assertEquals(NONE, tracker.join(a, true)); clock.advance(100);
        assertEquals(NONE, tracker.hop(a, true));
        assertEquals(NONE, tracker.leave(a, true));
    }
    @Test void leaveWithoutKnownJoinIsSilent() { assertEquals(NONE, tracker.leave(UUID.randomUUID(), false)); }
    @Test void welcomeOncePerUkDay() {
        assertTrue(tracker.firstJoinToday(a)); clock.advance(3600);
        assertFalse(tracker.firstJoinToday(a));
        clock.now = Instant.parse("2026-10-31T00:30:00Z"); // still GMT, new UK day
        assertTrue(tracker.firstJoinToday(a));
    }
    @Test void ukDayUsesLondonTimeInSummer() {
        clock.now = Instant.parse("2026-07-01T22:30:00Z"); // 23:30 BST
        assertTrue(tracker.firstJoinToday(a)); clock.advance(3600); // 00:30 BST next day
        assertTrue(tracker.firstJoinToday(a));
    }
    @Test void configParsesDefaultsAndRejectsBadValues() {
        var c = PresenceConfig.parse(new Properties());
        assertTrue(c.enabled()); assertFalse(c.serversOnly()); assertEquals(60_000, c.rules().switchCooldownMs());
        assertEquals(20_000, c.rules().leaveMinStayMs()); assertEquals(120_000, c.rules().quietRejoinMs());
        var p = new Properties(); p.setProperty("presence.scope", "servers"); p.setProperty("presence.hidden-roles", " Dev, staff ");
        var s = PresenceConfig.parse(p); assertTrue(s.serversOnly()); assertEquals(Set.of("dev", "staff"), s.hiddenRoles());
        p.setProperty("presence.scope", "everywhere"); assertThrows(IllegalArgumentException.class, () -> PresenceConfig.parse(p));
        var q = new Properties(); q.setProperty("presence.switch-cooldown-seconds", "-1"); assertThrows(IllegalArgumentException.class, () -> PresenceConfig.parse(q));
    }
}
