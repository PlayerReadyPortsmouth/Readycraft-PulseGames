package uk.co.playerready.pulsegames.core.lobby;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Read-only chest GUI (challenges, leaderboard). Chest inventories work on Java and Bedrock alike. */
public final class InfoMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public static void open(Player player, Component title, int rows, Map<Integer, ItemStack> items) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory = inv;
        items.forEach((slot, item) -> { if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item); });
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
