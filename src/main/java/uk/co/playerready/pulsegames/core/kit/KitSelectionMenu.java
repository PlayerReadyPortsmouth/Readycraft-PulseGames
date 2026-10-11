package uk.co.playerready.pulsegames.core.kit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.economy.Cosmetic;
import uk.co.playerready.pulsegames.core.economy.CosmeticsService;
import uk.co.playerready.pulsegames.core.util.Text;
import java.util.*;

/** Selection only: never purchases a kit or replaces a fighting player's inventory. */
public final class KitSelectionMenu implements Listener {
    private final PulseGamesPlugin plugin;
    public KitSelectionMenu(PulseGamesPlugin plugin) { this.plugin = plugin; }
    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, String> ids = new HashMap<>();
        public Inventory getInventory() { return inventory; }
    }
    public boolean select(Player player, String cosmeticId) {
        if (cosmeticId != null) {
            var choice = CosmeticsService.byCategory(Cosmetic.Category.KIT).stream()
                    .filter(c -> c.id().equals(cosmeticId)).findFirst().orElse(null);
            if (choice == null || !plugin.economy().isUnlocked(player, cosmeticId)
                    || plugin.kits().get(cosmeticId.substring("kit-".length())) == null) return false;
        }
        plugin.economy().equip(player, Cosmetic.Category.KIT, cosmeticId);
        return true;
    }
    private ItemStack icon(Material material, String name, String... lore) {
        var item = new ItemStack(material);
        item.editMeta(meta -> { meta.displayName(Text.mm(name)); meta.lore(Arrays.stream(lore).map(Text::mm).toList()); });
        return item;
    }
    public void open(Player player) {
        var holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>KitPvP: choose a loadout"));
        String equipped = plugin.economy().equipped(player, Cosmetic.Category.KIT);
        holder.inventory.setItem(10, icon(Material.IRON_SWORD, "<yellow>Map default",
                equipped == null ? "<green>Selected" : "<gray>Click to select the arena's default kit",
                "<gray>Applies at your next KitPvP spawn", "<gray>One in the Chamber uses its own fixed kit"));
        int slot = 12;
        for (var choice : CosmeticsService.byCategory(Cosmetic.Category.KIT)) {
            if (slot > 16) break;
            boolean owned = plugin.economy().isUnlocked(player, choice.id());
            boolean available = plugin.kits().get(choice.id().substring(4)) != null;
            holder.ids.put(slot, choice.id());
            holder.inventory.setItem(slot++, icon(choice.icon(), "<yellow>" + choice.name(),
                    "<gray>" + choice.description(),
                    !available ? "<red>Loadout unavailable on this server" : choice.id().equals(equipped) && owned ? "<green>Selected"
                            : owned ? "<green>Click to select" : "<gold>Locked: " + choice.price() + " tokens · view in shop",
                    "<gray>Applies at your next KitPvP spawn"));
        }
        holder.inventory.setItem(22, icon(Material.SUNFLOWER, "<gold>Browse kit unlocks", "<gray>Open the token shop"));
        player.openInventory(holder.inventory);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) return;
        int slot = event.getSlot();
        if (slot == 22) { plugin.tokenShop().openCategory(player, Cosmetic.Category.KIT); return; }
        if (slot != 10 && !holder.ids.containsKey(slot)) return;
        String id = slot == 10 ? null : holder.ids.get(slot);
        if (!select(player, id)) {
            player.sendMessage(Text.msg("<yellow>This kit needs an unlock and an available loadout. View it in the token shop."));
            return;
        }
        player.sendMessage(Text.msg("<green>Kit selected for your next KitPvP spawn. <gray>One in the Chamber keeps its fixed loadout."));
        open(player);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
