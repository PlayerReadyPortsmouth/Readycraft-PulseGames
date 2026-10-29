package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.shop.ShopFeedback;
import uk.co.playerready.pulsegames.core.ui.shop.ShopFeedback.Outcome;
import uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Status;
import uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Verdict;
import uk.co.playerready.pulsegames.core.ui.shop.ShopTiles;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop.Entry;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop.Tab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue.Category;
import static uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue.Item;
import static uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue.Upgrade;

/**
 * Categorised Bedwars item shop. Layout, safety (cancelled clicks and drags, next-tick actions, in-place
 * re-render) and tile styling come from the shared {@code core/ui/shop} kit; this class only supplies the
 * Bedwars catalogue and purchase rules.
 * Author: AurorasChaos (on behalf of Player Ready).
 */
public final class BedwarsShopMenu implements TabbedShop.Source {
    static final String TEAM = "team";

    private final Plugin plugin;
    private final ShopBackend backend;
    private final TabbedShop shop = new TabbedShop(this, "Item Shop");

    public BedwarsShopMenu(Plugin plugin, ShopBackend backend) {
        this.plugin = plugin;
        this.backend = backend;
    }

    public void open(Player player) { shop.open(player, Category.BLOCKS.name()); }

    @Override public Plugin plugin() { return plugin; }
    @Override public boolean live(Player player) { return backend.live(player); }

    @Override public List<Tab> tabs(Player player) {
        List<Tab> tabs = new ArrayList<>();
        for (Category c : Category.values()) {
            tabs.add(c == Category.QUICK_BUY
                    ? new Tab(c.name(), c.icon, c.title, false, "Pick a shelf above", "Tap a shelf at the top to see more things to buy.")
                    : Tab.of(c.name(), c.icon, c.title));
        }
        if (backend.teamUpgradesOn() && !backend.catalogue().upgrades().isEmpty())
            tabs.add(new Tab(TEAM, Material.BEACON, "Team Upgrades", true, null, null));
        return tabs;
    }

    @Override public List<Entry> quick(Player player) {
        Set<Material> held = held(player);
        return backend.catalogue().quickBuy().stream().map(i -> itemEntry(player, i, held)).toList();
    }

    @Override public List<Entry> entries(Player player, Tab tab) {
        ShopCatalogue catalogue = backend.catalogue();
        if (TEAM.equals(tab.id())) return catalogue.upgrades().stream().map(u -> upgradeEntry(player, u)).toList();
        if (tab.id().equals(Category.QUICK_BUY.name())) return List.of();
        Set<Material> held = held(player);
        return catalogue.inCategory(Category.valueOf(tab.id())).stream().map(i -> itemEntry(player, i, held)).toList();
    }

    @Override public Entry footer(Player player) {
        return Entry.info(ShopTiles.info(Material.GOLD_INGOT, "<gold><b>Your resources", Lore.of()
                .kv("Iron", String.valueOf(count(player, Material.IRON_INGOT)))
                .kv("Gold", String.valueOf(count(player, Material.GOLD_INGOT)))
                .kv("Diamonds", String.valueOf(count(player, Material.DIAMOND)))));
    }

    // ---- tiles ---------------------------------------------------------------------

    private Entry itemEntry(Player player, Item item, Set<Material> held) {
        int have = count(player, item.currency());
        Verdict verdict = ShopRules.verdict(backend.catalogue(), item, have, held);
        String title = (item.amount() > 1 ? item.amount() + "x " : "") + item.name();
        ItemStack stack = ShopTiles.offer(base(player, item), new ShopTiles.Offer(title, item.blurb(), item.cost(),
                n -> ShopRules.currencyName(item.currency(), n), have, verdict));
        return new Entry(stack, p -> {
            Outcome outcome = switch (backend.buy(p, item)) {
                case OK -> Outcome.OK;
                case NEED_MORE -> Outcome.NEED_MORE;
                case NO_ROOM -> Outcome.NO_ROOM;
                case ALREADY_OWNED -> Outcome.OWNED;
            };
            int shortfall = ShopRules.afford(count(p, item.currency()), item.cost()).shortfall();
            ShopFeedback.play(p, outcome, shortfall, ShopRules.currencyName(item.currency(), shortfall), item.name());
        });
    }

    private Entry upgradeEntry(Player player, Upgrade upgrade) {
        int level = backend.upgradeLevel(player, upgrade);
        int diamonds = count(player, Material.DIAMOND);
        Verdict verdict = ShopRules.upgradeVerdict(upgrade, level, diamonds);
        boolean maxed = verdict.status() == Status.OWNED_THIS;
        int cost = maxed ? 0 : upgrade.costs().get(level);
        String name = upgrade.name() + (maxed ? " " + ShopRules.roman(level) + " - maxed" : " " + ShopRules.roman(level + 1)
                + (verdict.buyable() ? "" : " - " + ShopRules.needMore(verdict.shortfall(), Material.DIAMOND).toLowerCase()));
        Lore lore = Lore.of().line(ShopTiles.status(verdict, n -> ShopRules.currencyName(Material.DIAMOND, n),
                "Click to buy for your team", "Your team has the best level", "Your team has the best level"))
                .desc(upgrade.blurb())
                .kv("Your team's level", level + " of " + upgrade.maxLevel());
        if (!maxed) lore.kv("Cost", cost + " " + ShopRules.currencyName(Material.DIAMOND, cost));
        lore.kv("You have", diamonds + " " + ShopRules.currencyName(Material.DIAMOND, diamonds));
        ItemStack stack = ShopTiles.dress(new ItemStack(upgrade.icon()), ShopTiles.colour(verdict) + "<b>" + name, lore.build(), maxed);
        return new Entry(stack, p -> {
            Outcome outcome = switch (backend.buyUpgrade(p, upgrade)) {
                case OK -> Outcome.OK;
                case NEED_MORE -> Outcome.NEED_MORE;
                case NO_ROOM -> Outcome.NO_ROOM;
                case ALREADY_OWNED -> Outcome.OWNED;
            };
            int left = ShopRules.upgradeVerdict(upgrade, level, count(p, Material.DIAMOND)).shortfall();
            ShopFeedback.play(p, outcome, left, ShopRules.currencyName(Material.DIAMOND, left), upgrade.name());
        });
    }

    private ItemStack base(Player player, Item item) {
        Material material = item.teamColour() && backend.woolFor(player) != null ? backend.woolFor(player) : item.material();
        ItemStack stack = new ItemStack(material, Math.min(item.amount(), 64));
        if (item.potion() != null && stack.getItemMeta() instanceof PotionMeta meta) {
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(item.potion().effect()));
            if (type != null) meta.addCustomEffect(new PotionEffect(type, item.potion().seconds() * 20, item.potion().level() - 1), true);
            stack.setItemMeta(meta);
        }
        return stack;
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
