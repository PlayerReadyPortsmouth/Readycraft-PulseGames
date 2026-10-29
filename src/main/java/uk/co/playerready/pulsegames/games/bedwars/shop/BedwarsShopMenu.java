package uk.co.playerready.pulsegames.games.bedwars.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue.Category;
import static uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue.Item;
import static uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue.Upgrade;

/**
 * Categorised Bedwars item shop on a plain Bukkit chest. Same safety pattern as ArenaSetupMenu:
 * every click type and drag is cancelled, actions run next tick after owner and stale-view checks,
 * and clicks re-render the same inventory instead of reopening it.
 * Kept local to this package so it can move onto a shared UI kit later.
 * Author: AurorasChaos (on behalf of Player Ready).
 */
public final class BedwarsShopMenu {

    public static final class Holder implements InventoryHolder {
        final UUID owner;
        Category view = Category.BLOCKS;
        boolean team;
        Inventory inventory;
        final Map<Integer, Runnable> actions = new HashMap<>();
        Holder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final ShopBackend backend;

    public BedwarsShopMenu(ShopBackend backend) { this.backend = backend; }

    public static boolean isShop(Inventory top) { return top != null && top.getHolder() instanceof Holder; }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, ShopLayout.SIZE, Text.mm("<dark_gray>Pulse · Item Shop"));
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    public void click(Player player, InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); // every click type, including shift, number keys and double-click
        if (!holder.owner.equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != holder.inventory) return;
        Runnable action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        backend.runLater(() -> {
            if (player.isOnline() && backend.live(player)
                    && player.getOpenInventory().getTopInventory() == holder.inventory) action.run();
        });
    }

    public void drag(InventoryDragEvent event) {
        if (isShop(event.getView().getTopInventory())) event.setCancelled(true);
    }

    // ---- rendering -----------------------------------------------------------------

    private void render(Player player, Holder holder) {
        ShopCatalogue catalogue = backend.catalogue();
        ItemStack[] desired = new ItemStack[ShopLayout.SIZE];
        holder.actions.clear();
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < desired.length; i++) desired[i] = filler;

        Set<Material> held = held(player);
        for (Category category : Category.values()) {
            boolean selected = !holder.team && holder.view == category;
            int slot = ShopLayout.tabSlot(category.ordinal());
            desired[slot] = tab(category.icon, category.title, selected);
            holder.actions.put(slot, () -> { holder.view = category; holder.team = false; render(player, holder); });
        }
        if (backend.teamUpgradesOn() && !catalogue.upgrades().isEmpty()) {
            desired[ShopLayout.TEAM_TAB] = tab(Material.BEACON, "Team Upgrades", holder.team);
            holder.actions.put(ShopLayout.TEAM_TAB, () -> { holder.team = true; render(player, holder); });
        }
        List<Item> quick = catalogue.quickBuy();
        for (int i = 0; i < quick.size(); i++) putItem(player, holder, desired, ShopLayout.quickSlot(i), quick.get(i), held);

        if (holder.team) {
            List<Upgrade> upgrades = catalogue.upgrades();
            for (int i = 0; i < upgrades.size(); i++) putUpgrade(player, holder, desired, ShopLayout.upgradeSlot(i), upgrades.get(i));
        } else if (holder.view == Category.QUICK_BUY) {
            desired[ShopLayout.HINT] = named(Material.BOOK, "<yellow>Pick a shelf above", "<gray>Tap a shelf at the top to see more things to buy.");
        } else {
            List<Item> page = catalogue.inCategory(holder.view);
            for (int i = 0; i < page.size(); i++) putItem(player, holder, desired, ShopLayout.pageSlot(i), page.get(i), held);
        }

        ItemStack bag = named(Material.GOLD_INGOT, "<gold><b>Your resources",
                "<gray>Iron: <white>" + count(player, Material.IRON_INGOT),
                "<gray>Gold: <white>" + count(player, Material.GOLD_INGOT),
                "<gray>Diamonds: <white>" + count(player, Material.DIAMOND));
        desired[ShopLayout.BALANCE] = bag;
        desired[ShopLayout.CLOSE] = named(Material.BARRIER, "<red><b>Close");
        holder.actions.put(ShopLayout.CLOSE, player::closeInventory);

        Inventory inv = holder.inventory;
        for (int i = 0; i < desired.length; i++) if (!Objects.equals(inv.getItem(i), desired[i])) inv.setItem(i, desired[i]);
    }

    private void putItem(Player player, Holder holder, ItemStack[] desired, int slot, Item item, Set<Material> held) {
        ShopCatalogue catalogue = backend.catalogue();
        int have = count(player, item.currency());
        ShopRules.Verdict verdict = ShopRules.verdict(catalogue, item, have, held);
        desired[slot] = itemStack(player, item, verdict, have);
        holder.actions.put(slot, () -> {
            ShopBackend.Result result = backend.buy(player, item);
            feedback(player, result, item.currency(), ShopRules.afford(count(player, item.currency()) , item.cost()).shortfall(), item.cost(), item.name());
            render(player, holder);
        });
    }

    private void putUpgrade(Player player, Holder holder, ItemStack[] desired, int slot, Upgrade upgrade) {
        int level = backend.upgradeLevel(player, upgrade);
        int diamonds = count(player, Material.DIAMOND);
        ShopRules.Verdict verdict = ShopRules.upgradeVerdict(upgrade, level, diamonds);
        boolean maxed = verdict.status() == ShopRules.Status.OWNED_THIS;
        int cost = maxed ? 0 : upgrade.costs().get(level);
        ItemStack stack = new ItemStack(upgrade.icon());
        String name = upgrade.name() + (maxed ? " " + ShopRules.roman(level) + " - maxed" : " " + ShopRules.roman(level + 1)
                + (verdict.buyable() ? "" : " - " + ShopRules.needMore(verdict.shortfall(), Material.DIAMOND).toLowerCase()));
        List<String> lore = new ArrayList<>();
        lore.add(status(verdict, Material.DIAMOND, maxed ? "Your team has the best level" : "Click to buy for your team"));
        lore.add("<gray>" + upgrade.blurb());
        lore.add("<gray>Your team's level: <white>" + level + " of " + upgrade.maxLevel());
        if (!maxed) lore.add("<gray>Cost: <aqua>" + cost + " " + ShopRules.currencyName(Material.DIAMOND, cost));
        lore.add("<gray>You have: <white>" + diamonds + " " + ShopRules.currencyName(Material.DIAMOND, diamonds));
        dress(stack, colour(verdict) + "<b>" + name, lore, maxed);
        desired[slot] = stack;
        holder.actions.put(slot, () -> {
            ShopBackend.Result result = backend.buyUpgrade(player, upgrade);
            int left = ShopRules.upgradeVerdict(upgrade, level, count(player, Material.DIAMOND)).shortfall();
            feedback(player, result, Material.DIAMOND, left, cost, upgrade.name());
            render(player, holder);
        });
    }

    private ItemStack itemStack(Player player, Item item, ShopRules.Verdict verdict, int have) {
        Material material = item.teamColour() && backend.woolFor(player) != null ? backend.woolFor(player) : item.material();
        ItemStack stack = new ItemStack(material, Math.min(item.amount(), 64));
        if (item.potion() != null && stack.getItemMeta() instanceof PotionMeta meta) {
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(item.potion().effect()));
            if (type != null) meta.addCustomEffect(new PotionEffect(type, item.potion().seconds() * 20, item.potion().level() - 1), true);
            stack.setItemMeta(meta);
        }
        String title = (item.amount() > 1 ? item.amount() + "x " : "") + item.name();
        String suffix = switch (verdict.status()) {
            case CAN_BUY -> "";
            case NEED_MORE -> " - " + ShopRules.needMore(verdict.shortfall(), item.currency()).toLowerCase();
            case OWNED_THIS -> " - you have this";
            case OWNED_BETTER -> " - you have better";
        };
        List<String> lore = new ArrayList<>();
        lore.add(status(verdict, item.currency(), "Click to buy"));
        if (!item.blurb().isBlank()) lore.add("<gray>" + item.blurb());
        lore.add("<gray>Cost: <yellow>" + item.cost() + " " + ShopRules.currencyName(item.currency(), item.cost()));
        lore.add("<gray>You have: <white>" + have + " " + ShopRules.currencyName(item.currency(), have));
        dress(stack, colour(verdict) + "<b>" + title + suffix, lore, false);
        return stack;
    }

    /** Text first, colour second: the status is readable without telling green from red. */
    private static String status(ShopRules.Verdict verdict, Material currency, String buyText) {
        return switch (verdict.status()) {
            case CAN_BUY -> "<green>" + buyText;
            case NEED_MORE -> "<red>" + ShopRules.needMore(verdict.shortfall(), currency);
            case OWNED_THIS -> "<gray>You already have this";
            case OWNED_BETTER -> "<gray>You already have a better one";
        };
    }

    private static String colour(ShopRules.Verdict verdict) {
        return switch (verdict.status()) { case CAN_BUY -> "<green>"; case NEED_MORE -> "<red>"; default -> "<gray>"; };
    }

    private void feedback(Player player, ShopBackend.Result result, Material currency, int shortfall, int cost, String what) {
        switch (result) {
            case OK -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            case NEED_MORE -> {
                player.sendMessage(Text.msg("<red>" + ShopRules.needMore(Math.max(1, shortfall), currency) + " for " + what + "."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            case NO_ROOM -> {
                player.sendMessage(Text.msg("<red>Your bag is full. Make some space first."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            case ALREADY_OWNED -> player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    private ItemStack tab(Material icon, String title, boolean selected) {
        ItemStack stack = new ItemStack(icon);
        dress(stack, (selected ? "<gold><b>" : "<yellow>") + title,
                List.of(selected ? "<gold>Showing now" : "<green>Click to open"), selected);
        return stack;
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        dress(stack, name, List.of(lore), false);
        return stack;
    }

    /** Non-italic name and lore, no vanilla attribute lines, optional glint. */
    private static void dress(ItemStack stack, String name, List<String> lore, boolean glint) {
        stack.editMeta(meta -> {
            meta.displayName(Text.mm("<!i>" + name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(Text.mm("<!i>" + line));
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
            if (glint) meta.setEnchantmentGlintOverride(true);
        });
    }

    private static Set<Material> held(Player player) {
        Set<Material> held = new HashSet<>();
        var inv = player.getInventory();
        for (ItemStack item : inv.getContents()) if (item != null) held.add(item.getType());
        for (ItemStack item : inv.getArmorContents()) if (item != null) held.add(item.getType());
        return held;
    }

    private static int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) if (item != null && item.getType() == material) total += item.getAmount();
        return total;
    }
}
