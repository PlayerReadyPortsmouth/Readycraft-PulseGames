package uk.co.playerready.pulsegames.core.settings;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;

/** Reads and writes {@link Comfort} options with the same player-data (PDC) pattern as the party invite opt-out. */
public final class PlayerSettings {
    private PlayerSettings() { }

    private static NamespacedKey key(Comfort option) {
        return NamespacedKey.fromString("pulsegames:" + option.id());
    }

    public static boolean enabled(Player player, Comfort option) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null) return option.defaultValue();
        return pdc.has(key(option), PersistentDataType.BYTE) ? true : option.defaultValue();
    }

    public static void set(Player player, Comfort option, boolean on) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null) return;
        if (on) pdc.set(key(option), PersistentDataType.BYTE, (byte) 1); else pdc.remove(key(option));
    }

    /** True when any of these players has the option on (used to apply an option to a whole round). */
    public static boolean anyEnabled(Collection<? extends Player> players, Comfort option) {
        for (Player player : players) if (enabled(player, option)) return true;
        return false;
    }

    public static boolean anyInWorld(World world, Comfort option) {
        return world != null && anyEnabled(world.getPlayers(), option);
    }
}
