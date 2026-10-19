package uk.co.playerready.pulsegames.games.readyplayerz;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Crash safety for ReadyPlayerZ legacy combat. The 1024 ATTACK_SPEED base is saved with the
 * player's data, so a crash or kill mid-game would leave it applied in the lobby and on other
 * servers. A PDC marker holding the original value is written while legacy combat is applied
 * and cleared on restore; {@link #heal} undoes a leftover marker, and only our own value.
 */
public final class LegacyAttackSpeed {
    static final NamespacedKey KEY = NamespacedKey.fromString("pulsegames:rpz-legacy-attack-speed");
    static final double LEGACY = 1024;
    static final double VANILLA_DEFAULT = 4.0;

    private LegacyAttackSpeed() {}

    static void mark(Player player, double original) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc != null) pdc.set(KEY, PersistentDataType.DOUBLE, original);
    }

    static void clear(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc != null) pdc.remove(KEY);
    }

    /** Restores a leftover legacy value. Call only when the player is not in a game. */
    public static boolean heal(Player player) {
        return heal(player, p -> p.getAttribute(Attribute.ATTACK_SPEED));
    }

    static boolean heal(Player player, java.util.function.Function<Player, org.bukkit.attribute.AttributeInstance> attackSpeed) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null || !pdc.has(KEY, PersistentDataType.DOUBLE)) return false;
        Double original = pdc.get(KEY, PersistentDataType.DOUBLE);
        pdc.remove(KEY);
        var attribute = attackSpeed.apply(player);
        if (attribute == null || attribute.getBaseValue() != LEGACY) return false; // someone else changed it since
        double restored = original == null || !Double.isFinite(original) || original == LEGACY ? VANILLA_DEFAULT : original;
        attribute.setBaseValue(restored);
        return true;
    }
}
