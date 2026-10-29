package uk.co.playerready.pulsegames.core.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One-block obstacle avoidance for bots that walk a single axis. When the cell ahead is blocked the bot tries the
 * cell to one side, then the other, and remembers which side it took for a short while so it does not flip back and
 * forth in front of a fence post.
 */
public final class BotSidestep {
    /** Which cells around the bot are fine to stand in. {@code ahead} is along (fx, fz); sides are perpendicular. */
    public interface Probe { boolean open(int dx, int dz); }

    /** A relative cell to step into. */
    public record Step(int dx, int dz) {}

    private static final long MEMORY_TICKS = 60;
    private record Memory(int side, long until) {}
    private final Map<UUID, Memory> remembered = new HashMap<>();

    /**
     * @param fx forward direction on x (-1, 0 or 1); @param fz forward direction on z; exactly one is non-zero
     * @return the side cell to step into, or null when both sides are closed too
     */
    public Step choose(UUID bot, int fx, int fz, long tick, Probe probe) {
        // perpendicular to (fx, fz): side +1 is (-fz, fx), side -1 is (fz, -fx)
        Memory memory = remembered.get(bot);
        int first = memory != null && tick < memory.until() ? memory.side() : 1;
        for (int side : new int[]{first, -first}) {
            int dx = -fz * side, dz = fx * side;
            if (probe.open(dx, dz)) { remembered.put(bot, new Memory(side, tick + MEMORY_TICKS)); return new Step(dx, dz); }
        }
        remembered.remove(bot);
        return null;
    }

    public void forget(UUID bot) { remembered.remove(bot); }
    public void clear() { remembered.clear(); }
}
