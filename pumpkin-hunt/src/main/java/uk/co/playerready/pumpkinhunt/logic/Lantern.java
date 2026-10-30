package uk.co.playerready.pumpkinhunt.logic;

/** One hidden jack-o'-lantern: a stable id, where it is, and the hint players are shown (never coordinates). */
public record Lantern(String id, String world, int x, int y, int z, String hint) {
    public String key() { return key(world, x, y, z); }

    public static String key(String world, int x, int y, int z) { return world + ":" + x + ":" + y + ":" + z; }
}
