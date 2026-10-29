package uk.co.playerready.pulsegames.core.ui.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import uk.co.playerready.pulsegames.core.ui.Lore;
import uk.co.playerready.pulsegames.core.ui.Menu;
import uk.co.playerready.pulsegames.core.ui.Theme;

import java.util.List;
import java.util.function.Consumer;

/**
 * A tabbed 54-slot shop on {@link Menu}: tab row, optional quick-buy row, one page of entries, a footer
 * (balance, or a Ready button) and Close. Clicks run next tick through the menu kit, every click and drag is
 * cancelled there, and a click re-renders the same window instead of reopening it.
 */
public final class TabbedShop {

    /** @param spaced entries sit one slot apart (few, big targets). @param hint shown when the tab has no entries. */
    public record Tab(String id, Material icon, String title, boolean spaced, String hintTitle, String hint) {
        public static Tab of(String id, Material icon, String title) { return new Tab(id, icon, title, false, null, null); }
    }

    /** @param action null for a tile that only informs. */
    public record Entry(ItemStack stack, Consumer<Player> action) {
        public static Entry info(ItemStack stack) { return new Entry(stack, null); }
    }

    public interface Source {
        Plugin plugin();
        List<Tab> tabs(Player player);
        /** Always-visible row; may be empty. */
        List<Entry> quick(Player player);
        List<Entry> entries(Player player, Tab tab);
        /** Bottom-centre tile: the balance, or a Ready button. */
        Entry footer(Player player);
        /** False once the child can no longer shop (round over, downed). Actions then do nothing. */
        boolean live(Player player);
    }

    private final Source source;
    private final Component title;

    public TabbedShop(Source source, String screen) {
        this.source = source;
        this.title = Theme.title(screen);
    }

    public Menu open(Player player, String initialTab) {
        Menu menu = new Menu(source.plugin(), player, title, ShopLayout.ROWS);
        String[] selected = {initialTab};
        menu.renderer(() -> render(menu, player, selected));
        menu.open(player);
        return menu;
    }

    private void render(Menu menu, Player player, String[] selected) {
        List<Tab> tabs = source.tabs(player);
        if (tabs.stream().noneMatch(t -> t.id().equals(selected[0]))) selected[0] = tabs.get(0).id();
        Tab current = tabs.stream().filter(t -> t.id().equals(selected[0])).findFirst().orElseThrow();
        for (int i = 0; i < Math.min(tabs.size(), ShopLayout.TABS); i++) {
            Tab tab = tabs.get(i);
            menu.set(i, ShopTiles.tab(tab.icon(), tab.title(), tab == current), p -> { selected[0] = tab.id(); menu.refresh(); });
        }
        List<Entry> quick = source.quick(player);
        for (int i = 0; i < Math.min(quick.size(), ShopLayout.QUICK_CAPACITY); i++) put(menu, ShopLayout.quickSlot(i), quick.get(i));
        List<Entry> page = source.entries(player, current);
        if (page.isEmpty() && current.hint() != null) {
            menu.set(ShopLayout.HINT, ShopTiles.info(Material.BOOK, "<yellow>" + current.hintTitle(),
                    Lore.of().desc(current.hint())));
        }
        int cap = current.spaced() ? ShopLayout.SPACED_CAPACITY : ShopLayout.PAGE_CAPACITY;
        for (int i = 0; i < Math.min(page.size(), cap); i++)
            put(menu, current.spaced() ? ShopLayout.spacedSlot(i) : ShopLayout.pageSlot(i), page.get(i));
        put(menu, ShopLayout.FOOTER, source.footer(player));
        menu.close();
        menu.fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private void put(Menu menu, int slot, Entry entry) {
        if (entry.action() == null) { menu.set(slot, entry.stack()); return; }
        menu.set(slot, entry.stack(), p -> {
            if (!source.live(p)) return;
            entry.action().accept(p);
            if (p.getOpenInventory().getTopInventory() == menu.getInventory()) menu.refresh();
        });
    }
}
