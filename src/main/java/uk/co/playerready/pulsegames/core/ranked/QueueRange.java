package uk.co.playerready.pulsegames.core.ranked;

/** The acceptable rating gap while waiting: starts at {@code start}, grows by {@code step} every {@code stepSeconds}, capped. */
public record QueueRange(int start, int step, int stepSeconds, int cap) {
    public static final QueueRange DEFAULT = new QueueRange(100, 50, 10, 400);

    public int at(long waitedMs) {
        long steps = stepSeconds <= 0 ? 0 : Math.max(0, waitedMs) / (stepSeconds * 1000L);
        return (int) Math.min(cap, start + steps * step);
    }
}
