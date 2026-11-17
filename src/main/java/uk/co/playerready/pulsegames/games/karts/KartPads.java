package uk.co.playerready.pulsegames.games.karts;

import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pad cooldowns and region entry maths shared by boost panels and jumps. */
final class KartPads {
    static final int BOOST_COOLDOWN = 20;
    private final Map<String, Long> readyAt = new HashMap<>();
    private final Map<String, Boolean> contact = new HashMap<>();

    /** True when this kart may use the pad now; arms a per-kart, per-pad cooldown. */
    boolean trigger(UUID kart, String pad, long tick, int cooldown) {
        String key = kart + "/" + pad;
        if (tick < readyAt.getOrDefault(key, 0L)) return false;
        readyAt.put(key, tick + cooldown);
        return true;
    }
    /** Contact between two karts: both must be off cooldown, and then both are armed. */
    boolean triggerBoth(UUID a, UUID b, String pad, long tick, int cooldown) {
        String ka = a + "/" + pad, kb = b + "/" + pad;
        if (tick < readyAt.getOrDefault(ka, 0L) || tick < readyAt.getOrDefault(kb, 0L)) return false;
        readyAt.put(ka, tick + cooldown); readyAt.put(kb, tick + cooldown);
        return true;
    }
    void forget(UUID kart) {
        String prefix = kart + "/";
        readyAt.keySet().removeIf(key -> key.startsWith(prefix));
        contact.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Continuous material strips are one contact: stopping or crossing neighbouring tiles cannot refresh it. */
    boolean contactEntry(UUID kart, String pad, boolean touching, long tick, int cooldown) {
        String key = kart + "/" + pad;
        boolean previous = contact.getOrDefault(key, false);
        if (touching) contact.put(key, true); else contact.remove(key);
        return touching && !previous && trigger(kart, pad, tick, cooldown);
    }

    /** Profiled pads require forward travel; optional authored yaw additionally rejects wrong-way approaches. */
    boolean entry(UUID kart, String pad, Cuboid box, KartPhysics.State from, KartPhysics.State to,
                  Double directionYaw, long tick, int cooldown) {
        return forwardEntry(box, from, to, directionYaw) && trigger(kart, pad, tick, cooldown);
    }

    static boolean forwardEntry(Cuboid box, KartPhysics.State from, KartPhysics.State to, Double directionYaw) {
        if (to.speed() <= 0 || !enters(box, from, to)) return false;
        double yaw = directionYaw == null ? from.yaw() : directionYaw;
        if (!Double.isFinite(yaw)) return false;
        double radians = Math.toRadians(yaw);
        return forwardEntry(box, from, to, -Math.sin(radians), 0, Math.cos(radians));
    }

    /** Surface-frame overload also handles panels while travelling vertically on a wall. */
    boolean entry(UUID kart, String pad, Cuboid box, KartPhysics.State from, KartPhysics.State to,
                  double forwardX, double forwardY, double forwardZ, long tick, int cooldown) {
        return forwardEntry(box, from, to, forwardX, forwardY, forwardZ) && trigger(kart, pad, tick, cooldown);
    }

    static boolean forwardEntry(Cuboid box, KartPhysics.State from, KartPhysics.State to,
                                double forwardX, double forwardY, double forwardZ) {
        if (to.speed() <= 0 || !enters(box, from, to)
                || !Double.isFinite(forwardX) || !Double.isFinite(forwardY) || !Double.isFinite(forwardZ)) return false;
        return (to.x() - from.x()) * forwardX + (to.y() - from.y()) * forwardY
                + (to.z() - from.z()) * forwardZ > 1e-6;
    }

    /** Block-coordinate containment, matching {@link Cuboid#contains} without needing a Location. */
    static boolean inside(Cuboid box, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        return bx >= box.minX() && bx <= box.maxX() && by >= box.minY() && by <= box.maxY()
                && bz >= box.minZ() && bz <= box.maxZ();
    }

    /** Within {@code margin} blocks of the region horizontally (and a few blocks vertically). */
    static boolean near(Cuboid box, double x, double y, double z, double margin) {
        return x >= box.minX() - margin && x <= box.maxX() + 1 + margin && z >= box.minZ() - margin && z <= box.maxZ() + 1 + margin
                && y >= box.minY() - 3 && y <= box.maxY() + 4;
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
