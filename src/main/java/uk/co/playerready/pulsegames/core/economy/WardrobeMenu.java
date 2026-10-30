package uk.co.playerready.pulsegames.core.economy;

import uk.co.playerready.pulsegames.core.command.Routes;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.progression.XpRules;
import uk.co.playerready.pulsegames.core.ui.Confirm;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.ui.shop.ShopTiles;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /wardrobe}: the whole cosmetics catalogue on the shared {@link TabbedShop}. Tabs are
 * Owned / Unlockable (free at a level) / Buy (tokens); the quick row filters by slot so each list fits one page.
 */
public final class WardrobeMenu implements TabbedShop.Source {
    static final String OWNED = "owned", UNLOCKABLE = "unlockable", BUY = "buy";
    private static final String COIN = "⛀";

    private final PulseGamesPlugin plugin;
    private final TabbedShop shop;
    private final Map<UUID, Cosmetic.Category> filter = new HashMap<>();

    public WardrobeMenu(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.shop = new TabbedShop(this, "Wardrobe");
    }

    public void open(Player player) { open(player, OWNED); }

    private void open(Player player, String tab) { shop.open(player, tab); }

    /** The tab an item belongs on for a player. Owned wins, then how it is earned. */
    static String tabFor(boolean owned, Cosmetic cosmetic) {
        return owned ? OWNED : cosmetic.levelUnlock() ? UNLOCKABLE : BUY;
    }

    private Cosmetic.Category category(Player player) { return filter.getOrDefault(player.getUniqueId(), Cosmetic.Category.values()[0]); }

    @Override public Plugin plugin() { return plugin; }

    @Override public List<TabbedShop.Tab> tabs(Player player) {
        return List.of(TabbedShop.Tab.of(OWNED, Material.CHEST, "Owned"),
                TabbedShop.Tab.of(UNLOCKABLE, Material.EXPERIENCE_BOTTLE, "Unlockable"),
                TabbedShop.Tab.of(BUY, Material.SUNFLOWER, "Buy"));
    }

    @Override public List<TabbedShop.Entry> quick(Player player) {
        List<TabbedShop.Entry> out = new ArrayList<>();
        for (Cosmetic.Category c : Cosmetic.Category.values()) {
            boolean chosen = c == category(player);
            out.add(new TabbedShop.Entry(ShopTiles.choice(c.icon, c.displayName, null, chosen, "Show these"),
                    p -> filter.put(p.getUniqueId(), c)));
        }
        return out;
    }

    @Override public List<TabbedShop.Entry> entries(Player player, TabbedShop.Tab tab) {
        Cosmetic.Category slot = category(player);
        String equipped = plugin.economy().equipped(player, slot);
        int level = plugin.levels().level(player);
        List<TabbedShop.Entry> out = new ArrayList<>();
        for (Cosmetic c : CosmeticsService.byCategory(slot)) {
            boolean owned = plugin.economy().isUnlocked(player, c.id());
            if (!tab.id().equals(tabFor(owned, c))) continue;
            out.add(new TabbedShop.Entry(icon(player, c, owned, c.id().equals(equipped), level), p -> click(p, c)));
        }
        return out;
    }

    private org.bukkit.inventory.ItemStack icon(Player player, Cosmetic c, boolean owned, boolean equipped, int level) {
        Lore lore = Lore.of();
        lore.line(c.levelUnlock() ? Theme.ACCENT + "Unlocked by level" : Theme.MONEY + "Bought with tokens");
        lore.desc(c.description()).blank();
        if (c.nameStyleId() != null && !owned) lore.line(Theme.ACCENT + "Set it with /namestyle");
        if (equipped) lore.status(Theme.GOOD + "Equipped").hint("Click to take it off");
        else if (owned && c.nameStyleId() != null) lore.status("<yellow>Yours").line(Theme.ACCENT + "Set it with /namestyle").hint("Click to use this style");
        else if (owned) lore.status("<yellow>Yours").hint("Click to equip");
        else if (c.levelUnlock()) lore.status(level >= c.level() ? Theme.GOOD + "Ready to unlock" : Theme.BAD + "Reach level " + c.level() + " (you are " + level + ")");
        else {
            int have = plugin.economy().tokens(player);
            lore.status(Theme.MONEY + COIN + " " + c.price() + " tokens");
            if (have >= c.price()) lore.hint("Click to buy"); else lore.status(Theme.BAD + "Need " + (c.price() - have) + " more tokens");
        }
        var stack = ShopTiles.info(c.icon(), (equipped ? Theme.GOOD + "<b>" : owned ? "<yellow>" : Theme.TEXT) + c.name(), lore);
        return equipped ? uk.co.playerready.pulsegames.core.ui.Buttons.glow(stack) : stack;
    }

    private void click(Player player, Cosmetic c) {
        var economy = plugin.economy();
        if (!economy.isUnlocked(player, c.id()) && c.levelUnlock()) {
            // Level rewards are granted when the level is reached; this covers one that was missed.
            if (plugin.levels().level(player) >= c.level()) economy.unlock(player, c.id());
            else {
                player.sendMessage(Text.msg("<red>Reach level " + c.level() + " to unlock <yellow>" + c.name() + "<red>."));
                Theme.sound(player, Theme.Cue.ERROR);
                return;
            }
        }
        if (economy.isUnlocked(player, c.id()) && c.nameStyleId() != null) {
            // The network picks and shows the style; run the same command the player would type.
            player.performCommand(Routes.namestyleSet(c.nameStyleId()));
            return;
        }
        if (economy.isUnlocked(player, c.id())) {
            boolean on = c.id().equals(economy.equipped(player, c.category()));
            economy.equip(player, c.category(), on ? null : c.id());
            player.sendMessage(Text.msg(on ? "Unequipped <yellow>" + c.name() : "Equipped <yellow>" + c.name() + "</yellow>!"));
            Theme.sound(player, Theme.Cue.SUCCESS);
            return;
        }
        int have = economy.tokens(player);
        if (have < c.price()) {
            player.sendMessage(Text.msg("<red>You need <gold>" + COIN + " " + c.price() + " tokens</gold> for that <gray>(you have " + have + ")"));
            Theme.sound(player, Theme.Cue.ERROR);
            return;
        }
        if (!ShopRules.needsConfirm(c.price(), plugin.getConfig().getInt("shop.confirm-at", 500))) { buy(player, c); return; }
        Confirm.ask(plugin, player, "Buy " + c.name() + "?", List.of(Theme.NAME + c.name(),
                Theme.TEXT + "Price: " + Theme.MONEY + COIN + " " + c.price() + " tokens",
                Theme.TEXT + "After: <white>" + ShopRules.balanceAfter(have, c.price())),
                true, p -> { buy(p, c); open(p, BUY); }, p -> open(p, BUY));
    }

    /** Safe to call twice: an item already owned is never charged for again. */
    private void buy(Player player, Cosmetic c) {
        if (plugin.economy().isUnlocked(player, c.id())) return;
        if (c.levelUnlock() || !plugin.economy().spend(player, c.price())) return;
        plugin.economy().unlock(player, c.id());
        plugin.economy().equip(player, c.category(), c.id());
        player.sendMessage(Text.msg("<green><b>Unlocked " + c.name() + "!</b> <gray>(equipped)"));
        Theme.sound(player, Theme.Cue.SUCCESS);
    }

    @Override public TabbedShop.Entry footer(Player player) {
        var p = plugin.levels().progress(player);
        return TabbedShop.Entry.info(ShopTiles.info(Material.SUNFLOWER, Theme.MONEY + "<b>" + COIN + " " + plugin.economy().tokens(player) + " tokens",
                Lore.of().line("<white>" + XpRules.tag(p.level()) + " Level " + p.level()).line("<gray>" + XpRules.bar(p, 10))));
    }

    @Override public boolean live(Player player) { return true; }
}
