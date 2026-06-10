package uk.co.playerready.pulsegames.core.util;

import org.bukkit.Location;
import org.bukkit.World;

/** Serializes locations as "x,y,z[,yaw,pitch]" relative to a world chosen at runtime. */
public final class LocUtil {

    private LocUtil() {
    }

    public static Location parse(String raw, World world) {
        String[] parts = raw.trim().split("\\s*,\\s*");
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);
        float yaw = parts.length > 3 ? Float.parseFloat(parts[3]) : 0f;
        float pitch = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static String serialize(Location loc) {
        return "%.2f,%.2f,%.2f,%.1f,%.1f".formatted(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }
}
