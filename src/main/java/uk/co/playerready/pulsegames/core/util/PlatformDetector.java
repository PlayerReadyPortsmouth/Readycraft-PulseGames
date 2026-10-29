package uk.co.playerready.pulsegames.core.util;

import org.bukkit.entity.Player;

/**
 * Java or Bedrock (via Geyser)? Floodgate is a soft dependency: its API is only touched inside
 * {@link Floodgate}, so the plugin loads and treats everyone as Java when it is not installed.
 */
public final class PlatformDetector {
    private PlatformDetector() { }

    private static final class Floodgate {
        static boolean is(Player player) {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        }
    }

    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        try {
            return org.bukkit.Bukkit.getPluginManager().getPlugin("floodgate") != null && Floodgate.is(player);
        } catch (Throwable absentOrBroken) {
            return false;
        }
    }

    /** Lower-case verb for "use this item": "tap" on Bedrock, "right-click" on Java. */
    public static String useVerb(Player player) { return useVerb(isBedrock(player)); }

    public static String useVerb(boolean bedrock) { return bedrock ? "tap" : "right-click"; }

    /** "Tap" / "Right-click" for the start of a sentence. */
    public static String useVerbCapital(Player player) {
        String verb = useVerb(player);
        return Character.toUpperCase(verb.charAt(0)) + verb.substring(1);
    }
}
