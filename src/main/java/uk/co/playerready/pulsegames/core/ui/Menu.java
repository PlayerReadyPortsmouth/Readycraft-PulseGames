package uk.co.playerready.pulsegames.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One chest menu for one player. Build it with {@link #set}, show it with {@link #open()}, and change it
 * with {@link #refresh()}: that re-renders into the same window (only changed stacks are sent), so the
 * client never sees a window close and reopen. Clicks are handled by {@link MenuListener}, always next tick.
 *
 * <p>Fixed navigation slots: 27-slot menus use Back 18 and Close 26; 54-slot menus use Back 45,
 * Prev 48, Page 49, Next 50 and Close 53.
 */
public final class Menu implements InventoryHolder {
    final Plugin plugin;
    final UUID owner;
    private final Component title;
    private final int size;
    private final ItemStack[] stacks;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private Runnable renderer = () -> { };
    Inventory inventory;

    public Menu(Plugin plugin, Player owner, Component title, int rows) {
        this.plugin = plugin;
        this.owner = owner.getUniqueId();
        this.title = title;
        this.size = Math.max(1, Math.min(6, rows)) * 9;
        this.stacks = new ItemStack[size];
    }

    @Override public Inventory getInventory() { return inventory; }
    public int size() { return size; }
    public UUID owner() { return owner; }

    // Navigation slots.
    public int backSlot() { return size - 9; }
    public int prevSlot() { return size - 6; }
    public int pageSlot() { return size - 5; }
    public int nextSlot() { return size - 4; }
    public int closeSlot() { return size - 1; }

    /** Staging area for the next frame: nothing reaches the player until {@link #open()} or {@link #refresh()}. */
    public Menu set(int slot, ItemStack stack, Consumer<Player> action) {
        if (slot < 0 || slot >= size) return this;
        stacks[slot] = stack;
        if (action != null) actions.put(slot, action); else actions.remove(slot);
        return this;
    }

    public Menu set(int slot, ItemStack stack) { return set(slot, stack, null); }

    /** Fills every still-empty slot with a tooltip-free pane. */
    public Menu fillEmpty(Material pane) {
        ItemStack filler = Buttons.filler(pane);
        for (int i = 0; i < size; i++) if (stacks[i] == null) stacks[i] = filler;
        return this;
    }

    public Menu back(Consumer<Player> action) { return set(backSlot(), Buttons.back(), action); }

    public Menu close() { return set(closeSlot(), Buttons.close(), Player::closeInventory); }

    /** Prev / page / next on the fixed 54-slot slots; hidden when there is only one page. */
    public Menu pager(Paginator.Page<?> page, Runnable prev, Runnable next) {
        if (page.pages() <= 1) return this;
        int human = page.page() + 1;
        if (page.hasPrev()) set(prevSlot(), Buttons.prev(human, page.pages()), p -> prev.run());
        set(pageSlot(), Buttons.pageIndicator(human, page.pages()));
        if (page.hasNext()) set(nextSlot(), Buttons.next(human, page.pages()), p -> next.run());
        return this;
    }

    /** The code that fills the staging area; {@link #refresh()} clears it and runs this again. */
    public Menu renderer(Runnable renderer) {
        this.renderer = Objects.requireNonNull(renderer);
        return this;
    }

    /** Re-render into the open window. Only slots whose stack changed are written. */
    public void refresh() {
        for (int i = 0; i < size; i++) stacks[i] = null;
        actions.clear();
        renderer.run();
        commit();
    }

    void commit() {
        if (inventory == null) return;
        for (int i = 0; i < size; i++)
            if (!Objects.equals(inventory.getItem(i), stacks[i])) inventory.setItem(i, stacks[i]);
    }

    /** Render and show. Safe to call from a click action (those run next tick). */
    public void open(Player player) {
        if (inventory == null) attach(Bukkit.createInventory(this, size, title));
        refresh();
        Theme.sound(player, Theme.Cue.OPEN);
        player.openInventory(inventory);
    }

    void attach(Inventory inventory) { this.inventory = inventory; }

    Consumer<Player> action(int slot) { return actions.get(slot); }
}
