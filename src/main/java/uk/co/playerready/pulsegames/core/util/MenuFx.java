package uk.co.playerready.pulsegames.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Animated glass-pane borders for menu GUIs: the Pulse gradient (red, orange,
 * yellow) flows around the edge with a couple of white sparks chasing it.
 * Players in calm mode get a static border instead - no flashing.
 */
public final class MenuFx implements Listener {

    /** The flowing wave, in perimeter order; repeated around the border. */
    private static final Material[] WAVE = {
            Material.RED_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
    };
    private static final Material SPARK = Material.WHITE_STAINED_GLASS_PANE;
    private static final long PERIOD_TICKS = 4L;

    private final JavaPlugin plugin;
    private final Map<Material, ItemStack> panes = new EnumMap<>(Material.class);
    private final Map<UUID, Inventory> animating = new HashMap<>();
    private BukkitTask task;
    private int frame;

    public MenuFx(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Draws the border, shows the menu and (outside calm mode) starts animating it. */
    public void open(Player player, Inventory inv) {
        if (inv.getSize() < 27) { // too small for a border
            player.openInventory(inv);
            return;
        }
        drawBorder(inv, 0);
        player.openInventory(inv);
        if (!Text.calm(player)) {
            animating.put(player.getUniqueId(), inv);
            ensureTask();
        }
    }

    /** Interior (non-border) slots of an inventory, top-left to bottom-right. */
    public static List<Integer> interiorSlots(int size) {
        int rows = size / 9;
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private void drawBorder(Inventory inv, int frame) {
        List<Integer> perimeter = perimeterSlots(inv.getSize());
        int sparkA = Math.floorMod(frame, perimeter.size());
        int sparkB = Math.floorMod(frame + perimeter.size() / 2, perimeter.size());
        for (int i = 0; i < perimeter.size(); i++) {
            Material material = (i == sparkA || i == sparkB)
                    ? SPARK
                    : WAVE[Math.floorMod(i - frame, WAVE.length)];
            inv.setItem(perimeter.get(i), pane(material));
        }
    }

    /** Border slots ordered clockwise so the animation flows around the edge. */
    private static List<Integer> perimeterSlots(int size) {
        int rows = size / 9;
        List<Integer> slots = new ArrayList<>();
        for (int col = 0; col < 9; col++) slots.add(col);                          // top, left->right
        for (int row = 1; row < rows - 1; row++) slots.add(row * 9 + 8);           // right, down
        for (int col = 8; col >= 0; col--) slots.add((rows - 1) * 9 + col);        // bottom, right->left
        for (int row = rows - 2; row >= 1; row--) slots.add(row * 9);              // left, up
        return slots;
    }

    private ItemStack pane(Material material) {
        return panes.computeIfAbsent(material, m -> {
            ItemStack item = new ItemStack(m);
            item.editMeta(meta -> meta.setHideTooltip(true));
            return item;
        });
    }

    private void ensureTask() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    private void tick() {
        frame++;
        animating.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || player.getOpenInventory().getTopInventory() != entry.getValue()) {
                return true;
            }
            drawBorder(entry.getValue(), frame);
            return false;
        });
        if (animating.isEmpty()) {
            task.cancel();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (animating.get(uuid) == event.getInventory()) animating.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        animating.remove(event.getPlayer().getUniqueId());
    }
}
