package uk.co.playerready.pulsegames.core.lobby;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live "N waiting / M playing" per game, counted in humans. */
public final class QueueCounts {
    private QueueCounts() { }

    public record Count(int waiting, int playing) { }

    /** One running or queued instance; practice rounds are not counted. */
    public record Row(String gameId, boolean queueing, boolean running, boolean practice, int humans) { }

    public static Map<String, Count> tally(List<Row> rows) {
        Map<String, Count> result = new HashMap<>();
        for (Row row : rows) {
            if (row.practice() || row.humans() <= 0 || (!row.queueing() && !row.running())) continue;
            Count old = result.getOrDefault(row.gameId(), new Count(0, 0));
            result.put(row.gameId(), row.queueing()
                    ? new Count(old.waiting() + row.humans(), old.playing())
                    : new Count(old.waiting(), old.playing() + row.humans()));
        }
        return result;
    }
}
