package uk.co.playerready.pulsegames.core.player;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Snapshots inventory + vitals when entering a game and restores on exit. */
public final class PlayerStateService {

    private record Snapshot(ItemStack[] contents, GameMode gameMode, double health, int food,
                            float exp, int level, boolean allowFlight) {}

    private final Map<UUID, Snapshot> saved = new HashMap<>();

    public void save(Player player) {
        saved.putIfAbsent(player.getUniqueId(), new Snapshot(
                player.getInventory().getContents().clone(),
                player.getGameMode(), player.getHealth(), player.getFoodLevel(),
                player.getExp(), player.getLevel(), player.getAllowFlight()));
        reset(player);
    }

    public void restore(Player player) {
        Snapshot snap = saved.remove(player.getUniqueId());
        reset(player);
        if (snap == null) return;
        player.getInventory().setContents(snap.contents());
        player.setGameMode(snap.gameMode());
        player.setHealth(Math.min(snap.health(), maxHealth(player)));
        player.setFoodLevel(snap.food());
        player.setExp(snap.exp());
        player.setLevel(snap.level());
        player.setAllowFlight(snap.allowFlight());
    }

    /** Full clean slate: empty inventory, full health/food, no effects. */
    public void reset(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setHealth(maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(10f);
        player.setExp(0f);
        player.setLevel(0);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.closeInventory();
    }

    private static double maxHealth(Player player) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}
