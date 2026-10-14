package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import java.util.UUID;

/** Last seen evidence expires: no tracking a hidden player through walls. */
final class PropBotMemory {
    private final java.util.Map<UUID, Location> visiblePositions = new java.util.HashMap<>();
    private UUID target;
    private Location seen;
    private long expires;

    void observe(UUID candidate, Location point, boolean visible, boolean moving, long tick) {
        if (!visible) { visiblePositions.remove(candidate); return; }
        Location previous = visiblePositions.put(candidate, point.clone());
        // Client-driven walking need not have a useful Bukkit velocity; compare two visible samples.
        moving |= previous != null && previous.getWorld() == point.getWorld() && previous.distanceSquared(point) > .0025;
        if (!moving && !(candidate.equals(target) && tick <= expires)) return;
        if (target != null && !candidate.equals(target) && tick <= expires) return;
        target = candidate; seen = point.clone(); expires = tick + 100;
    }
    Location destination(long tick) { return seen == null || tick > expires ? null : seen.clone(); }
    UUID target() { return target; }
}
