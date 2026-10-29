package uk.co.playerready.pulsegames.games.readyplayerz;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.shop.ShopFeedback;
import uk.co.playerready.pulsegames.core.ui.shop.ShopFeedback.Outcome;
import uk.co.playerready.pulsegames.core.ui.shop.ShopRules;
import uk.co.playerready.pulsegames.core.ui.shop.ShopTiles;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop.Entry;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop.Tab;

import java.util.ArrayList;
import java.util.List;

/**
 * The ReadyPlayerZ Supply Shop on the shared tabbed shop. Prices, points and Pack-a-Punch rules are untouched:
 * every click goes through {@link ReadyPlayerZGame#purchase}, the same call the bots use.
 */
final class ZShopMenu implements TabbedShop.Source {
    private final ReadyPlayerZGame game;
    private final TabbedShop shop = new TabbedShop(this, "Supply Shop");

    ZShopMenu(ReadyPlayerZGame game) { this.game = game; }

    void open(Player player) { shop.open(player, ZShelf.WEAPONS.name()); }

    @Override public Plugin plugin() { return game.plugin(); }
    @Override public boolean live(Player player) { return game.shopOpen(player); }
    @Override public List<Entry> quick(Player player) { return List.of(); }

    @Override public List<Tab> tabs(Player player) {
        List<Tab> tabs = new ArrayList<>();
        for (ZShelf shelf : ZShelf.values()) {
            if (shelf == ZShelf.SPECIAL && specials().isEmpty()) continue;
            tabs.add(Tab.of(shelf.name(), shelf.icon, shelf.title));
        }
        return tabs;
    }

    /** Box and Pack-a-Punch only appear here when the map has no physical one. */
    private List<Integer> specials() {
        List<Integer> out = new ArrayList<>();
        if (game.boxLocations().isEmpty()) out.add(ReadyPlayerZGame.BOX_SLOT);
        if (game.packLocation() == null) out.add(ReadyPlayerZGame.PAP_SLOT);
        return out;
    }

    @Override public List<Entry> entries(Player player, Tab tab) {
        ZShelf shelf = ZShelf.valueOf(tab.id());
        List<Integer> slots = shelf == ZShelf.SPECIAL ? specials() : shelf.slots;
        return slots.stream().map(slot -> entry(player, slot)).toList();
    }

    @Override public Entry footer(Player player) {
        return Entry.info(ShopTiles.info(Material.EMERALD, "<gold><b>Your points",
                Lore.of().kv("Points", String.valueOf(game.points(player)))));
    }

    private static String unit(int n) { return n == 1 ? "point" : "points"; }

    private Entry entry(Player player, int slot) {
        int have = game.points(player);
        String name, blurb;
        int cost;
        ItemStack base;
        boolean active = false;
        if (slot == ReadyPlayerZGame.BOX_SLOT) {
            name = "Mystery Box"; blurb = "A random weapon or perk. The price goes up a little each time.";
            cost = game.boxPrice(player); base = new ItemStack(Material.ENDER_CHEST);
        } else if (slot == ReadyPlayerZGame.PAP_SLOT) {
            name = "Pack-a-Punch"; blurb = "Hold a weapon first. Makes it hit harder, once.";
            cost = ZMysteryBox.PACK_A_PUNCH_PRICE; base = new ItemStack(Material.ANVIL);
        } else {
            ReadyPlayerZGame.Upgrade u = ReadyPlayerZGame.UPGRADES.get(slot);
            name = u.name(); blurb = u.blurb(); cost = u.cost(); base = new ItemStack(u.icon());
            active = u.perk() != null && player.hasPotionEffect(u.perk().get());
        }
        boolean bagFull = ZShelf.needsBagSpace(slot) && player.getInventory().firstEmpty() < 0;
        if (bagFull) blurb += " Your bag is full: make some space first.";
        ShopRules.Verdict verdict = ShopRules.afford(have, cost);
        ItemStack stack = ShopTiles.offer(base, new ShopTiles.Offer(name, blurb, cost, ZShopMenu::unit, have, verdict,
                active, active ? "Click to renew" : "Click to buy"));
        String what = name;
        return new Entry(stack, p -> {
            int points = game.points(p);
            if (game.purchase(p, slot)) return; // purchase() pays, plays its sound and says "Bought ..."
            int shortfall = Math.max(0, cost - points);
            Outcome outcome = shortfall > 0 ? Outcome.NEED_MORE
                    : ZShelf.needsBagSpace(slot) && p.getInventory().firstEmpty() < 0 ? Outcome.NO_ROOM : Outcome.UNAVAILABLE;
            ShopFeedback.play(p, outcome, shortfall, shortfall == 1 ? "point" : "points", what);
        });
    }
}
