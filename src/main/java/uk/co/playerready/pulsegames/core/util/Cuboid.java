package uk.co.playerready.pulsegames.core.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.AbstractList;
import java.util.List;

/** Axis-aligned block region defined in template-world coordinates. */
public final class Cuboid {

    /**
     * Games walk whole regions block by block on the main thread (Spleef's floors,
     * Block Party's dance floor...), so a region big enough to OOM the server must
     * never reach the arena pool in the first place - it is rejected here, at
     * construction, rather than at the point it would hang a round.
     */
    public static final long MAX_BLOCKS = 1_000_000L;

    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public Cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        long volume = volume();
        if (volume > MAX_BLOCKS) {
            throw new IllegalArgumentException("Region spans " + volume
                    + " blocks; the limit is " + MAX_BLOCKS
                    + " (corners " + minX + "," + minY + "," + minZ
                    + " to " + maxX + "," + maxY + "," + maxZ + ")");
        }
    }

    public static Cuboid parse(String min, String max) {
        int[] a = corner(min, "min");
        int[] b = corner(max, "max");
        return new Cuboid(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    private static int[] corner(String raw, String label) {
        if (raw == null) throw new IllegalArgumentException("missing '" + label + "' corner");
        String[] parts = raw.trim().split("\\s*,\\s*");
        if (parts.length < 3) {
            throw new IllegalArgumentException("'" + label + "' corner needs \"x,y,z\", got \"" + raw + "\"");
        }
        return new int[]{
                (int) Double.parseDouble(parts[0]),
                (int) Double.parseDouble(parts[1]),
                (int) Double.parseDouble(parts[2])};
    }

    /** Block count, saturating at {@link Long#MAX_VALUE} so a wide region can't
     *  overflow into a small number and slip past the limit. */
    public long volume() {
        return saturatingMul(saturatingMul((long) maxX - minX + 1, (long) maxY - minY + 1),
                (long) maxZ - minZ + 1);
    }

    private static long saturatingMul(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public boolean contains(Location loc) {
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    public boolean containsColumn(Location loc) {
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    /**
     * Lazy view - blocks are fetched as they are iterated rather than materialised
     * into a list up front, so walking a region costs no heap of its own.
     */
    public List<Block> blocks(World world) {
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int size = (int) volume();
        return new AbstractList<>() {
            @Override
            public Block get(int index) {
                if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
                int x = index / (sizeY * sizeZ);
                int rest = index - x * sizeY * sizeZ;
                return world.getBlockAt(minX + x, minY + rest / sizeZ, minZ + rest % sizeZ);
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }

    public Location center(World world) {
        return new Location(world, (minX + maxX) / 2.0 + 0.5, minY, (minZ + maxZ) / 2.0 + 0.5);
    }
}
