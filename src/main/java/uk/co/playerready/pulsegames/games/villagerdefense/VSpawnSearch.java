package uk.co.playerready.pulsegames.games.villagerdefense;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.World;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.games.readyplayerz.ZSpawnSearch;

/** Bounded authored-volume search; never loads chunks or falls back to player spawns. */
final class VSpawnSearch {
    static Optional<Location> choose(World world, List<Cuboid> regions, RandomGenerator random) {
        if (world == null || regions.isEmpty()) return Optional.empty();
        for (int attempt = 0; attempt < 64; attempt++) {
            Cuboid region = regions.get(random.nextInt(regions.size()));
            Location feet = new Location(world,
                random.nextInt(region.minX(), region.maxX() + 1) + .5,
                random.nextInt(region.minY(), region.maxY() + 1),
                random.nextInt(region.minZ(), region.maxZ() + 1) + .5);
            if (ZSpawnSearch.safe(feet, 2.2)) return Optional.of(feet);
        }
        return Optional.empty();
    }
}
