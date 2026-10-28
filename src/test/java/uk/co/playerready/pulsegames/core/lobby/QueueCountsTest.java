package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueueCountsTest {
    @Test void talliesWaitingAndPlayingHumansPerGameAndIgnoresPractice() {
        var counts = QueueCounts.tally(List.of(
                new QueueCounts.Row("spleef", true, false, false, 2),
                new QueueCounts.Row("spleef", true, false, false, 1),
                new QueueCounts.Row("spleef", false, true, false, 5),
                new QueueCounts.Row("spleef", false, true, true, 9),
                new QueueCounts.Row("koth", false, false, false, 4),
                new QueueCounts.Row("koth", true, false, false, 0)));
        assertEquals(new QueueCounts.Count(3, 5), counts.get("spleef"));
        assertNull(counts.get("koth"));
    }
}
