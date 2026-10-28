package uk.co.playerready.pulsegames.games.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RaceFinishTest {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test void firstFinishOpensGraceAndLaterFinishersGetPlacesAndTimes() {
        RaceFinish race = new RaceFinish(List.of(a, b, c), 30);
        assertTrue(race.finish(a, 60_000));
        assertFalse(race.shouldEnd(61_000));
        assertTrue(race.finish(b, 75_000));
        assertEquals(1, race.placeOf(a));
        assertEquals(2, race.placeOf(b));
        assertEquals(75_000, race.timeOf(b));
        assertEquals(0, race.placeOf(c));
    }

    @Test void finishAfterGraceIsRejectedAndRaceEnds() {
        RaceFinish race = new RaceFinish(List.of(a, b), 30);
        race.finish(a, 60_000);
        assertFalse(race.finish(b, 90_001));
        assertTrue(race.shouldEnd(90_000));
        assertEquals(0, race.graceRemainingMs(95_000));
    }

    @Test void endsEarlyWhenEveryoneFinishedOrLeft() {
        RaceFinish race = new RaceFinish(List.of(a, b, c), 30);
        race.finish(a, 1_000);
        race.depart(c);
        assertFalse(race.shouldEnd(2_000));
        race.finish(b, 3_000);
        assertTrue(race.shouldEnd(3_000));
    }

    @Test void unfinishedAreOrderedAfterFinishersByProgress() {
        RaceFinish race = new RaceFinish(List.of(a, b, c), 30);
        race.finish(b, 5_000);
        assertEquals(List.of(b, c, a), race.fullOrder(List.of(c, a)));
    }

    @Test void splitIsGapToTheFastestEarlierArrival() {
        RaceFinish race = new RaceFinish(List.of(a, b, c), 30);
        assertEquals(0, race.split(1, a, 10_000));
        assertEquals(1_400, race.split(1, b, 11_400));
        assertEquals(2_000, race.split(1, c, 12_000));
        assertEquals(0, race.split(2, b, 20_000), "different gate has its own leader");
    }

    @Test void graceIsClampedAndFormatting() {
        RaceFinish race = new RaceFinish(List.of(a, b), 1);
        race.finish(a, 0);
        assertEquals(5, race.graceRemainingSeconds(0));
        assertEquals("1st", RaceFinish.ordinal(1));
        assertEquals("3rd", RaceFinish.ordinal(3));
        assertEquals("11th", RaceFinish.ordinal(11));
        assertEquals("22nd", RaceFinish.ordinal(22));
        assertEquals("1:02.3", RaceFinish.time(62_300));
        assertEquals("+1.4s", RaceFinish.gap(1_400));
        assertEquals("leader", RaceFinish.gap(0));
    }
}
