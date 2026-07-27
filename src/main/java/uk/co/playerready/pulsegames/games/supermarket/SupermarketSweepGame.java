package uk.co.playerready.pulsegames.games.supermarket;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Supermarket Sweep: grab everything on your shopping list from the shelves
 * (chests around the map), then check out first!
 * Modes: classic (6 items), rush (4 items, 3 min).
 * Arena: region checkout; stock the map's chests when building the template.
 */
public final class SupermarketSweepGame extends MiniGame {

    private static final List<Material> DEFAULT_POOL = List.of(
            Material.APPLE, Material.BREAD, Material.CARROT, Material.POTATO, Material.EGG,
            Material.MILK_BUCKET, Material.COOKED_BEEF, Material.PUMPKIN_PIE, Material.COOKIE,
            Material.MELON_SLICE, Material.SWEET_BERRIES, Material.HONEY_BOTTLE, Material.CAKE,
            Material.GOLDEN_CARROT, Material.MUSHROOM_STEW);

    private List<Material> shoppingList = List.of();
    private final Map<UUID, Set<Material>> collected = new HashMap<>();
    private Cuboid checkout;

    public SupermarketSweepGame(GameInstance game) {
        super(game);
    }

    private boolean isRush() { return game.mode().id().equals("rush"); }

    @Override
    public void onStart() {
        checkout = game.arena().region("checkout");
        List<Material> pool = new ArrayList<>();
        for (String name : game.arena().settings().getStringList("item-pool")) {
            Material material = Material.matchMaterial(name);
            if (material != null) pool.add(material);
        }
        if (pool.isEmpty()) pool = new ArrayList<>(DEFAULT_POOL);
        java.util.Collections.shuffle(pool, new java.util.Random(ThreadLocalRandom.current().nextLong()));
        int size = game.arena().settings().getInt("list-size", isRush() ? 4 : 6);
        shoppingList = List.copyOf(pool.subList(0, Math.min(size, pool.size())));
        game.alivePlayers().forEach(p -> collected.put(p.getUniqueId(), new HashSet<>()));
        game.broadcast("<yellow><b>Shopping list:</b></yellow> <white>" + listText());
        game.broadcast("Raid the shelves, then bring everything to the <green>checkout</green>!");
    }

    private String listText() {
        return String.join("<gray>,</gray> ",
                shoppingList.stream().map(m -> pretty(m)).toList());
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        if (checkout == null || !checkout.contains(to)) return;
        Set<Material> mine = collected.get(player.getUniqueId());
        if (mine == null) return;
        boolean any = false;
        for (Material needed : shoppingList) {
            if (mine.contains(needed)) continue;
            if (player.getInventory().contains(needed)) {
                player.getInventory().removeItem(new ItemStack(needed, 1));
                mine.add(needed);
                any = true;
                player.sendMessage(Text.msg("<green>✔ Checked out: <yellow>" + pretty(needed)
                        + "</yellow> <gray>(" + mine.size() + "/" + shoppingList.size() + ")"));
            }
        }
        if (any) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
            if (mine.size() >= shoppingList.size()) {
                game.broadcast("<gold><b>" + player.getName() + " completed their shopping list!");
                game.end(List.of(player), "list-complete");
            }
        }
    }

    @Override
    public void onQuit(Player player) {
        collected.remove(player.getUniqueId());
    }

    @Override
    public void onTimeUp() {
        Player best = null;
        int bestCount = -1;
        for (Player p : game.alivePlayers()) {
            int count = collected.getOrDefault(p.getUniqueId(), Set.of()).size();
            if (count > bestCount) {
                bestCount = count;
                best = p;
            }
        }
        game.end(best == null ? List.of() : List.of(best), "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        Set<Material> mine = collected.getOrDefault(player.getUniqueId(), Set.of());
        List<String> lines = new ArrayList<>();
        for (Material material : shoppingList) {
            lines.add((mine.contains(material) ? "<green>✔ " : "<gray>• ") + pretty(material));
        }
        return lines;
    }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) { return true; }

    @Override
    public boolean itemDrops() { return true; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", isRush() ? 180 : 360);
    }
}
