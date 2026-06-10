package uk.co.playerready.pulsegames.core.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Axis-aligned block region defined in template-world coordinates. */
public final class Cuboid {

    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public Cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid parse(String min, String max) {
        String[] a = min.trim().split("\\s*,\\s*");
        String[] b = max.trim().split("\\s*,\\s*");
        return new Cuboid(
                (int) Double.parseDouble(a[0]), (int) Double.parseDouble(a[1]), (int) Double.parseDouble(a[2]),
                (int) Double.parseDouble(b[0]), (int) Double.parseDouble(b[1]), (int) Double.parseDouble(b[2]));
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

    public List<Block> blocks(World world) {
        List<Block> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    blocks.add(world.getBlockAt(x, y, z));
        return blocks;
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
