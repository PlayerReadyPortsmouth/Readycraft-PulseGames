package uk.co.playerready.pulsegames.games.karts;

import java.util.*;

/** Live banana hazards: arming delay, owner grace, lifetime and a match-wide cap. Pure. */
final class KartBananas {
    static final int ARM_TICKS = 10, OWNER_SAFE_TICKS = 20, LIFETIME_TICKS = 600, MAX_LIVE = 24;
    static final double RADIUS = 1.1, REACH_Y = 1.5, DROP_BEHIND = 2.5;
    record Banana(int id, UUID owner, double x, double y, double z, long placed) {}
    private final List<Banana> live = new ArrayList<>();
    private int nextId;

    List<Banana> live() { return List.copyOf(live); }

    /** Drops one behind the kart; returns any older banana evicted by the cap so its display can be removed. */
    Banana drop(UUID owner, KartPhysics.State kart, double behind, long tick, List<Banana> evicted) {
        double radians = Math.toRadians(kart.yaw());
        var banana = new Banana(nextId++, owner, kart.x() + Math.sin(radians) * behind, kart.y(),
                kart.z() - Math.cos(radians) * behind, tick);
        live.add(banana);
        while (live.size() > MAX_LIVE) evicted.add(live.remove(0));
        return banana;
    }
    List<Banana> expire(long tick) {
        List<Banana> gone = live.stream().filter(b -> tick - b.placed() >= LIFETIME_TICKS).toList();
        live.removeAll(gone);
        return gone;
    }
    /** The first armed banana within reach of the kart's path this tick; the caller decides whether to consume it. */
    Banana touched(KartPhysics.State from, KartPhysics.State to, UUID kart, long tick) {
        for (Banana banana : live) {
            if (tick - banana.placed() < ARM_TICKS) continue;
            if (banana.owner().equals(kart) && tick - banana.placed() < OWNER_SAFE_TICKS) continue;
            if (Math.abs(to.y() - banana.y()) > REACH_Y && Math.abs(from.y() - banana.y()) > REACH_Y) continue;
            if (KartItemBoxes.segmentDistance(from.x(), 0, from.z(), to.x(), 0, to.z(), banana.x(), 0, banana.z()) <= RADIUS)
                return banana;
        }
        return null;
    }
    void remove(Banana banana) { live.remove(banana); }
    List<Banana> clear() { var all = List.copyOf(live); live.clear(); return all; }
}
