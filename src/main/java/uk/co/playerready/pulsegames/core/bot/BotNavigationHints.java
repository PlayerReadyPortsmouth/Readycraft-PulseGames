package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.LocUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Authored navigation hints only: these checks do not prove a traversable path. */
public final class BotNavigationHints {
    public static final int MAX_POINTS = 128;
    private static final Set<Material> HAZARDS = Set.of(Material.FIRE, Material.SOUL_FIRE, Material.LAVA,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.WITHER_ROSE);
    private BotNavigationHints() {}

    /** Reads at most 128 entries, preserving authored order; malformed or presently unsafe hints are omitted. */
    public static List<Location> points(GameInstance game, String key) {
        if (game == null || game.world() == null || game.arena() == null || !allowedKey(key)) return List.of();
        List<String> configured = game.arena().settings().getStringList(key);
        List<Location> result = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_POINTS, configured.size()); i++) {
            try {
                Location point = LocUtil.parse(configured.get(i), game.world());
                if (safe(point)) result.add(point);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // A bad map hint must not stop the match or trigger chunk loading.
            }
        }
        return List.copyOf(result);
    }

    /** Nearest currently safe authored point, not a claim that a navigator can reach it. */
    public static Location nearestSafe(GameInstance game, Location from, String key) {
        if (game == null || from == null || from.getWorld() == null || from.getWorld() != game.world()
                || !Double.isFinite(from.getX()) || !Double.isFinite(from.getY()) || !Double.isFinite(from.getZ())) return null;
        Location nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Location point : points(game, key)) {
            double candidate = from.distanceSquared(point);
            if (candidate < distance) { nearest = point; distance = candidate; }
        }
        return nearest == null ? null : nearest.clone();
    }

    private static boolean allowedKey(String key) {
        return key != null && (key.equals("bot-patrol-points") || key.equals("bot-hide-points")
                || key.equals("bot-recovery-points") || key.matches("bot-route-[a-z0-9_-]{1,48}"));
    }

    private static boolean safe(Location feet) {
        World world = feet.getWorld();
        if (Math.abs(feet.getX()) > 29_999_983 || Math.abs(feet.getZ()) > 29_999_983) return false;
        // A full player footprint is conservative for scaled hiders and valid for seekers too.
        BoundingBox body = new BoundingBox(feet.getX() - .3, feet.getY() + .001, feet.getZ() - .3,
                feet.getX() + .3, feet.getY() + 1.8, feet.getZ() + .3);
        if (feet.getY() < world.getMinHeight() + 1 || body.getMaxY() >= world.getMaxHeight()) return false;
        var border = world.getWorldBorder();
        if (border == null || !border.isInside(new Location(world, body.getMinX(), feet.getY(), body.getMinZ()))
                || !border.isInside(new Location(world, body.getMaxX(), feet.getY(), body.getMaxZ()))) return false;
        int minX = floor(body.getMinX()), maxX = floor(body.getMaxX());
        int minZ = floor(body.getMinZ()), maxZ = floor(body.getMaxZ());
        // Check every footprint chunk before any block access; never force-load map chunks.
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            for (int y = floor(body.getMinY()); y <= floor(body.getMaxY()); y++) {
                var block = world.getBlockAt(x, y, z);
                if (block.isLiquid() || HAZARDS.contains(block.getType())
                        || (!block.isPassable() && block.getBoundingBox().overlaps(body))) return false;
            }
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var floor = world.getBlockAt(x, floor(feet.getY() - .02), z);
            var top = floor.getBoundingBox();
            if (floor.isPassable() || floor.isLiquid() || !BotFloorSafety.footing(floor) || HAZARDS.contains(floor.getType())
                    || Math.abs(top.getMaxY() - feet.getY()) >= .04
                    || top.getMinX() > Math.max(x, body.getMinX()) + .001 || top.getMaxX() < Math.min(x + 1, body.getMaxX()) - .001
                    || top.getMinZ() > Math.max(z, body.getMinZ()) + .001 || top.getMaxZ() < Math.min(z + 1, body.getMaxZ()) - .001) return false;
        }
        return true;
    }
    private static int floor(double value) { return (int) Math.floor(value); }
}
