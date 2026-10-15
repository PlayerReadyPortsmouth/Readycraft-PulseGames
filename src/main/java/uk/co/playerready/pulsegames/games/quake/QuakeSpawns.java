package uk.co.playerready.pulsegames.games.quake;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import java.util.random.RandomGenerator;

/** Bounded, loaded-chunk-only placement shared by humans and bots. */
final class QuakeSpawns {
    private QuakeSpawns() {}

    static boolean safe(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return false;
        var floor = world.getBlockAt(x, y - 1, z);
        return floor.getBlockData().isOccluding() && floor.getType() != Material.MAGMA_BLOCK
                && world.getBlockAt(x, y, z).isEmpty()
                && world.getBlockAt(x, y + 1, z).isEmpty();
    }

    static Location choose(World world, Cuboid bounds, List<Location> occupants, List<Location> enemies, RandomGenerator random) {
        int low = Math.max(bounds.minY(), world.getMinHeight() + 1);
        int high = Math.min(bounds.maxY() - 1, world.getMaxHeight() - 2);
        if (low > high) return null;
        List<Location> candidates = new ArrayList<>();
        // At most 1,024 floor probes per spawn, regardless of map volume/height.
        for (int i = 0; i < 1024 && candidates.size() < 32; i++) {
            int x = random.nextInt(bounds.minX(), bounds.maxX() + 1);
            int y = random.nextInt(low, high + 1);
            int z = random.nextInt(bounds.minZ(), bounds.maxZ() + 1);
            if (!safe(world, x, y, z)) continue;
            Location point = new Location(world, x + .5, y, z + .5);
            if (occupants.stream().anyMatch(p -> p.distanceSquared(point) < 4)) continue;
            if (candidates.stream().noneMatch(p -> p.distanceSquared(point) < 1)) candidates.add(point);
        }
        if (candidates.isEmpty()) return null;
        // Randomise among comfortably separated points; use the furthest if the arena is crowded.
        List<Location> separated = candidates.stream().filter(p -> nearest(p, enemies) >= 64).toList();
        Location chosen = separated.isEmpty()
                ? candidates.stream().max(Comparator.comparingDouble(p -> nearest(p, enemies))).orElseThrow()
                : separated.get(random.nextInt(separated.size()));
        chosen.setYaw(random.nextFloat() * 360);
        return chosen;
    }

    private static double nearest(Location point, List<Location> others) {
        return others.stream().mapToDouble(point::distanceSquared).min().orElse(Double.POSITIVE_INFINITY);
    }
}
