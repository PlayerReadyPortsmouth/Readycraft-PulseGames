package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.Locale;

/** Absolute, bounded coordinates; parsing never loads chunks or moves a player. */
final class SetupCoordinates {
    private SetupCoordinates() {}
    static String key(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Use a name containing only letters, numbers, hyphens or underscores (up to 64).");
        return key;
    }
    static Location point(World world, Location current, String[] args, int offset, boolean corner) {
        int count = args.length - offset;
        if (count == 0) return current.clone();
        if (count != 3 && !(count == 5 && !corner))
            throw new IllegalArgumentException(corner ? "Supply x y z, or omit coordinates to use your position." : "Supply x y z, optionally yaw pitch, or omit coordinates to use your position.");
        double x = number(args[offset]), y = number(args[offset + 1]), z = number(args[offset + 2]);
        if (Math.abs(x) > 29_999_984 || Math.abs(z) > 29_999_984 || y < world.getMinHeight() || y >= world.getMaxHeight())
            throw new IllegalArgumentException("Coordinates are outside the map world's height or coordinate limits.");
        if (corner && (x != Math.rint(x) || y != Math.rint(y) || z != Math.rint(z)))
            throw new IllegalArgumentException("Region corners must use whole block coordinates.");
        double yaw = count == 5 ? number(args[offset + 3]) : 0;
        double pitch = count == 5 ? number(args[offset + 4]) : 0;
        if (Math.abs(yaw) > 360 || Math.abs(pitch) > 90) throw new IllegalArgumentException("Yaw must be -360..360 and pitch -90..90.");
        return new Location(world, x, y, z, (float) yaw, (float) pitch);
    }
    private static double number(String value) {
        try {
            double result = Double.parseDouble(value);
            if (Double.isFinite(result)) return result;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Coordinates must be finite absolute numbers; relative (~ or ^) coordinates are not supported.");
    }
}
