package uk.co.playerready.pulsegames.games.quake;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Theme;
import uk.co.playerready.pulsegames.core.ui.shop.ShopTiles;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop.Entry;
import uk.co.playerready.pulsegames.core.ui.shop.TabbedShop.Tab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quake loadout on the shared tabbed shop: one tab for the weapon, one per ability slot. Nothing costs
 * anything, so tiles say "Selected" in words and with a glint. Choosing re-renders the same window.
 */
final class QuakeLoadoutMenu implements TabbedShop.Source {
    private static final String WEAPON = "weapon", FIRST = "first", SECOND = "second";

    private final QuakeGame game;
    private final TabbedShop shop = new TabbedShop(this, "Quake Loadout");
    private final Map<UUID, Menu> open = new HashMap<>();

    QuakeLoadoutMenu(QuakeGame game) { this.game = game; }

    void open(Player player) { open.put(player.getUniqueId(), shop.open(player, WEAPON)); }

    boolean isOpenFor(Player player) {
        Menu menu = open.get(player.getUniqueId());
        return menu != null && player.getOpenInventory().getTopInventory().getHolder() == menu;
    }

    void forget(Player player) { open.remove(player.getUniqueId()); }

    @Override public Plugin plugin() { return game.plugin(); }
    @Override public boolean live(Player player) { return game.loadoutLive(player); }
    @Override public List<Entry> quick(Player player) { return List.of(); }

    @Override public List<Tab> tabs(Player player) {
        return List.of(Tab.of(WEAPON, Material.IRON_HOE, "Weapon"),
                Tab.of(FIRST, Material.FEATHER, "First ability"), Tab.of(SECOND, Material.RABBIT_FOOT, "Second ability"));
    }

    @Override public List<Entry> entries(Player player, Tab tab) {
        if (WEAPON.equals(tab.id())) {
            return java.util.Arrays.stream(QuakeGame.Weapon.values()).map(w -> new Entry(
                    ShopTiles.choice(w.item, w.label, w.damage + " damage. Fires every " + w.cooldown / 20.0 + " seconds.",
                            game.weapon(player) == w, "Click to use"),
                    p -> game.chooseWeapon(p, w))).toList();
        }
        int target = FIRST.equals(tab.id()) ? 0 : 1;
        return java.util.Arrays.stream(QuakeGame.Ability.values()).map(a -> {
            boolean selected = game.abilities(player).get(target) == a;
            boolean other = game.abilities(player).get(1 - target) == a;
            String note = a.cooldown / 20 + " second cooldown." + (other ? " It is in your other slot: choosing it swaps them." : "");
            return new Entry(ShopTiles.choice(a.item, a.label, note, selected, "Click to use"), p -> game.chooseAbility(p, a, target));
        }).toList();
    }

    @Override public Entry footer(Player player) {
        ItemStack ready = ShopTiles.info(Material.LIME_DYE, Theme.GOOD + "<b>Ready", Lore.of().desc("Keep this loadout and fight!").hint("Click to close"));
        return new Entry(ready, Player::closeInventory);
    }
}
