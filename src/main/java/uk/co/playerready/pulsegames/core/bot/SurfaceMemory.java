package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;

/** A small trail of visited cells, not knowledge of hidden or future hazards. */
public final class SurfaceMemory {
    private record Cell(int x, int y, int z) {
        static Cell at(Location p) { return new Cell(p.getBlockX(), p.getBlockY(), p.getBlockZ()); }
    }
    private final Map<UUID, Location> destinations = new HashMap<>();
    private final Map<UUID, Deque<Cell>> trails = new HashMap<>();
    public void remember(Player bot) {
        var trail = trails.computeIfAbsent(bot.getUniqueId(), key -> new ArrayDeque<>());
        var cell = Cell.at(bot.getLocation());
        if (!cell.equals(trail.peekLast())) {
            trail.addLast(cell);
            if (trail.size() > 24) trail.removeFirst();
        }
    }
    public double penalty(Player bot, Location point) {
        var trail = trails.get(bot.getUniqueId());
        if (trail == null) return 0;
        var cell = Cell.at(point);
        return trail.stream().filter(cell::equals).count() * 3.0;
    }
    public Location nextStep(Player bot, java.util.function.Predicate<Location> allowed,
                             java.util.function.ToDoubleFunction<Location> score) {
        var here = bot.getLocation();
        var previous = destinations.get(bot.getUniqueId());
        // A player can stand on the edge of the next block while their centre is over air.
        // Finish that legal step instead of cancelling navigation halfway up a stair.
        if (previous != null && previous.getWorld().equals(here.getWorld())
                && here.distanceSquared(previous) < 4
                && (!Cell.at(here).equals(Cell.at(previous)) || !GroundNavigation.walkable(here))
                && allowed.test(previous) && GroundNavigation.walkable(previous)) return previous.clone();
        var next = GroundNavigation.nextStep(here, allowed, score);
        if (next == null) destinations.remove(bot.getUniqueId());
        else destinations.put(bot.getUniqueId(), next.clone());
        return next;
    }
    public void clear() { trails.clear(); destinations.clear(); }
}
