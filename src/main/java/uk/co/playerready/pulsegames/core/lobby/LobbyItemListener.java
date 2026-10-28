package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/** Keeps the lobby controls in place without locking players' building materials. */
public final class LobbyItemListener implements Listener {
    private final LobbyService lobby;

    public LobbyItemListener(LobbyService lobby) { this.lobby = lobby; }

    private boolean control(ItemStack item) {
        return lobby.isMenuItem(item) || lobby.isShopItem(item) || lobby.isQuickPlayItem(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack swapped = null;
        if (event.getHotbarButton() >= 0) swapped = player.getInventory().getItem(event.getHotbarButton());
        else if (event.getClick() == ClickType.SWAP_OFFHAND) swapped = player.getInventory().getItemInOffHand();
        if (control(event.getCurrentItem()) || control(event.getCursor()) || control(swapped)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (control(event.getOldCursor()) || event.getRawSlots().stream()
                .anyMatch(slot -> control(event.getView().getItem(slot)))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (control(event.getMainHandItem()) || control(event.getOffHandItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (control(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }
}
