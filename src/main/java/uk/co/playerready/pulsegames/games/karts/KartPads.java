package uk.co.playerready.pulsegames.games.karts;

import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pad cooldowns and region entry maths shared by boost panels and jumps. */
final class KartPads {
    static final int BOOST_COOLDOWN = 20;
    private final Map<String, Long> readyAt = new HashMap<>();

    /** True when this kart may use the pad now; arms a per-kart, per-pad cooldown. */
    boolean trigger(UUID kart, String pad, long tick, int cooldown) {
        String key = kart + "/" + pad;
        if (tick < readyAt.getOrDefault(key, 0L)) return false;
        readyAt.put(key, tick + cooldown);
        return true;
    }
    void forget(UUID kart) { readyAt.keySet().removeIf(key -> key.startsWith(kart.toString())); }

    /** Block-coordinate containment, matching {@link Cuboid#contains} without needing a Location. */
    static boolean inside(Cuboid box, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        return bx >= box.minX() && bx <= box.maxX() && by >= box.minY() && by <= box.maxY()
                && bz >= box.minZ() && bz <= box.maxZ();
    }

    /**
     * Entry this tick: the swept path touches the region and the previous position was outside, so a kart
     * parked on a pad (or reset onto one) does not re-trigger it, and a fast kart cannot skip a thin pad.
     */
    static boolean enters(Cuboid box, KartPhysics.State from, KartPhysics.State to) {
        if (inside(box, from.x(), from.y(), from.z())) return false;
        double[] a = {from.x(), from.y(), from.z()}, b = {to.x(), to.y(), to.z()};
        double[] low = {box.minX(), box.minY(), box.minZ()};
        double[] high = {Math.nextDown(box.maxX() + 1d), Math.nextDown(box.maxY() + 1d), Math.nextDown(box.maxZ() + 1d)};
        double enter = 0, exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            double delta = b[axis] - a[axis];
            if (!Double.isFinite(delta)) return false;
            if (delta == 0) { if (a[axis] < low[axis] || a[axis] > high[axis]) return false; continue; }
            double first = (low[axis] - a[axis]) / delta, last = (high[axis] - a[axis]) / delta;
            enter = Math.max(enter, Math.min(first, last));
            exit = Math.min(exit, Math.max(first, last));
            if (enter > exit) return false;
        }
        return true;
    }
}
