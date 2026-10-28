package uk.co.playerready.pulsegames.games.common;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block-by-block snapshot of regions, restorable in budgeted steps. This generalises Party Games'
 * floor snapshot so any game can put an arena back between rounds without stalling a tick on a
 * large box. It can be filled up front from regions ({@link #capture}) or lazily as a journal
 * ({@link #remember}) for games like TNT Run that define no floor region.
 */
public final class RegionSnapshot {

    /** One documented limit for every rounds-mode snapshot: each cell holds a Block plus a cloned BlockData. */
    public static final long MAX_BLOCKS = 300_000L;

    private final Map<Block, BlockData> cells = new LinkedHashMap<>();
    private List<Map.Entry<Block, BlockData>> pending = List.of();
    private int cursor;

    public static RegionSnapshot empty() { return new RegionSnapshot(); }

    /** Total cells the regions would capture (overlaps counted twice, which only errs on the safe side). */
    public static long volume(Collection<Cuboid> regions) {
        long total = 0;
        for (Cuboid region : regions) total = Math.min(Long.MAX_VALUE / 2, total + region.volume());
        return total;
    }

    /**
     * Null when the regions fit {@link #MAX_BLOCKS}; otherwise the lobby-facing refusal, which is also sent to
     * {@code console} so an admin sees why rounds mode will not start on this arena.
     */
    public static String refusal(String game, Collection<Cuboid> regions, java.util.function.Consumer<String> console) {
        long blocks = volume(regions);
        if (blocks <= MAX_BLOCKS) return null;
        console.accept(game + " rounds mode refused: the arena snapshot would hold " + blocks
                + " blocks, over the " + MAX_BLOCKS + " limit. Shrink the region or use another mode.");
        return game + " rounds need an arena under " + MAX_BLOCKS + " blocks to snapshot";
    }

    /** Cells still waiting to be put back by {@link #restoreStep}. */
    public int remaining() { return Math.max(0, pending.size() - cursor); }

    /** Capture the current state of every region (overlapping cells are stored once). */
    public static RegionSnapshot capture(World world, Collection<Cuboid> regions) {
        RegionSnapshot snapshot = new RegionSnapshot();
        for (Cuboid region : regions) region.blocks(world).forEach(snapshot::remember);
        return snapshot;
    }

    /** Record a block's current state if it has not been recorded yet; call before changing it. */
    public void remember(Block block) {
        if (!cells.containsKey(block)) cells.put(block, block.getBlockData().clone());
    }

    public int size() { return cells.size(); }

    public void beginRestore() {
        pending = new ArrayList<>(cells.entrySet());
        cursor = 0;
    }

    /** Restore up to {@code max} cells; true once every cell has been put back. */
    public boolean restoreStep(int max) {
        int end = (int) Math.min(pending.size(), (long) cursor + Math.max(1, max));
        for (; cursor < end; cursor++) {
            Map.Entry<Block, BlockData> cell = pending.get(cursor);
            if (!cell.getValue().equals(cell.getKey().getBlockData())) cell.getKey().setBlockData(cell.getValue(), false);
        }
        return cursor >= pending.size();
    }

    /** Finish an in-progress restore in one go. */
    public void finishRestore() { restoreStep(Integer.MAX_VALUE); }

    /** Full restore from the start, in one go. */
    public void restoreAll() {
        beginRestore();
        finishRestore();
    }
}
