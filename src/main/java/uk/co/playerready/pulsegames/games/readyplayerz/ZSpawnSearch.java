package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import uk.co.playerready.pulsegames.core.util.Cuboid;

/** Samples authored spawn volumes at spawn time, without loading chunks. */
public final class ZSpawnSearch {
    static Optional<Location> choose(World world, List<Cuboid> regions, List<Player> players, double min, double max,
        double height, Predicate<Location> allowed, RandomGenerator random) {
        if (world == null || regions.isEmpty() || players.isEmpty())
            return Optional.empty();
        for (int attempt = 0; attempt < 64; attempt++) {
            Cuboid region = regions.get(random.nextInt(regions.size()));
            Location feet = new Location(world, random.nextInt(region.minX(), region.maxX() + 1) + .5,
                random.nextInt(region.minY(), region.maxY() + 1),
                random.nextInt(region.minZ(), region.maxZ() + 1) + .5);
            double distance = players.stream()
                                  .filter(p -> p.getWorld() == world)
                                  .mapToDouble(p -> p.getLocation().distanceSquared(feet))
                                  .min()
                                  .orElse(Double.POSITIVE_INFINITY);
            if (distance < min * min || distance > max * max || !allowed.test(feet) || !safe(feet, height))
                continue;
            return Optional.of(feet);
        }
        return Optional.empty();
    }
    public static boolean safe(Location feet, double height) {
        World world = feet.getWorld();
        if (world == null || !Double.isFinite(height) || height < 1 || height > 4)
            return false;
        int x = feet.getBlockX(), y = feet.getBlockY(), z = feet.getBlockZ();
        if (y <= world.getMinHeight() || feet.getY() + height >= world.getMaxHeight()
            || !world.isChunkLoaded(x >> 4, z >> 4))
            return false;
        if (world.getWorldBorder() != null && !world.getWorldBorder().isInside(feet))
            return false;
        var floor = world.getBlockAt(x, y - 1, z);
        if (floor.isLiquid() || floor.isPassable() || Math.abs(floor.getBoundingBox().getMaxY() - feet.getY()) > .05)
            return false;
        BoundingBox body = new BoundingBox(feet.getX() - .49, feet.getY() + .001, feet.getZ() - .49, feet.getX() + .49,
            feet.getY() + height, feet.getZ() + .49);
        for (int by = y; by <= Math.floor(body.getMaxY()); by++) {
            var block = world.getBlockAt(x, by, z);
            if (block.isLiquid() || (!block.isPassable() && block.getBoundingBox().overlaps(body)))
                return false;
        }
        return true;
    }
}
