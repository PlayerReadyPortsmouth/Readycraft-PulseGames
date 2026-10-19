package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Real-blocks mode: the one place that writes disguise blocks into the world. Every written
 * cell is tracked with the block it replaced, so each cleanup path restores through
 * {@link #release}. For a DECOY the owner is the decoy id, not the hider.
 */
final class PropBlocks {
    enum Kind { HIDER, DECOY }
    record Placed(UUID owner, Kind kind, BlockData original) {}
    private record Cell(int x, int y, int z) {}
    private record Entry(Block block, Placed placed) {}

    private final Map<Cell, Entry> cells = new HashMap<>();
    private final Function<Material, BlockData> factory;

    PropBlocks(Function<Material, BlockData> factory) { this.factory = factory; }

    private static Cell cell(Block block) { return new Cell(block.getX(), block.getY(), block.getZ()); }

    /** What the owner is shown in place of their own disguise block. */
    BlockData air() { return factory.apply(Material.AIR); }
    boolean tracked(Block block) { return block != null && cells.containsKey(cell(block)); }
    Placed at(Block block) { Entry entry = block == null ? null : cells.get(cell(block)); return entry == null ? null : entry.placed(); }
    boolean hasHider(UUID id) { return hiderCell(id) != null; }
    Block hiderCell(UUID id) {
        for (Entry entry : cells.values()) if (entry.placed().kind() == Kind.HIDER && entry.placed().owner().equals(id)) return entry.block();
        return null;
    }

    /** Derived from the collision shape, never from a material list: only a single full cube qualifies. */
    boolean qualifies(Material material, Location at) {
        return fullCube(factory.apply(material), at);
    }
    static boolean fullCube(BlockData data, Location at) {
        var boxes = data.getCollisionShape(at).getBoundingBoxes();
        if (boxes.size() != 1) return false;
        BoundingBox box = boxes.iterator().next();
        double e = 1e-6;
        return box.getMinX() < e && box.getMinY() < e && box.getMinZ() < e
                && box.getMaxX() > 1 - e && box.getMaxY() > 1 - e && box.getMaxZ() > 1 - e;
    }

    /** Only passable, fluid-free, single-block cells with no stored state may be overwritten. */
    static boolean replaceable(Block block) {
        if (!block.isPassable() || block.isLiquid()) return false;
        if (block.getState(false) instanceof TileState) return false;
        BlockData data = block.getBlockData();
        return !(data instanceof Bisected || data instanceof Bed);
    }

    /** Logs stand upright; barrels close upward; other facing blocks face the given direction. */
    static BlockData orient(BlockData data, Material material, BlockFace facing) {
        if (data instanceof Orientable orientable) orientable.setAxis(Axis.Y);
        else if (data instanceof Directional directional) {
            BlockFace face = material == Material.BARREL ? BlockFace.UP : facing;
            if (directional.getFaces().contains(face)) directional.setFacing(face);
        }
        return data;
    }

    /** False (and no write) if the cell is already tracked, not replaceable, or the owner already has a hider block. */
    boolean place(Block block, UUID owner, Kind kind, Material material, BlockFace facing) {
        if (tracked(block) || !replaceable(block) || (kind == Kind.HIDER && hasHider(owner))) return false;
        BlockData original = block.getBlockData().clone();
        cells.put(cell(block), new Entry(block, new Placed(owner, kind, original)));
        block.setBlockData(orient(factory.apply(material), material, facing), false);
        return true;
    }

    Block release(Block block) {
        Entry entry = block == null ? null : cells.remove(cell(block));
        if (entry == null) return null;
        restore(entry);
        return entry.block();
    }
    Block releaseHider(UUID id) { return release(hiderCell(id)); }
    void releaseAll() {
        for (Entry entry : new ArrayList<>(cells.values())) restore(entry);
        cells.clear();
    }
    /** Skipped for an unloaded chunk: the throwaway round world is discarded anyway. */
    private static void restore(Entry entry) {
        World world = entry.block().getWorld();
        if (world != null && world.isChunkLoaded(entry.block().getX() >> 4, entry.block().getZ() >> 4))
            entry.block().setBlockData(entry.placed().original(), false);
    }
}
