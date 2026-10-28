package uk.co.playerready.pulsegames.core.player;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Map;

/**
 * Login and world-change clean-up for anyone not in a live instance. A crash or {@code kill -9} mid-round
 * leaves ghost flags, game attributes, effects and kit items in the player's saved data, because
 * {@link PlayerStateService} snapshots only live in memory.
 */
public final class PlayerSanitiser {
    private PlayerSanitiser() {}

    /** Plugin namespace used by every attribute modifier PulseGames adds. */
    private static final String NAMESPACE = "pulsegames";

    static Map<Attribute, Double> defaults() {
        return Map.of(Attribute.SCALE, 1.0, Attribute.GRAVITY, 0.08, Attribute.ATTACK_SPEED, 4.0,
                Attribute.MOVEMENT_SPEED, 0.1);
    }

    /**
     * @param clearInventory true on login only. On a world change the inventory may be the one a game has just
     *                       restored, which must not be wiped. Staff keep their items either way.
     */
    public static void sanitise(Player player, boolean clearInventory) {
        apply(player, clearInventory && !player.hasPermission("pulsegames.admin"), defaults());
    }

    static void apply(Player player, boolean clearInventory, Map<Attribute, Double> attributeDefaults) {
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setGlowing(false);
        player.setGravity(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setGameMode(GameMode.ADVENTURE);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        attributeDefaults.forEach((attribute, value) -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) return;
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (NAMESPACE.equals(modifier.getKey().getNamespace())) instance.removeModifier(modifier);
            }
            instance.setBaseValue(value);
        });
        if (clearInventory) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
            player.setItemOnCursor(null);
        }
    }
}
