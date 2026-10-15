package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Location;
import uk.co.playerready.pulsegames.core.util.Cuboid;

/** Segment intersection with a block region; returns its entry fraction or NaN. */
public final class RaceGateCrossing {
    private RaceGateCrossing() {}

    public static double entry(Cuboid gate, Location from, Location to) {
        if (gate == null || from == null || to == null || from.getWorld() == null
                || from.getWorld() != to.getWorld()) return Double.NaN;
        double[] a = {from.getX(), from.getY(), from.getZ()};
        double[] b = {to.getX(), to.getY(), to.getZ()};
        double[] low = {gate.minX(), gate.minY(), gate.minZ()};
        double[] high = {Math.nextDown((double) gate.maxX() + 1),
                Math.nextDown((double) gate.maxY() + 1), Math.nextDown((double) gate.maxZ() + 1)};
        double enter = 0, exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            if (!Double.isFinite(a[axis]) || !Double.isFinite(b[axis])) return Double.NaN;
            double delta = b[axis] - a[axis];
            if (!Double.isFinite(delta)) return Double.NaN;
            if (delta == 0) {
                if (a[axis] < low[axis] || a[axis] > high[axis]) return Double.NaN;
                continue;
            }
            double first = (low[axis] - a[axis]) / delta;
            double last = (high[axis] - a[axis]) / delta;
            enter = Math.max(enter, Math.min(first, last));
            exit = Math.min(exit, Math.max(first, last));
            if (enter > exit) return Double.NaN;
        }
        return enter;
    }
}
