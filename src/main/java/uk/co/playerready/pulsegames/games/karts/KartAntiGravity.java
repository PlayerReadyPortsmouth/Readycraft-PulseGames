package uk.co.playerready.pulsegames.games.karts;

import java.util.ArrayList;
import java.util.List;

/**
 * Anti-gravity numbers and the ribbon maths. The ribbon is a Catmull-Rom curve through the builder's rail points,
 * sampled for particles only: karts are not held to it (see docs/PULSEKARTS-ARCADE-DESIGN.md).
 */
final class KartAntiGravity {
    static final double SPEED_FACTOR = 1.10, BUMP_FACTOR = 1.30;
    static final int BUMP_TICKS = 15, BUMP_COOLDOWN = 40;
    private KartAntiGravity() {}

    /** Points along the rail no more than {@code spacing} apart, passing through every control point. */
    static List<KartArcadeConfig.Pt> ribbon(List<KartArcadeConfig.Pt> rail, double spacing) {
        if (rail.isEmpty() || !(spacing > .05)) return List.of();
        List<KartArcadeConfig.Pt> out = new ArrayList<>();
        out.add(rail.get(0));
        for (int i = 0; i + 1 < rail.size(); i++) {
            var p0 = rail.get(Math.max(0, i - 1)); var p1 = rail.get(i);
            var p2 = rail.get(i + 1); var p3 = rail.get(Math.min(rail.size() - 1, i + 2));
            double length = Math.sqrt(sq(p2.x() - p1.x()) + sq(p2.y() - p1.y()) + sq(p2.z() - p1.z()));
            int pieces = Math.max(1, (int) Math.ceil(length / spacing * 1.15));
            for (int k = 1; k <= pieces; k++) out.add(spline(p0, p1, p2, p3, k / (double) pieces));
        }
        return List.copyOf(out);
    }
    private static KartArcadeConfig.Pt spline(KartArcadeConfig.Pt a, KartArcadeConfig.Pt b, KartArcadeConfig.Pt c,
                                              KartArcadeConfig.Pt d, double t) {
        return new KartArcadeConfig.Pt(axis(a.x(), b.x(), c.x(), d.x(), t), axis(a.y(), b.y(), c.y(), d.y(), t),
                axis(a.z(), b.z(), c.z(), d.z(), t));
    }
    private static double axis(double p0, double p1, double p2, double p3, double t) {
        return .5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }
    private static double sq(double v) { return v * v; }
}
