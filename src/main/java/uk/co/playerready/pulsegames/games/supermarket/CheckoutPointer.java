package uk.co.playerready.pulsegames.games.supermarket;

import java.util.Locale;

/** Pure maths and wording for the Supermarket Sweep checkout pointer. */
final class CheckoutPointer {
    private CheckoutPointer() {}

    /** True when the block the player stands in lies inside the inclusive block box. */
    static boolean inside(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    /** Nearest coordinate on one axis of the box [min, max + 1) to the point. */
    static double nearest(double value, int min, int max) {
        return Math.max(min, Math.min(max + 1.0, value));
    }

    /** Straight-line distance in blocks from the point to the nearest point of the inclusive block box. */
    static double distance(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double x, double y, double z) {
        double dx = x - nearest(x, minX, maxX), dy = y - nearest(y, minY, maxY), dz = z - nearest(z, minZ, maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Direction word for a target at (dx, dz) from a player facing {@code yaw} (Minecraft yaw: 0 = south). */
    static String direction(double dx, double dz, float yaw) {
        if (Math.abs(dx) < 1.0e-6 && Math.abs(dz) < 1.0e-6) return "here";
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((bearing - yaw) % 360 + 540) % 360 - 180; // -180..180, positive = to the player's right
        int sector = (int) Math.floor((rel + 22.5 + 360) / 45.0) % 8;
        return switch (sector) {
            case 0 -> "ahead";
            case 1 -> "ahead right";
            case 2 -> "right";
            case 3 -> "behind right";
            case 4 -> "behind";
            case 5 -> "behind left";
            case 6 -> "left";
            default -> "ahead left";
        };
    }

    /** Sidebar line (MiniMessage). */
    static String line(boolean atCheckout, double distance, String direction) {
        if (atCheckout) return "<green>At the checkout";
        return "<yellow>Checkout: <white>" + Math.round(distance) + "m <gray>" + direction.toLowerCase(Locale.ROOT);
    }
}
