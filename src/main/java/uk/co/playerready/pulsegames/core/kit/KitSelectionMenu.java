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
    static final class Holder implements InventoryHolder {
        final UUID owner;
        final int page;
        Holder(UUID owner, int page) { this.owner = owner; this.page = page; }
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
    /** Bounded, read-only preview; selecting a kit never applies these items immediately. */
    public static List<String> contents(Kit kit) {
        List<String> result = new ArrayList<>();
        List<ItemStack> armor = Arrays.asList(kit.helmet(), kit.chestplate(), kit.leggings(), kit.boots());
        String worn = armor.stream().filter(Objects::nonNull).map(item -> item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(java.util.stream.Collectors.joining(", "));
        result.add("<gray>Armour: " + (worn.isEmpty() ? "none" : worn));
        List<ItemStack> items = kit.items() == null ? List.of() : kit.items();
        for (ItemStack item : items.stream().filter(Objects::nonNull).limit(6).toList())
            result.add("<gray>" + item.getAmount() + " × " + item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (items.size() > 6) result.add("<gray>And " + (items.size() - 6) + " more item stacks");
        return List.copyOf(result);
    }
    public void open(Player player) { open(player, 0); }
    private void open(Player player, int requestedPage) {
        var choices = CosmeticsService.byCategory(Cosmetic.Category.KIT);
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (choices.size() - 1) / 5)));
        var holder = new Holder(player.getUniqueId(), page);
        holder.inventory = Bukkit.createInventory(holder, 27, Text.mm("<dark_gray>KitPvP: choose a loadout"));
        String equipped = plugin.economy().equipped(player, Cosmetic.Category.KIT);
        holder.inventory.setItem(10, icon(Material.IRON_SWORD, "<yellow>Map default",
                equipped == null ? "<green>Selected" : "<gray>Click to select the arena's default kit",
                "<gray>Applies at your next KitPvP spawn", "<gray>One in the Chamber uses its own fixed kit"));
        int slot = 12;
        for (var choice : choices.stream().skip(page * 5L).limit(5).toList()) {
            boolean owned = plugin.economy().isUnlocked(player, choice.id());
            boolean available = plugin.kits().get(choice.id().substring(4)) != null;
            holder.ids.put(slot, choice.id());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + choice.description());
            lore.add(!available ? "<red>Loadout unavailable on this server" : choice.id().equals(equipped) && owned ? "<green>Selected"
                    : owned ? "<green>Click to select" : "<gold>Locked: " + choice.price() + " tokens · view in shop");
            if (available) lore.addAll(contents(plugin.kits().get(choice.id().substring(4))));
            lore.add("<gray>Applies at your next KitPvP spawn");
            holder.inventory.setItem(slot++, icon(choice.icon(), "<yellow>" + choice.name(), lore.toArray(String[]::new)));
        }
        if (page > 0) holder.inventory.setItem(18, icon(Material.ARROW, "<gray>Previous kits"));
        if ((page + 1) * 5 < choices.size()) holder.inventory.setItem(26, icon(Material.ARROW, "<gray>Next kits"));
        holder.inventory.setItem(22, icon(Material.SUNFLOWER, "<gold>Browse kit unlocks", "<gray>Open the token shop"));
        player.openInventory(holder.inventory);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) return;
        int slot = event.getSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !holder.owner.equals(player.getUniqueId())
                    || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            if (slot == 18 && holder.page > 0) { open(player, holder.page - 1); return; }
            if (slot == 26 && (holder.page + 1) * 5 < CosmeticsService.byCategory(Cosmetic.Category.KIT).size()) {
                open(player, holder.page + 1); return;
            }
            if (slot == 22) { plugin.tokenShop().openCategory(player, Cosmetic.Category.KIT); return; }
            if (slot != 10 && !holder.ids.containsKey(slot)) return;
            String id = slot == 10 ? null : holder.ids.get(slot);
            if (!select(player, id)) {
                player.sendMessage(Text.msg("<yellow>This kit needs an unlock and an available loadout. View it in the token shop."));
                return;
            }
            player.sendMessage(Text.msg("<green>Kit selected for your next KitPvP spawn. <gray>One in the Chamber keeps its fixed loadout."));
            open(player, holder.page);
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
