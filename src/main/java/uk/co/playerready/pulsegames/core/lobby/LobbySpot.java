package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/** A saved position that does not need the world to be loaded: world name plus coordinates. */
public record LobbySpot(String world, double x, double y, double z, float yaw, float pitch) {

    public static LobbySpot of(Location l) {
        return new LobbySpot(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation(World w) { return new Location(w, x, y, z, yaw, pitch); }

    /** Squared distance, or infinity when the spots are in different worlds. */
    public double distanceSq(LobbySpot o) {
        if (!world.equals(o.world)) return Double.POSITIVE_INFINITY;
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean sameBlock(LobbySpot o) {
        return world.equals(o.world) && Math.floor(x) == Math.floor(o.x) && Math.floor(y) == Math.floor(o.y) && Math.floor(z) == Math.floor(o.z);
    }

    public int blockX() { return (int) Math.floor(x); }
    public int blockZ() { return (int) Math.floor(z); }

    public String serialise() {
        return String.format(Locale.ROOT, "%s;%.2f,%.2f,%.2f,%.1f,%.1f", world, x, y, z, yaw, pitch);
    }

    public static LobbySpot parse(String raw) {
        int cut = raw.lastIndexOf(';');
        if (cut < 0) throw new IllegalArgumentException("Missing world in '" + raw + "'");
        String[] p = raw.substring(cut + 1).split(",");
        if (p.length != 5) throw new IllegalArgumentException("Expected x,y,z,yaw,pitch in '" + raw + "'");
        return new LobbySpot(raw.substring(0, cut), Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                Double.parseDouble(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4]));
    }
}
