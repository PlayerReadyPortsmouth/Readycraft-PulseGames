package uk.co.playerready.pulsegames.core.util;

import java.util.ArrayDeque;

/**
 * At most {@code max} events in any {@code windowMs}. Used to bound flashing effects (lightning, fireworks,
 * emotes) so that many players doing the same thing cannot stack into a strobe for a photosensitive viewer.
 * Not thread-safe: call from the main thread.
 */
public final class RateCap {
    private final int max;
    private final long windowMs;
    private final ArrayDeque<Long> times = new ArrayDeque<>();

    public RateCap(int max, long windowMs) {
        this.max = Math.max(1, max);
        this.windowMs = windowMs;
    }

    /** True (and the event is counted) when another event fits in the window; false when it should be dropped. */
    public boolean allow(long nowMs) {
        while (!times.isEmpty() && nowMs - times.peekFirst() >= windowMs) times.pollFirst();
        if (times.size() >= max) return false;
        times.addLast(nowMs);
        return true;
    }
}
