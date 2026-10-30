package uk.co.playerready.pulsenetwork;

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
import java.util.HashMap;
import java.util.Map;

/** A tiny chest menu: items in slots, a click runs that slot's action. Nothing can be taken out of it. */
final class ClickMenu implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Runnable> actions = new HashMap<>();

    ClickMenu(Component title, int size) { inventory = Bukkit.createInventory(this, size, title); }

    ClickMenu set(int slot, ItemStack item, Runnable action) { inventory.setItem(slot, item); if (action != null) actions.put(slot, action); return this; }
    void open(Player player) { player.openInventory(inventory); }
    @Override public Inventory getInventory() { return inventory; }

    static final class Guard implements Listener {
        @EventHandler public void click(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder() instanceof ClickMenu menu)) return;
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                Runnable run = menu.actions.get(e.getSlot());
                if (run != null) Bukkit.getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(ClickMenu.class), run);
            }
        }
        @EventHandler public void drag(InventoryDragEvent e) { if (e.getView().getTopInventory().getHolder() instanceof ClickMenu) e.setCancelled(true); }
    }
}
