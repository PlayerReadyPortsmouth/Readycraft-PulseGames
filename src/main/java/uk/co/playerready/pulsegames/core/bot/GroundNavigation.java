package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Bounded, cardinal surface search. Returns one reachable step, never a jump over a hole. */
public final class GroundNavigation {
    private GroundNavigation() {}
    private record Cell(int x, int y, int z) {
        Location centre(World world) { return new Location(world, x + 0.5, y, z + 0.5); }
    }
    private record Visit(Cell cell, Cell first, int depth) {}
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1}};

    public static Location nextStep(Location from, Predicate<Location> allowed, ToDoubleFunction<Location> score) {
        return nextStep(from, allowed, score, GroundNavigation::walkable);
    }

    static Location nextStep(Location from, Predicate<Location> allowed, ToDoubleFunction<Location> score, Predicate<Location> surface) {
        World world = from.getWorld();
        if (world == null) return null;
        Cell start = new Cell(from.getBlockX(), from.getBlockY(), from.getBlockZ());
        Map<Cell, Boolean> surfaces = new HashMap<>();
        Predicate<Cell> valid = cell -> surfaces.computeIfAbsent(cell,
                key -> allowed.test(key.centre(world)) && surface.test(key.centre(world)));
        if (!valid.test(start)) return null;
        double bestScore = score.applyAsDouble(start.centre(world));
        Cell best = null;
        Set<Cell> seen = new HashSet<>(); seen.add(start);
        ArrayDeque<Visit> queue = new ArrayDeque<>(); queue.add(new Visit(start, null, 0));
        int visits = 0;
        while (!queue.isEmpty() && visits++ < 32) {
            Visit visit = queue.removeFirst();
            if (visit.depth() == 3) continue;
            for (int[] direction : DIRECTIONS) {
                for (int dy : new int[]{0, 1, -1}) {
                    Cell cell = new Cell(visit.cell().x() + direction[0], visit.cell().y() + dy, visit.cell().z() + direction[1]);
                    if (!valid.test(cell)) continue;
                    // Stepping up also needs clearance above the departure position.
                    if (dy > 0 && !world.getBlockAt(visit.cell().x(), visit.cell().y() + 2, visit.cell().z()).isPassable()) continue;
                    if (!seen.add(cell)) break;
                    Cell first = visit.first() == null ? cell : visit.first();
                    int depth = visit.depth() + 1;
                    double candidate = score.applyAsDouble(cell.centre(world)) - depth * 0.08;
                    if (candidate > bestScore + 0.001) { bestScore = candidate; best = first; }
                    queue.addLast(new Visit(cell, first, depth));
                    break;
                }
            }
        }
        return best == null ? null : best.centre(world);
    }

    public static boolean walkable(Location feet) {
        var world = feet.getWorld(); if (world == null) return false;
        int x = feet.getBlockX(), y = feet.getBlockY(), z = feet.getBlockZ();
        var ground = world.getBlockAt(x, y - 1, z);
        var body = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        return safeGround(ground.getType()) && body.isPassable() && !body.isLiquid()
                && head.isPassable() && !head.isLiquid()
                && harmless(body.getType()) && harmless(head.getType());
    }
    private static boolean safeGround(Material material) { return material.isSolid() && harmless(material); }
    private static boolean harmless(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW, SWEET_BERRY_BUSH -> false;
            default -> true;
        };
    }
}
