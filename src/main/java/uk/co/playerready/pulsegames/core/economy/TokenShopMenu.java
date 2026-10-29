package uk.co.playerready.pulsegames.core.economy;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.ui.Buttons;
import uk.co.playerready.pulsegames.core.ui.Confirm;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Paginator;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;

/**
 * The token shop GUI: cosmetics, kit unlocks and boosters, built on the shared {@link Menu} kit.
 * Equipping and buying re-render the open window in place; only switching between the main window
 * and a category opens a new one (different sizes). The balance is shown in every view.
 */
public final class TokenShopMenu {

    private static final int BOOSTER_PRICE = 500;
    private static final int BOOSTER_MINUTES = 60;
    /** Token glyph; always followed by the word "tokens" in case a Bedrock font draws a box. */
    private static final String COIN = "⛀";

    private final PulseGamesPlugin plugin;

    public TokenShopMenu(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private int confirmAt() { return plugin.getConfig().getInt("shop.confirm-at", BOOSTER_PRICE); }

    private ItemStack balance(Player player) {
        return Buttons.item(Material.SUNFLOWER, Theme.MONEY + "<b>" + COIN + " " + plugin.economy().tokens(player) + " tokens",
                Lore.of().desc("Earn tokens by playing games, winning, kills, playtime and daily logins!"));
    }

    public void openMain(Player player) {
        Menu menu = new Menu(plugin, player, Theme.title("Token Shop"), 3);
        menu.renderer(() -> {
            menu.set(4, balance(player));
            Cosmetic.Category[] categories = Cosmetic.Category.values();
            int[] slots = {10, 12, 14, 16};
            for (int i = 0; i < categories.length && i < slots.length; i++) {
                Cosmetic.Category category = categories[i];
                List<Cosmetic> all = CosmeticsService.byCategory(category);
                long owned = all.stream().filter(c -> plugin.economy().isUnlocked(player, c.id())).count();
                menu.set(slots[i], Buttons.item(category.icon, Theme.NAME + category.displayName,
                        Lore.of().kv("Unlocked", owned + "/" + all.size()).hint("Click to browse")),
                        p -> openCategory(p, category));
            }
            boolean active = plugin.economy().boosterActive(player);
            Lore lore = Lore.of().desc(active ? "Active now! Buying again adds more time." : "Doubles all the tokens you earn.")
                    .kv("Lasts", BOOSTER_MINUTES + " minutes").kv("Price", COIN + " " + BOOSTER_PRICE + " tokens")
                    .hint("Click to buy");
            menu.set(22, Buttons.item(Material.EXPERIENCE_BOTTLE, "<light_purple><b>2x Token Booster", lore), p -> askBooster(p));
            menu.close();
        });
        menu.open(player);
    }

    private void askBooster(Player player) {
        int have = plugin.economy().tokens(player);
        Confirm.ask(plugin, player, "Buy booster?", List.of(
                "<light_purple><b>2x Token Booster",
                Theme.TEXT + "Lasts " + BOOSTER_MINUTES + " minutes",
                Theme.TEXT + "Price: " + Theme.MONEY + COIN + " " + BOOSTER_PRICE + " tokens",
                Theme.TEXT + "You have: <white>" + have,
                Theme.TEXT + "After: <white>" + (have >= BOOSTER_PRICE ? ShopRules.balanceAfter(have, BOOSTER_PRICE) : "not enough yet")),
                true, p -> { buyBooster(p); openMain(p); }, this::openMain);
    }

    private void buyBooster(Player player) {
        if (plugin.economy().spend(player, BOOSTER_PRICE)) {
            plugin.economy().activateBooster(player, BOOSTER_MINUTES);
            player.sendMessage(Text.msg("<light_purple><b>2x Token Booster activated</b> <gray>for "
                    + BOOSTER_MINUTES + " minutes!"));
            Theme.sound(player, Theme.Cue.SUCCESS);
        } else {
            tooPoor(player, BOOSTER_PRICE);
        }
    }

    private void tooPoor(Player player, int price) {
        player.sendMessage(Text.msg("<red>You need <gold>" + COIN + " " + price + " tokens</gold> for that <gray>(you have "
                + plugin.economy().tokens(player) + ")"));
        Theme.sound(player, Theme.Cue.ERROR);
    }

    public void openCategory(Player player, Cosmetic.Category category) { openCategory(player, category, 0); }

    private void openCategory(Player player, Cosmetic.Category category, int firstPage) {
        List<Cosmetic> items = CosmeticsService.byCategory(category);
        int rows = ShopRules.rows(items.size());
        int per = ShopRules.perPage(rows);
        int[] page = {firstPage};
        Menu menu = new Menu(plugin, player, Theme.title(category.displayName), rows);
        menu.renderer(() -> {
            Paginator.Page<Cosmetic> shown = Paginator.page(items, per, page[0]);
            page[0] = shown.page();
            String equipped = plugin.economy().equipped(player, category);
            int have = plugin.economy().tokens(player);
            for (int i = 0; i < shown.items().size(); i++) {
                Cosmetic cosmetic = shown.items().get(i);
                menu.set(i, icon(player, cosmetic, cosmetic.id().equals(equipped), have), p -> click(p, menu, category, cosmetic, page[0]));
            }
            menu.set(menu.backSlot() + 1, balance(player));
            menu.back(this::openMain).close().pager(shown, () -> { page[0]--; menu.refresh(); }, () -> { page[0]++; menu.refresh(); });
        });
        menu.open(player);
    }

    private ItemStack icon(Player player, Cosmetic cosmetic, boolean equipped, int have) {
        boolean owned = plugin.economy().isUnlocked(player, cosmetic.id());
        Lore lore = Lore.of().desc(cosmetic.description()).blank();
        if (equipped) lore.status(Theme.GOOD + "Equipped").hint("Click to take it off");
        else if (owned) lore.status("<yellow>Yours").hint("Click to equip");
        else {
            lore.status(Theme.MONEY + COIN + " " + cosmetic.price() + " tokens");
            if (have >= cosmetic.price()) lore.hint("Click to buy");
            else lore.status(Theme.BAD + "Need " + (cosmetic.price() - have) + " more tokens");
        }
        var stack = Buttons.item(cosmetic.icon(), (equipped ? Theme.GOOD + "<b>" : owned ? "<yellow>" : Theme.TEXT) + cosmetic.name(), lore);
        return equipped ? Buttons.glow(stack) : stack;
    }

    private void click(Player player, Menu menu, Cosmetic.Category category, Cosmetic cosmetic, int page) {
        if (plugin.economy().isUnlocked(player, cosmetic.id())) {
            boolean isEquipped = cosmetic.id().equals(plugin.economy().equipped(player, category));
            plugin.economy().equip(player, category, isEquipped ? null : cosmetic.id());
            player.sendMessage(Text.msg(isEquipped ? "Unequipped <yellow>" + cosmetic.name()
                    : "Equipped <yellow>" + cosmetic.name() + "</yellow>!"));
            Theme.sound(player, Theme.Cue.SUCCESS);
            menu.refresh();
            return;
        }
        int have = plugin.economy().tokens(player);
        if (have < cosmetic.price()) { tooPoor(player, cosmetic.price()); menu.refresh(); return; }
        if (!ShopRules.needsConfirm(cosmetic.price(), confirmAt())) { buy(player, category, cosmetic); menu.refresh(); return; }
        Confirm.ask(plugin, player, "Buy " + cosmetic.name() + "?", List.of(
                Theme.NAME + cosmetic.name(),
                Theme.TEXT + "Price: " + Theme.MONEY + COIN + " " + cosmetic.price() + " tokens",
                Theme.TEXT + "You have: <white>" + have,
                Theme.TEXT + "After: <white>" + ShopRules.balanceAfter(have, cosmetic.price())),
                true, p -> { buy(p, category, cosmetic); openCategory(p, category, page); }, p -> openCategory(p, category, page));
    }

    /** Safe to call twice: an item already owned is never charged for again. */
    private void buy(Player player, Cosmetic.Category category, Cosmetic cosmetic) {
        if (plugin.economy().isUnlocked(player, cosmetic.id())) return;
        if (plugin.economy().spend(player, cosmetic.price())) {
            plugin.economy().unlock(player, cosmetic.id());
            plugin.economy().equip(player, category, cosmetic.id());
            player.sendMessage(Text.msg("<green><b>Unlocked " + cosmetic.name() + "!</b> <gray>(equipped)"));
            Theme.sound(player, Theme.Cue.SUCCESS);
        } else {
            tooPoor(player, cosmetic.price());
        }
    }
}
