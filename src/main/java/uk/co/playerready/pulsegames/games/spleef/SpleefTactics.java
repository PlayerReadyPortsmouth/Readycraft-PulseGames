package uk.co.playerready.pulsegames.games.spleef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Pure floor reasoning for spleef bots: what to dig, what is safe to dig, where is comfortable. No Bukkit. */
final class SpleefTactics {
    private SpleefTactics() {}

    /** One floor level; true where a block is still there. */
    interface Grid { boolean solid(int x, int z); }
    record Cell(int x, int z) {}
    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static int neighbours(Grid g, int x, int z) {
        int n = 0;
        for (int[] d : DIRS) if (g.solid(x + d[0], z + d[1])) n++;
        return n;
    }

    /** Intact cells within two cells (5x5, excluding the centre): thin bridges score low. */
    static int density(Grid g, int x, int z) {
        int n = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if ((dx != 0 || dz != 0) && g.solid(x + dx, z + dz)) n++;
        return n;
    }

    /** Higher is better to stand on. A cell that is cracking is poison; its neighbours count for less too. */
    static double comfort(Grid g, Grid cracking, int x, int z) {
        double score = density(g, x, z) * 0.25 + neighbours(g, x, z) * 0.5;
        if (cracking.solid(x, z)) score -= 30;
        for (int[] d : DIRS) if (cracking.solid(x + d[0], z + d[1])) score -= 1.5;
        return score;
    }

    /** Cells reachable on foot from {@code from} (cardinal), ignoring {@code removed}; capped. */
    static int reach(Grid g, Cell from, Cell removed, int cap) {
        Set<Cell> seen = new HashSet<>(); ArrayDeque<Cell> queue = new ArrayDeque<>();
        seen.add(from); queue.add(from);
        while (!queue.isEmpty() && seen.size() < cap) {
            Cell c = queue.removeFirst();
            for (int[] d : DIRS) {
                Cell n = new Cell(c.x() + d[0], c.z() + d[1]);
                if (n.equals(removed) || !g.solid(n.x(), n.z()) || !seen.add(n)) continue;
                queue.addLast(n);
            }
        }
        return seen.size();
    }

    /** Never the bot's own block, never its last exit, never something that strands it on a smaller island. */
    static boolean safeToDig(Grid g, Cell self, Cell dig, int minExits) {
        if (dig.equals(self) || !g.solid(dig.x(), dig.z())) return false;
        int exits = 0;
        for (int[] d : DIRS) {
            Cell n = new Cell(self.x() + d[0], self.z() + d[1]);
            if (!n.equals(dig) && g.solid(n.x(), n.z())) exits++;
        }
        if (exits < minExits) return false;
        return reach(g, self, dig, 6) >= Math.min(6, reach(g, self, null, 6) - 1);
    }

    /** The cell the enemy will be standing on in about {@code ticks}, lead capped at {@code maxLead} blocks. */
    static Cell ahead(double ex, double ez, double vx, double vz, double ticks, double maxLead) {
        double lx = vx * ticks, lz = vz * ticks, len = Math.hypot(lx, lz);
        if (len > maxLead) { lx *= maxLead / len; lz *= maxLead / len; }
        if (len < 0.3) { lx = 0; lz = 0; }
        return new Cell((int) Math.floor(ex + lx), (int) Math.floor(ez + lz));
    }

    /** A cell about two blocks out on the line from the bot to an approaching enemy; widens to 3 then 1. */
    static Cell trench(Grid g, Cell self, double bx, double bz, double ex, double ez, int minExits) {
        double dx = ex - bx, dz = ez - bz, len = Math.hypot(dx, dz);
        if (len < 2.5) return null;
        for (double d : new double[]{2, 3, 1}) {
            Cell c = new Cell((int) Math.floor(bx + dx / len * d), (int) Math.floor(bz + dz / len * d));
            if (!c.equals(new Cell((int) Math.floor(ex), (int) Math.floor(ez))) && safeToDig(g, self, c, minExits)) return c;
        }
        return null;
    }

    /**
     * Classic digging priorities, best first, already filtered for safety: block under an adjacent enemy,
     * a trench ahead of an approaching enemy (hard bots lead with it), the predicted-ahead block, the block
     * under the enemy, then a trench as a last resort.
     */
    static List<Cell> classicTargets(Grid g, Cell self, double bx, double bz, double ex, double ez,
                                     double vx, double vz, boolean hard, int minExits) {
        List<Cell> out = new ArrayList<>();
        Cell under = new Cell((int) Math.floor(ex), (int) Math.floor(ez));
        Cell predicted = ahead(ex, ez, vx, vz, 8, 2);
        boolean adjacent = Math.hypot(ex - bx, ez - bz) <= 2.2;
        boolean approaching = (vx * (bx - ex) + vz * (bz - ez)) > 0.02 * Math.hypot(bx - ex, bz - ez);
        Cell trench = trench(g, self, bx, bz, ex, ez, minExits);
        if (adjacent) add(out, g, self, under, minExits);
        if (hard && approaching) add(out, g, self, trench, minExits);
        add(out, g, self, predicted, minExits);
        add(out, g, self, under, minExits);
        add(out, g, self, trench, minExits);
        return out;
    }

    private static void add(List<Cell> out, Grid g, Cell self, Cell c, int minExits) {
        if (c != null && !out.contains(c) && safeToDig(g, self, c, minExits)) out.add(c);
    }

    /** A random neighbour for a bot with nowhere better to go; null when none qualifies. */
    static Cell randomNeighbour(Cell self, IntUnaryOperator random, Predicate<Cell> ok) {
        int start = random.applyAsInt(4);
        for (int i = 0; i < 4; i++) {
            int[] d = DIRS[(start + i) % 4];
            Cell c = new Cell(self.x() + d[0], self.z() + d[1]);
            if (ok.test(c)) return c;
        }
        return null;
    }

    /** Fires once when the same block has been held for {@code limit} ticks while the bot wanted to move. */
    static final class StuckWatch {
        private long since = -1; private int x, z;
        boolean update(long tick, int bx, int bz, boolean wantsMove, int limit) {
            if (!wantsMove || since < 0 || bx != x || bz != z) { since = tick; x = bx; z = bz; return false; }
            if (tick - since < limit) return false;
            since = tick;
            return true;
        }
    }
}
