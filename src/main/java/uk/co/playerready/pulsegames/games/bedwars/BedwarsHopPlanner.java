package uk.co.playerready.pulsegames.games.bedwars;

import uk.co.playerready.pulsegames.games.bedwars.BedwarsBridge.Cell;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBridge.Island;
import uk.co.playerready.pulsegames.games.bedwars.BedwarsBridge.Terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Island hopping for bridging bots. Every hop is a straight bridge of at most {@code maxGap} void cells (a hard
 * limit, never a weight), and the route with the fewest bridged cells wins, then the fewest hops. Pure: the game
 * supplies the terrain and the islands (team bases and diamond islands).
 */
final class BedwarsHopPlanner {
    private BedwarsHopPlanner() {}

    /** One bridge: stand on {@code to} afterwards. {@code gap} void cells are bridged to get there. */
    record Hop(Cell to, int gap) {}

    /** Spare blocks over the bare gap, for a misplaced block or a knock back. */
    static final double MARGIN = 1.25;

    /** Blocks a bot should carry for a bridge of {@code gap} cells: gap + 25%, rounded up; none for a walk. */
    static int blocksNeeded(int gap) { return gap <= 0 ? 0 : (int) Math.ceil(gap * MARGIN); }

    /** Blocks for the whole route, or for its first {@code hops} hops. */
    static int blocksNeeded(List<Hop> route, int hops) {
        int total = 0;
        for (int i = 0; i < Math.min(hops, route.size()); i++) total += blocksNeeded(route.get(i).gap());
        return total;
    }

    /** The cheapest route from {@code from} to {@code dest} over the islands, or null when no chain of hops fits {@code maxGap}. */
    static List<Hop> plan(Terrain terrain, Cell from, Cell dest, List<Island> islands, int maxGap) {
        int n = islands.size() + 2; // 0 = start, 1..n-2 = islands, n-1 = destination
        Cell[] at = new Cell[n];
        at[0] = from; at[n - 1] = dest;
        for (int i = 0; i < islands.size(); i++) at[i + 1] = islands.get(i).target();
        long[] cost = new long[n]; Arrays.fill(cost, Long.MAX_VALUE); cost[0] = 0;
        int[] via = new int[n]; Arrays.fill(via, -1);
        int[] gapTo = new int[n];
        boolean[] done = new boolean[n];
        for (int round = 0; round < n; round++) {
            int u = -1;
            for (int i = 0; i < n; i++) if (!done[i] && cost[i] != Long.MAX_VALUE && (u < 0 || cost[i] < cost[u])) u = i;
            if (u < 0 || u == n - 1) break;
            done[u] = true;
            for (int v = 1; v < n; v++) {
                if (done[v] || v == u) continue;
                int gap = BedwarsBridge.gap(terrain, at[u], at[v], maxGap);
                if (gap > maxGap) continue;
                long next = cost[u] + gap * 100L + 1; // fewest bridged cells first, then fewest hops
                if (next < cost[v]) { cost[v] = next; via[v] = u; gapTo[v] = gap; }
            }
        }
        if (cost[n - 1] == Long.MAX_VALUE) return null;
        List<Hop> reversed = new ArrayList<>();
        for (int v = n - 1; v > 0; v = via[v]) reversed.add(new Hop(at[v], gapTo[v]));
        List<Hop> route = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) route.add(reversed.get(i));
        return route;
    }
}
