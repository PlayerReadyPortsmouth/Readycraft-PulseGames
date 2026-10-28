package uk.co.playerready.pulsegames.games.karts;

import java.util.List;

/** Bullet item numbers and route lookup. The bullet is the bot driver steering a human kart along the bot route. */
final class KartBullet {
    static final int TICKS = 160;
    static final double FACTOR = 1.60, SPEED_SCALE = 1.5, MAX_OFF_ROUTE = 14;
    private KartBullet() {}

    /** Index of the waypoint ending the route segment nearest to (x, z): where the autopilot should head first. */
    static int nextWaypoint(List<KartBotDriver.Point> route, double x, double z) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double distance = distanceToSegment(route.get(i), route.get((i + 1) % route.size()), x, z);
            if (distance < bestDistance) { bestDistance = distance; best = (i + 1) % route.size(); }
        }
        return best;
    }
    static double distanceToRoute(List<KartBotDriver.Point> route, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++)
            best = Math.min(best, distanceToSegment(route.get(i), route.get((i + 1) % route.size()), x, z));
        return best;
    }
    private static double distanceToSegment(KartBotDriver.Point a, KartBotDriver.Point b, double x, double z) {
        return KartItemBoxes.segmentDistance(a.x(), 0, a.z(), b.x(), 0, b.z(), x, 0, z);
    }
}
