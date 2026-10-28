package uk.co.playerready.pulsegames.games.karts;

import java.util.List;

/** Collectable item-box state: swept proximity pickup, global 4 s respawn and a short fade-in. Pure. */
final class KartItemBoxes {
    static final double RADIUS = 1.2;
    static final int RESPAWN_TICKS = 80, FADE_TICKS = 10;
    /** Box centre sits this far above its location (feet) and the kart centre this far above its floor. */
    static final double BOX_LIFT = .9, KART_LIFT = .6;
    private final List<KartArcadeConfig.Pt> positions;
    private final long[] readyAt;

    KartItemBoxes(List<KartArcadeConfig.Pt> positions) {
        this.positions = List.copyOf(positions);
        this.readyAt = new long[positions.size()];
    }
    int size() { return positions.size(); }
    KartArcadeConfig.Pt position(int index) { return positions.get(index); }
    boolean available(int index, long tick) { return tick >= readyAt[index]; }

    /**
     * The nearest available box touched by the kart's path this tick, now collected, or -1. The path is swept
     * so a kart at full speed (0.6 blocks per tick) cannot skip a 1.2-block pickup radius.
     */
    int collect(KartPhysics.State from, KartPhysics.State to, long tick) {
        int best = -1;
        double bestDistance = RADIUS;
        for (int i = 0; i < positions.size(); i++) {
            if (!available(i, tick)) continue;
            var box = positions.get(i);
            double distance = segmentDistance(from.x(), from.y() + KART_LIFT, from.z(), to.x(), to.y() + KART_LIFT, to.z(),
                    box.x(), box.y() + BOX_LIFT, box.z());
            if (distance <= bestDistance) { best = i; bestDistance = distance; }
        }
        if (best >= 0) readyAt[best] = tick + RESPAWN_TICKS;
        return best;
    }
    /** 0 while collected, rising to 1 across the last {@link #FADE_TICKS} before it can be collected again. */
    double scale(int index, long tick) {
        long remaining = readyAt[index] - tick;
        if (remaining <= 0) return 1;
        if (remaining >= FADE_TICKS) return 0;
        return 1 - remaining / (double) FADE_TICKS;
    }
    void reset() { java.util.Arrays.fill(readyAt, 0); }

    static double segmentDistance(double ax, double ay, double az, double bx, double by, double bz,
                                  double px, double py, double pz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double length = dx * dx + dy * dy + dz * dz;
        double t = length < 1e-12 ? 0 : Math.clamp(((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / length, 0, 1);
        double cx = ax + dx * t - px, cy = ay + dy * t - py, cz = az + dz * t - pz;
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }
}
