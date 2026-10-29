package uk.co.playerready.pulsegames.games.supermarket;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
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
    private final Set<UUID> toldToCheckout = new HashSet<>();
    private static final int COMPASS_SLOT = 8;
    private static final String COMPASS_NAME = "Checkout";
    private static final String HINT = "<yellow>Carry your items to the green checkout at the tills to tick them off.";

    private boolean hints() { return game.plugin() == null || game.plugin().getConfig().getBoolean("supermarket.hints.enabled", true); }

    public SupermarketSweepGame(GameInstance game) {
        super(game);
    }

    private boolean isRush() { return game.mode().id().equals("rush"); }

    @Override
    public void onStart() {
        checkout = game.arena().region("checkout");
        List<Material> pool = new ArrayList<>(availablePool());
        if (pool.isEmpty()) throw new IllegalStateException("No eligible shopping items; validate the arena item-pool");
        java.util.Collections.shuffle(pool, new java.util.Random(ThreadLocalRandom.current().nextLong()));
        int size = Math.max(1, game.arena().settings().getInt("list-size", isRush() ? 4 : 6));
        shoppingList = List.copyOf(pool.subList(0, Math.min(size, pool.size())));
        game.alivePlayers().forEach(p -> collected.put(p.getUniqueId(), new HashSet<>()));
        game.broadcast("<yellow><b>Shopping list:</b></yellow> <white>" + listText());
        game.broadcast("Raid the shelves, then bring everything to the <green>checkout</green>!");
        if (hints() && checkout != null) {
            game.broadcast(HINT);
            game.alivePlayers().forEach(this::giveCompass);
        }
    }

    private void giveCompass(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.displayName(Text.mm("<!italic><green>" + COMPASS_NAME));
        meta.setLodestone(checkout.center(game.world()));
        meta.setLodestoneTracked(false);
        compass.setItemMeta(meta);
        player.getInventory().setItem(COMPASS_SLOT, compass);
    }

    private static boolean isCheckoutCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()
                && Text.plain(Text.legacy(item.getItemMeta().displayName())).contains(COMPASS_NAME);
    }

    @Override
    public void onEnd(List<Player> winners) {
        for (Player p : game.world().getPlayers()) {
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                if (isCheckoutCompass(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
            }
        }
    }

    @Override
    public void onSecond(int gameTime) {
        if (!hints() || checkout == null) return;
        if (gameTime == timeLimitSeconds() - 60 && gameTime > 0) game.broadcast(HINT);
        World world = game.world();
        List<Player> viewers = game.alivePlayers();
        if (viewers.isEmpty()) return;
        double x0 = checkout.minX(), x1 = checkout.maxX() + 1.0, z0 = checkout.minZ(), z1 = checkout.maxZ() + 1.0;
        double floor = checkout.minY() + 0.2;
        // Floor-edge ring: at most ~24 points however big the region, plus a short column at the centre.
        double perimeter = 2 * ((x1 - x0) + (z1 - z0));
        int points = (int) Math.max(8, Math.min(24, perimeter / 2));
        for (int i = 0; i < points; i++) {
            double[] p = perimeterPoint(x0, z0, x1, z1, (double) i / points);
            world.spawnParticle(Particle.HAPPY_VILLAGER, p[0], floor, p[1], 1, 0, 0, 0, 0);
        }
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        for (int i = 0; i < 6; i++) world.spawnParticle(Particle.END_ROD, cx, floor + i * 0.6, cz, 1, 0, 0, 0, 0);
    }

    /** Point at fraction t (0..1) around the rectangle's edge. */
    static double[] perimeterPoint(double x0, double z0, double x1, double z1, double t) {
        double w = x1 - x0, d = z1 - z0, run = (t - Math.floor(t)) * 2 * (w + d);
        if (run < w) return new double[]{x0 + run, z0};
        run -= w;
        if (run < d) return new double[]{x1, z0 + run};
        run -= d;
        if (run < w) return new double[]{x1 - run, z1};
        run -= w;
        return new double[]{x0, z1 - run};
    }

    @Override
    public boolean itemDrops(ItemStack stack) { return !isCheckoutCompass(stack); }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        if (isCheckoutCompass(event.getCurrentItem()) || isCheckoutCompass(event.getCursor())
                || (event.getHotbarButton() == COMPASS_SLOT)
                || (event.getHotbarButton() >= 0 && isCheckoutCompass(player.getInventory().getItem(event.getHotbarButton())))) {
            event.setCancelled(true);
        }
    }

    @Override
    public String startProblem(List<Player> participants) {
        try {
            if (availablePool().isEmpty()) return "Supermarket item-pool must contain at least one obtainable item";
            return null;
        } catch (IllegalStateException invalid) { return invalid.getMessage(); }
    }

    /** Shelf stock remains a map acceptance check: this game has no authored stock bounds. */
    private List<Material> availablePool() {
        java.util.LinkedHashSet<Material> pool = new java.util.LinkedHashSet<>();
        List<String> configured = game.arena().settings().getStringList("item-pool");
        if (configured.isEmpty()) pool.addAll(DEFAULT_POOL);
        else for (String name : configured) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isItem() && !material.isAir()) pool.add(material);
        }
        return List.copyOf(pool);
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
        checkOut(player);
    }

    private void checkOut(Player player) {
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
        toldToCheckout.remove(player.getUniqueId());
    }

    /** Highest count wins and ties share it; nobody wins on zero items (idling must not pay a win). */
    static <T> List<T> topCollectors(java.util.Map<T, Integer> counts) {
        int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (best <= 0) return List.of();
        return counts.entrySet().stream().filter(e -> e.getValue() == best).map(java.util.Map.Entry::getKey).toList();
    }

    @Override
    public void onTimeUp() {
        java.util.Map<Player, Integer> counts = new java.util.LinkedHashMap<>();
        for (Player p : game.alivePlayers()) {
            counts.put(p, collected.getOrDefault(p.getUniqueId(), Set.of()).size());
        }
        game.end(topCollectors(counts), "time");
    }

    @Override
    public List<String> sidebar(Player player) {
        Set<Material> mine = collected.getOrDefault(player.getUniqueId(), Set.of());
        List<String> lines = new ArrayList<>();
        for (Material material : shoppingList) {
            lines.add((mine.contains(material) ? "<green>✔ " : "<gray>• ") + pretty(material));
        }
        if (hints() && checkout != null && game.world() != null) {
            Location at = player.getLocation();
            boolean in = CheckoutPointer.inside(checkout.minX(), checkout.minY(), checkout.minZ(),
                    checkout.maxX(), checkout.maxY(), checkout.maxZ(), at.getX(), at.getY(), at.getZ());
            double dist = CheckoutPointer.distance(checkout.minX(), checkout.minY(), checkout.minZ(),
                    checkout.maxX(), checkout.maxY(), checkout.maxZ(), at.getX(), at.getY(), at.getZ());
            double dx = (checkout.minX() + checkout.maxX() + 1) / 2.0 - at.getX();
            double dz = (checkout.minZ() + checkout.maxZ() + 1) / 2.0 - at.getZ();
            lines.add(CheckoutPointer.line(in, dist, CheckoutPointer.direction(dx, dz, at.getYaw())));
        }
        return lines;
    }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) {
        Material type = item.getItemStack().getType();
        Set<Material> mine = collected.get(player.getUniqueId());
        if (checkout != null && mine != null && shoppingList.contains(type) && !mine.contains(type)) {
            if (hints() && toldToCheckout.add(player.getUniqueId())) {
                player.sendActionBar(Text.mm("<green>Got it! Take it to the checkout."));
            }
            // The item lands in the inventory after this event: re-run the checkout check next tick.
            game.runLater(1L, () -> {
                if (game.state() != uk.co.playerready.pulsegames.core.game.GameState.RUNNING || !player.isOnline()) return;
                if (checkout.contains(player.getLocation())) checkOut(player);
            });
        }
        return true;
    }

    @Override
    public boolean itemDrops() { return true; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", isRush() ? 180 : 360);
    }
}
