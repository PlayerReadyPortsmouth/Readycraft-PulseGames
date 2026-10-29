package uk.co.playerready.pulsegames.core.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * The single listener behind every {@link Menu}. Every click and drag while a Menu is the top inventory is
 * cancelled outright, which covers shift-click, number keys, offhand swap, double-click and collect-to-cursor
 * from either inventory. Actions only run for the owner, for a click in the menu itself, next tick, and only
 * if that same menu is still the open window.
 */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != top) return;
        Consumer<Player> action = menu.action(event.getRawSlot());
        if (action == null) return;
        // Never open or close windows inside the click event: the client keeps a ghost window.
        menu.plugin.getServer().getScheduler().runTask(menu.plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            Theme.sound(player, Theme.Cue.CLICK);
            action.accept(player);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }
}
