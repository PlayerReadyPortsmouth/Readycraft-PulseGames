package uk.co.playerready.pulsegames.core.economy;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.util.MenuFx;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The token shop GUI: cosmetics, kit unlocks and boosters. */
public final class TokenShopMenu implements Listener {

    private static final String TITLE_TAG = "<gradient:#ffc371:#ffd700><b>";

    private static final int BOOSTER_PRICE = 500;
    private static final int BOOSTER_MINUTES = 60;

    private static final int BALANCE_SLOT = 13;
    private static final int[] CATEGORY_SLOTS = {19, 21, 23, 25};
    private static final int BOOSTER_SLOT = 31;
    private static final int BACK_SLOT = 31;

    private final PulseGamesPlugin plugin;

    public TokenShopMenu(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class ShopHolder implements InventoryHolder {
        Cosmetic.Category category; // null = main menu
        final Map<Integer, Cosmetic> bySlot = new HashMap<>();
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public void openMain(Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 45, Text.mm(TITLE_TAG + "Token Shop"));
        holder.inventory = inv;

        ItemStack balance = new ItemStack(Material.SUNFLOWER);
        balance.editMeta(meta -> {
            meta.displayName(Text.mm("<gold><b>⛀ " + plugin.economy().tokens(player) + " Tokens"));
            meta.lore(List.of(
                    Text.mm("<gray>Earn tokens by playing games,"),
                    Text.mm("<gray>winning, kills, playtime and"),
                    Text.mm("<gray>daily logins!")));
        });
        inv.setItem(BALANCE_SLOT, balance);

        Cosmetic.Category[] categories = Cosmetic.Category.values();
        for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
            Cosmetic.Category category = categories[i];
            ItemStack item = new ItemStack(category.icon);
            long owned = CosmeticsService.byCategory(category).stream()
                    .filter(c -> plugin.economy().isUnlocked(player, c.id())).count();
            item.editMeta(meta -> {
                meta.displayName(Text.mm("<yellow><b>" + category.displayName));
                meta.lore(List.of(
                        Text.mm("<gray>Unlocked: <white>" + owned + "/" + CosmeticsService.byCategory(category).size()),
                        Text.mm("<green>Click to browse!")));
            });
            inv.setItem(CATEGORY_SLOTS[i], item);
        }

        ItemStack booster = new ItemStack(Material.EXPERIENCE_BOTTLE);
        boolean active = plugin.economy().boosterActive(player);
        booster.editMeta(meta -> {
            meta.displayName(Text.mm("<light_purple><b>2x Token Booster <gray>(1 hour)"));
            meta.lore(List.of(
                    Text.mm(active ? "<green>Currently active!" : "<gray>Doubles all token earnings"),
                    Text.mm("<gold>⛀ " + BOOSTER_PRICE + " <gray>- click to buy" + (active ? " (extends)" : ""))));
        });
        inv.setItem(BOOSTER_SLOT, booster);
        plugin.menuFx().open(player, inv);
    }

    public void openCategory(Player player, Cosmetic.Category category) {
        ShopHolder holder = new ShopHolder();
        holder.category = category;
        Inventory inv = Bukkit.createInventory(holder, 45, Text.mm(TITLE_TAG + category.displayName));
        holder.inventory = inv;
        List<Cosmetic> items = CosmeticsService.byCategory(category);
        String equipped = plugin.economy().equipped(player, category);
        // Interior slots minus the bottom-centre one reserved for the back button.
        List<Integer> slots = new ArrayList<>(MenuFx.interiorSlots(45));
        slots.remove(Integer.valueOf(BACK_SLOT));
        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            Cosmetic cosmetic = items.get(i);
            boolean owned = plugin.economy().isUnlocked(player, cosmetic.id());
            boolean isEquipped = cosmetic.id().equals(equipped);
            ItemStack item = new ItemStack(cosmetic.icon());
            item.editMeta(meta -> {
                meta.displayName(Text.mm((isEquipped ? "<green><b>" : owned ? "<yellow>" : "<gray>") + cosmetic.name()));
                List<Component> lore = new ArrayList<>();
                lore.add(Text.mm("<gray>" + cosmetic.description()));
                lore.add(Text.mm(""));
                if (isEquipped) {
                    lore.add(Text.mm("<green>EQUIPPED <gray>- click to unequip"));
                } else if (owned) {
                    lore.add(Text.mm("<yellow>Owned <gray>- click to equip"));
                } else {
                    lore.add(Text.mm("<gold>⛀ " + cosmetic.price() + " <gray>- click to buy"));
                }
                meta.lore(lore);
            });
            int slot = slots.get(i);
            inv.setItem(slot, item);
            holder.bySlot.put(slot, cosmetic);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(meta -> meta.displayName(Text.mm("<gray>« Back")));
        inv.setItem(BACK_SLOT, back);
        plugin.menuFx().open(player, inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getSlot();

        if (holder.category == null) {
            Cosmetic.Category[] categories = Cosmetic.Category.values();
            for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
                if (slot == CATEGORY_SLOTS[i]) {
                    openCategory(player, categories[i]);
                    return;
                }
            }
            if (slot == BOOSTER_SLOT) buyBooster(player);
            return;
        }
        if (slot == BACK_SLOT) {
            openMain(player);
            return;
        }
        Cosmetic cosmetic = holder.bySlot.get(slot);
        if (cosmetic == null) return;
        if (plugin.economy().isUnlocked(player, cosmetic.id())) {
            boolean isEquipped = cosmetic.id().equals(plugin.economy().equipped(player, holder.category));
            plugin.economy().equip(player, holder.category, isEquipped ? null : cosmetic.id());
            player.sendMessage(Text.msg(isEquipped ? "Unequipped <yellow>" + cosmetic.name()
                    : "Equipped <yellow>" + cosmetic.name() + "</yellow>!"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        } else if (plugin.economy().spend(player, cosmetic.price())) {
            plugin.economy().unlock(player, cosmetic.id());
            plugin.economy().equip(player, holder.category, cosmetic.id());
            player.sendMessage(Text.msg("<green><b>Unlocked " + cosmetic.name() + "!</b> <gray>(equipped)"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        } else {
            player.sendMessage(Text.msg("<red>You need <gold>⛀ " + cosmetic.price() + "</gold> for that <gray>(you have "
                    + plugin.economy().tokens(player) + ")"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
        openCategory(player, holder.category);
    }

    private void buyBooster(Player player) {
        if (plugin.economy().spend(player, BOOSTER_PRICE)) {
            plugin.economy().activateBooster(player, BOOSTER_MINUTES);
            player.sendMessage(Text.msg("<light_purple><b>2x Token Booster activated</b> <gray>for "
                    + BOOSTER_MINUTES + " minutes!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            openMain(player);
        } else {
            player.sendMessage(Text.msg("<red>You need <gold>⛀ " + BOOSTER_PRICE + "</gold> for a booster."));
        }
    }
}
