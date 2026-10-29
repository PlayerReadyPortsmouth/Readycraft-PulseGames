package uk.co.playerready.pulsegames.games.bedwars;

import java.util.List;

/**
 * Pure bridge planning on a cell grid. A cell is a feet position; the bot stands on {@code solid(x, y-1, z)}.
 * Paths are four-connected (no diagonal leaps) but follow the straight line from where bridging began to the
 * target, so a diagonal run becomes a staircase. Height follows the line one block at a time.
 */
public final class BedwarsBridge {
    private BedwarsBridge() {}

    public interface Terrain { boolean solid(int x, int y, int z); }
    public record Cell(int x, int y, int z) {}
    public record Island(String name, Cell target) {}

    /** The next cell towards {@code to}, or null when already within one cell horizontally and at height. */
    public static Cell next(Cell anchor, Cell at, Cell to) {
        int dx = to.x() - at.x(), dz = to.z() - at.z();
        if (dx == 0 && dz == 0) return null;
        Cell stepX = dx == 0 ? null : new Cell(at.x() + Integer.signum(dx), at.y(), at.z());
        Cell stepZ = dz == 0 ? null : new Cell(at.x(), at.y(), at.z() + Integer.signum(dz));
        Cell pick;
        if (stepX == null) pick = stepZ; else if (stepZ == null) pick = stepX;
        else pick = deviation(anchor, to, stepX) <= deviation(anchor, to, stepZ) ? stepX : stepZ;
        int total = Math.abs(to.x() - anchor.x()) + Math.abs(to.z() - anchor.z());
        int travelled = Math.abs(pick.x() - anchor.x()) + Math.abs(pick.z() - anchor.z());
        int wanted = total == 0 ? to.y() : anchor.y() + (int) Math.round((to.y() - anchor.y()) * (double) travelled / total);
        int y = at.y() + Math.max(-1, Math.min(1, wanted - at.y()));
        return new Cell(pick.x(), y, pick.z());
    }

    private static double deviation(Cell a, Cell t, Cell p) {
        double lx = t.x() - a.x(), lz = t.z() - a.z(), len = Math.hypot(lx, lz);
        if (len == 0) return 0;
        return Math.abs(lx * (p.z() - a.z()) - lz * (p.x() - a.x())) / len;
    }

    /** Void cells (no floor) the staircase from {@code from} to {@code to} would have to bridge; {@code max+1} when too far. */
    public static int gap(Terrain terrain, Cell from, Cell to, int max) {
        int gaps = 0;
        Cell at = from;
        for (int i = 0; i < 200; i++) {
            Cell next = next(from, at, to);
            if (next == null) break;
            if (!terrain.solid(next.x(), next.y() - 1, next.z())) { gaps++; if (gaps > max) return max + 1; }
            at = next;
        }
        return gaps;
    }

    /** Island with the shortest void gap from here; ties go to the nearer one. Null when none is reachable within {@code max}. */
    public static Island shortestGap(Terrain terrain, Cell from, List<Island> islands, int max) {
        Island best = null; int bestGap = Integer.MAX_VALUE; long bestDistance = Long.MAX_VALUE;
        for (Island island : islands) {
            int gap = gap(terrain, from, island.target(), max);
            if (gap > max) continue;
            long dx = island.target().x() - from.x(), dz = island.target().z() - from.z(), distance = dx * dx + dz * dz;
            if (gap < bestGap || (gap == bestGap && distance < bestDistance)) { best = island; bestGap = gap; bestDistance = distance; }
        }
        return best;
    }
}
