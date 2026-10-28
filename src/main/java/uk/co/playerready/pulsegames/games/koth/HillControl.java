package uk.co.playerready.pulsegames.games.koth;

import java.util.Collection;
import java.util.HashSet;

/** Who is scoring the hill this second. More than one side on it means nobody scores. */
public final class HillControl {
    public enum State { EMPTY, HELD, CONTESTED }

    public record Control<T>(State state, T holder) {}

    private HillControl() {}

    /** @param sidesOnHill one entry per occupant: the team index, or the player id in solo. */
    public static <T> Control<T> resolve(Collection<T> sidesOnHill) {
        var sides = new HashSet<>(sidesOnHill);
        if (sides.isEmpty()) return new Control<>(State.EMPTY, null);
        if (sides.size() > 1) return new Control<>(State.CONTESTED, null);
        return new Control<>(State.HELD, sides.iterator().next());
    }

    /** Index of the hill to use after rotating, or the same one if there is only one. */
    public static int next(int current, int count) {
        return count <= 1 ? 0 : (current + 1) % count;
    }
}
