package uk.co.playerready.pulsegames.core.ranked;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rating change is reduced when the same two players keep meeting inside a short window. */
public final class RepeatDampener {
    private final long windowMs;
    private final double[] factors;
    private final Map<String, Deque<Long>> meetings = new HashMap<>();

    /** @param factors multiplier after 0, 1, 2... earlier meetings in the window; the last repeats. */
    public RepeatDampener(long windowMs, double... factors) {
        this.windowMs = windowMs;
        this.factors = factors.length == 0 ? new double[]{1.0} : factors;
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** Multiplier for a match finishing now. Does not record it. */
    public synchronized double factor(UUID a, UUID b, long nowMs) {
        Deque<Long> times = meetings.get(key(a, b));
        int n = 0;
        if (times != null) {
            times.removeIf(t -> nowMs - t > windowMs);
            n = times.size();
        }
        return factors[Math.min(n, factors.length - 1)];
    }

    public synchronized void record(UUID a, UUID b, long nowMs) {
        if (meetings.size() > 2048) meetings.values().removeIf(q -> q.isEmpty() || nowMs - q.peekLast() > windowMs);
        meetings.computeIfAbsent(key(a, b), k -> new ArrayDeque<>()).add(nowMs);
    }
}
