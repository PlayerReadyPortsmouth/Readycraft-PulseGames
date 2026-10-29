package uk.co.playerready.pulsegames.core.events;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.events.EventSchedule.Format;
import uk.co.playerready.pulsegames.core.events.EventSchedule.Pick;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventScheduleTest {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final Pick SPLEEF = new Pick("spleef", "classic", Format.POINTS, 3, 2.0);
    private static final Pick DUELS = new Pick("duels", "classic", Format.BRACKET, 3, 2.0);

    private static EventSchedule hourly(Pick... rotation) {
        return new EventSchedule(LONDON, List.of(new EventSchedule.Entry(null, Duration.ofMinutes(60), null)), List.of(rotation));
    }

    @Test void parsesEveryAndAtAndRejectsNonsense() {
        assertEquals(Duration.ofMinutes(60), EventSchedule.parseEvery("60m"));
        assertEquals(Duration.ofMinutes(90), EventSchedule.parseEvery("1.5h".replace("1.5h", "90")));
        assertEquals(Duration.ofHours(2), EventSchedule.parseEvery("2h"));
        assertNull(EventSchedule.parseEvery("5m"));
        assertNull(EventSchedule.parseEvery("soon"));
        assertNull(EventSchedule.parseEvery("25h"));
        assertEquals(LocalTime.of(9, 5), EventSchedule.parseAt("9:05"));
        assertEquals(LocalTime.of(19, 30), EventSchedule.parseAt("19:30"));
        assertNull(EventSchedule.parseAt("25:00"));
    }

    @Test void parsesAPickAndClampsOddValues() {
        Pick p = EventSchedule.parsePick(Map.of("game", "Spleef", "format", "bracket", "rounds", 99, "tokens-multiplier", 0.1), 2.0);
        assertEquals("spleef", p.game());
        assertEquals(Format.BRACKET, p.format());
        assertEquals(5, p.rounds());
        assertEquals(1.0, p.multiplier());
        assertNull(EventSchedule.parsePick(Map.of("game", "spleef", "format", "ladder"), 2.0));
        assertNull(EventSchedule.parsePick(Map.of("format", "points"), 2.0));
    }

    @Test void hourlyEventsRunAcrossMidnight() {
        Instant from = Instant.parse("2026-11-10T23:30:00Z");
        var next = hourly(SPLEEF, DUELS).next(from, 3);
        assertEquals(List.of(Instant.parse("2026-11-11T00:00:00Z"), Instant.parse("2026-11-11T01:00:00Z"),
                Instant.parse("2026-11-11T02:00:00Z")), next.stream().map(EventSchedule.Occurrence::start).toList());
    }

    @Test void anOccurrenceExactlyNowIsIncluded() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        assertEquals(now, hourly(SPLEEF).next(now, 1).getFirst().start());
    }

    @Test void springForwardKeepsHourlyEventsOnTheHourWithNoGapOrRepeat() {
        // London clocks go forward at 01:00 GMT on 28 March 2027.
        Instant from = Instant.parse("2027-03-27T23:30:00Z");
        var starts = hourly(SPLEEF).next(from, 5).stream().map(EventSchedule.Occurrence::start).toList();
        for (int i = 1; i < starts.size(); i++) assertEquals(Duration.ofHours(1), Duration.between(starts.get(i - 1), starts.get(i)));
        assertEquals(Instant.parse("2027-03-28T00:00:00Z"), starts.getFirst());
        assertEquals(2, starts.get(1).atZone(LONDON).getHour(), "01:00Z is 02:00 BST: the missing hour is skipped, not doubled");
        assertTrue(starts.stream().allMatch(s -> s.atZone(LONDON).getMinute() == 0));
    }

    @Test void autumnClocksBackGiveTwentyFiveHourlySlotsWithoutRepeats() {
        // 25 October 2026 has 25 hours; the extra 01:00 happens once at 01:00 GMT.
        Instant from = Instant.parse("2026-10-24T23:00:00Z");
        var starts = hourly(SPLEEF).next(from, 26).stream().map(EventSchedule.Occurrence::start).toList();
        assertEquals(26, starts.size());
        for (int i = 1; i < starts.size(); i++) assertEquals(Duration.ofHours(1), Duration.between(starts.get(i - 1), starts.get(i)));
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), starts.get(25));
    }

    @Test void aFixedTimeInTheSpringGapMovesForwardAndAnAutumnOverlapUsesTheFirst() {
        var s = new EventSchedule(LONDON, List.of(new EventSchedule.Entry(LocalTime.of(1, 30), null, SPLEEF)), List.of());
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), s.next(Instant.parse("2027-03-28T00:00:00Z"), 1).getFirst().start());
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), s.next(Instant.parse("2026-10-25T00:00:00Z"), 1).getFirst().start());
    }

    @Test void dailyFixedTimeReadsLondonWallClockInSummer() {
        var s = new EventSchedule(LONDON, List.of(new EventSchedule.Entry(LocalTime.of(19, 0), null, SPLEEF)), List.of());
        assertEquals(Instant.parse("2026-07-01T18:00:00Z"), s.next(Instant.parse("2026-07-01T12:00:00Z"), 1).getFirst().start());
    }

    @Test void theRotationAdvancesEachSlotWrapsAndOffersTheRestAsFallbacks() {
        var next = hourly(SPLEEF, DUELS).next(Instant.parse("2026-11-10T10:00:00Z"), 3);
        assertNotEquals(next.get(0).pick(), next.get(1).pick());
        assertEquals(next.get(0).pick(), next.get(2).pick());
        assertEquals(List.of(next.get(1).pick(), next.get(0).pick()), next.get(1).choices());
        // Deterministic: a restart mid-hour picks the same game.
        assertEquals(next.get(1).pick(), hourly(SPLEEF, DUELS).next(Instant.parse("2026-11-10T10:20:00Z"), 1).getFirst().pick());
    }

    @Test void aFixedLineBeatsTheRotationAtTheSameInstantAndEarlierLinesWin() {
        Pick special = new Pick("karts", "cup", Format.POINTS, 3, 3.0);
        var s = new EventSchedule(LONDON, List.of(new EventSchedule.Entry(LocalTime.of(19, 0), null, special),
                new EventSchedule.Entry(null, Duration.ofHours(1), null)), List.of(SPLEEF));
        var at19 = s.next(Instant.parse("2026-11-10T18:30:00Z"), 2);
        assertEquals(special, at19.getFirst().pick());
        assertEquals(Instant.parse("2026-11-10T20:00:00Z"), at19.get(1).start());
    }

    @Test void anEmptyRotationWithNoFixedGameSchedulesNothing() {
        assertTrue(hourly().next(Instant.parse("2026-11-10T10:00:00Z"), 3).isEmpty());
        assertTrue(hourly().isEmpty());
    }
}
