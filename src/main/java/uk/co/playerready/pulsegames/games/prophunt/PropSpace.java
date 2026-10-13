package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Test the chosen prop footprint, not two spare cells above a full-height player. */
final class PropSpace {
    private PropSpace() {}
    static boolean clear(Location feet, double height) {
        World world = feet.getWorld();
        if (world == null || !Double.isFinite(feet.getX()) || !Double.isFinite(feet.getY()) || !Double.isFinite(feet.getZ())) return false;
        BoundingBox box = bounds(feet, height).expand(-.001);
        if (box.getMinY() < world.getMinHeight() || box.getMaxY() >= world.getMaxHeight()) return false;
        var border = world.getWorldBorder();
        if (border != null && (!border.isInside(new Location(world, box.getMinX(), feet.getY(), box.getMinZ()))
                || !border.isInside(new Location(world, box.getMaxX(), feet.getY(), box.getMaxZ())))) return false;
        for (int x = floor(box.getMinX()); x <= floor(box.getMaxX()); x++)
            for (int z = floor(box.getMinZ()); z <= floor(box.getMaxZ()); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = floor(box.getMinY()); y <= floor(box.getMaxY()); y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (block.isLiquid() || (!block.isPassable() && block.getBoundingBox().overlaps(box))) return false;
                }
            }
        return true;
    }
    static boolean supported(Location feet) {
        if (feet.getWorld() == null) return false;
        var below = feet.clone().subtract(0, .02, 0);
        var floor = feet.getWorld().getBlockAt(below.getBlockX(), below.getBlockY(), below.getBlockZ());
        return !floor.isPassable() && !floor.isLiquid() && Math.abs(floor.getBoundingBox().getMaxY() - feet.getY()) < .04;
    }
    static BoundingBox bounds(Location feet, double height) {
        double half = height / 2;
        return new BoundingBox(feet.getX()-half, feet.getY(), feet.getZ()-half,
                feet.getX()+half, feet.getY()+height, feet.getZ()+half);
    }
    private static int floor(double value) { return (int)Math.floor(value); }
}
