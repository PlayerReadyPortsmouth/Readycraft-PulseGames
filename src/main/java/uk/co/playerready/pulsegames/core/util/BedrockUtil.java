package uk.co.playerready.pulsegames.core.util;

import org.bukkit.entity.Player;

/** Bedrock (Geyser/Floodgate) client detection without a Floodgate dependency. */
public final class BedrockUtil {

    private BedrockUtil() {
    }

    /**
     * Floodgate gives Bedrock players version-0 UUIDs (xuid-based), unlike the
     * version-4 UUIDs of Java accounts. Works without depending on the Floodgate API.
     */
    public static boolean isBedrock(Player player) {
        return player.getUniqueId().version() == 0;
    }
}
