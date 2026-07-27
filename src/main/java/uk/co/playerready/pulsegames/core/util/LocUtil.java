package uk.co.playerready.pulsegames.core.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

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

    /** Locale.ROOT is load-bearing: parse() splits on ',', so a comma-decimal default
     *  locale ("12,50") would write coordinates that read back as different numbers. */
    public static String serialize(Location loc) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }
}
