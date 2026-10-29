package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Per-bot use of the kit it was actually given: golden apples, the bow and ender pearls. Every item is a real
 * inventory item that is spent, and each action fires the ordinary Bukkit event so protection plugins can veto it.
 */
final class BotKitUse {
    private long nextEat, nextShot, nextPearl;
    private boolean drawing;
    private final java.util.function.Consumer<Player> eatEffects;

    BotKitUse(java.util.function.Consumer<Player> eatEffects) { this.eatEffects = eatEffects; }

    /** Vanilla golden apple: regeneration II for 5 s and absorption I for 2 minutes. */
    static void goldenAppleEffects(Player bot) {
        bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
    }

    static int hotbar(PlayerInventory inventory, Material type) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == type) return i;
        }
        return -1;
    }

    static boolean has(Player bot, Material type) { return bot.getInventory().first(type) >= 0; }

    boolean canHeal(Player bot) { return has(bot, Material.GOLDEN_APPLE); }
    boolean canShoot(Player bot) { return has(bot, Material.BOW) && has(bot, Material.ARROW); }
    boolean canPearl(Player bot) { return has(bot, Material.ENDER_PEARL); }
    boolean drawing() { return drawing; }
    void stopDrawing() { drawing = false; }

    /** Eat a golden apple (regeneration II and absorption, as vanilla) after the usual consume event. */
    boolean eat(Player bot, long now) {
        if (now < nextEat) return false;
        var inventory = bot.getInventory();
        int slot = inventory.first(Material.GOLDEN_APPLE);
        if (slot < 0) return false;
        ItemStack apple = inventory.getItem(slot);
        if (apple == null) return false;
        var event = new PlayerItemConsumeEvent(bot, apple, EquipmentSlot.HAND);
        bot.getServer().getPluginManager().callEvent(event);
        nextEat = now + 40;
        if (event.isCancelled()) return false;
        if (apple.getAmount() <= 1) inventory.setItem(slot, null); else apple.setAmount(apple.getAmount() - 1);
        eatEffects.accept(bot);
        bot.swingMainHand();
        return true;
    }

    /** Draw for a second, then loose a real arrow at {@code aimPoint} with ordinary ballistic lift. */
    boolean shoot(Player bot, org.bukkit.Location aimPoint, double distance, long now, int delayTicks) {
        var inventory = bot.getInventory();
        int bow = hotbar(inventory, Material.BOW), arrowSlot = inventory.first(Material.ARROW);
        if (bow < 0 || arrowSlot < 0) { drawing = false; return false; }
        inventory.setHeldItemSlot(bow);
        if (!drawing) { drawing = true; nextShot = Math.max(nextShot, now + 20); }
        if (now < nextShot) return true;
        nextShot = now + Math.max(20, delayTicks);
        Vector delta = aimPoint.toVector().subtract(bot.getEyeLocation().toVector());
        delta.setY(delta.getY() + distance * 0.08);
        Arrow arrow = bot.launchProjectile(Arrow.class, delta.normalize().multiply(3));
        if (!arrow.isValid()) return true; // a protection plugin may cancel ProjectileLaunchEvent
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        var event = new EntityShootBowEvent(bot, inventory.getItem(bow), inventory.getItem(arrowSlot),
                arrow, EquipmentSlot.HAND, 1.0f, true);
        bot.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) { arrow.remove(); return true; }
        if (event.getProjectile() != arrow) arrow.remove();
        if (event.shouldConsumeItem()) {
            ItemStack ammo = inventory.getItem(arrowSlot);
            if (ammo != null && ammo.getType() == Material.ARROW) {
                if (ammo.getAmount() <= 1) inventory.setItem(arrowSlot, null); else ammo.setAmount(ammo.getAmount() - 1);
            }
        }
        bot.swingMainHand();
        return true;
    }

    /** Throw one pearl at the target (to close a gap or escape a corner). One every ten seconds at most. */
    boolean pearl(Player bot, org.bukkit.Location aimPoint, double distance, long now) {
        if (now < nextPearl) return false;
        var inventory = bot.getInventory();
        int slot = inventory.first(Material.ENDER_PEARL);
        if (slot < 0) return false;
        nextPearl = now + 200;
        Vector delta = aimPoint.toVector().subtract(bot.getEyeLocation().toVector());
        delta.setY(delta.getY() + distance * 0.06);
        EnderPearl pearl = bot.launchProjectile(EnderPearl.class, delta.normalize().multiply(1.5));
        if (!pearl.isValid()) return false;
        ItemStack item = inventory.getItem(slot);
        if (item != null) { if (item.getAmount() <= 1) inventory.setItem(slot, null); else item.setAmount(item.getAmount() - 1); }
        bot.swingMainHand();
        return true;
    }
}
