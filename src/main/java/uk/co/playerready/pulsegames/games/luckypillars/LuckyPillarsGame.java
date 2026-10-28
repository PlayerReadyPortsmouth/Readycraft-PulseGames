package uk.co.playerready.pulsegames.games.luckypillars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lucky Pillars: stranded on pillars, random lucky drops every few seconds.
 * Bridge, fight and knock everyone else into the void!
 * Modes: classic, chaos (adds timed chaos events, see {@link LuckyChaos}).
 * Drops come from {@link LuckyDrops}: weak early, stronger later, with catch-up for the unarmed.
 */
public final class LuckyPillarsGame extends MiniGame {

    public static final int DEFAULT_DROP_INTERVAL = 5;
    public static final int MIN_DROP_INTERVAL = 3;
    static final int MAX_GROUND_OVERFLOW = 30;

    private final Map<UUID, Map<String, Integer>> received = new HashMap<>();
    private LuckyChaos.Director chaos;
    private int overflowOnGround;
    /** Overflow drops from {@link #give}; only these may be picked up. */
    private final java.util.Set<java.util.UUID> overflowItems = new java.util.HashSet<>();

    public LuckyPillarsGame(GameInstance game) {
        super(game);
    }

    private boolean isChaos() { return game.mode().rulesId().equals("chaos"); }

    /** Arena {@code drop-interval} override, never faster than {@link #MIN_DROP_INTERVAL}. */
    static int dropInterval(int configured) {
        return Math.max(MIN_DROP_INTERVAL, configured);
    }

    private int dropInterval() {
        return dropInterval(game.arena().settings().getInt("drop-interval", DEFAULT_DROP_INTERVAL));
    }

    @Override
    public void onStart() {
        game.broadcast("Lucky drops every <yellow>" + dropInterval() + "s</yellow>! Knock the others off!");
        if (isChaos()) {
            chaos = new LuckyChaos.Director(game, ThreadLocalRandom.current(), () -> roll(null), this::giveDrop);
            chaos.start();
        }
    }

    @Override
    public void onSecond(int gameTime) {
        if (gameTime == 0 || gameTime % dropInterval() != 0) return;
        for (Player player : game.alivePlayers()) {
            LuckyDrops.Drop drop = roll(player);
            if (drop == null) continue;
            giveDrop(player, drop);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
        }
    }

    /** Time-scaled weighted roll; with a player, applies their caps and weapon catch-up. */
    private LuckyDrops.Drop roll(Player player) {
        double progress = LuckyDrops.progress(game.gameTime());
        Set<String> capped = new HashSet<>();
        boolean unarmed = false;
        if (player != null) {
            var counts = received.getOrDefault(player.getUniqueId(), Map.of());
            for (LuckyDrops.Drop d : LuckyDrops.ALL) {
                if (d.cap() > 0 && counts.getOrDefault(d.id(), 0) >= d.cap()) capped.add(d.id());
            }
            unarmed = !armed(player);
        } else {
            for (LuckyDrops.Drop d : LuckyDrops.ALL) if (d.cap() > 0) capped.add(d.id());
        }
        return LuckyDrops.pick(LuckyDrops.ALL, progress, unarmed, capped, ThreadLocalRandom.current());
    }

    static boolean armed(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && LuckyDrops.WEAPONS.contains(item.getType())) return true;
        }
        return false;
    }

    private void giveDrop(Player player, LuckyDrops.Drop drop) {
        if (drop.cap() > 0) received.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).merge(drop.id(), 1, Integer::sum);
        give(player, drop.item().get(), drop.label());
    }

    /** Placed TNT lights itself, so the drop is usable without flint and steel. */
    @Override
    public void onBlockPlaced(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.TNT) return;
        block.setType(Material.AIR, false);
        Location at = block.getLocation().add(0.5, 0, 0.5);
        at.getWorld().spawn(at, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(50);
            tnt.setSource(event.getPlayer());
        });
    }

    @Override
    public void onEnd(List<Player> winners) {
        shutdownExtras();
    }

    /** A quit or kick mid-event must not take a chaos scale or gravity change back to the lobby. */
    @Override
    public void onQuit(Player player) {
        if (chaos != null) chaos.restoreFor(player);
    }

    @Override
    public void onCleanup() {
        shutdownExtras();
    }

    /** Restore chaos changes and drop egg-spawned mobs; safe to call from both end and cleanup. */
    private void shutdownExtras() {
        if (chaos != null) chaos.shutdown();
        World world = game.world();
        if (world == null) return;
        for (Entity e : world.getEntities()) {
            if (e instanceof Mob || e instanceof TNTPrimed) e.remove();
        }
    }

    void give(Player player, ItemStack item, String description) {
        if (!game.isActive(player) || player.getWorld() != game.world()) return;
        var leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            if (overflowOnGround >= MAX_GROUND_OVERFLOW) continue;
            overflowOnGround++;
            var dropped = game.world().dropItem(player.getLocation().clone(), leftover);
            dropped.setVelocity(new org.bukkit.util.Vector());
            dropped.setPickupDelay(10);
            overflowItems.add(dropped.getUniqueId());
        }
        player.sendActionBar(Text.mm("<gold>Lucky drop: <yellow>" + description
                + (leftovers.isEmpty() ? "" : " <gray>(inventory full; dropped beside you)")));
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        if (killer != null) {
            game.broadcast("<red>" + victim.getName() + "</red> was knocked off by <yellow>" + killer.getName() + "</yellow>!");
            game.stats().addKill(killer, game.type().id());
        } else {
            game.broadcast("<red>" + victim.getName() + "</red> fell!");
        }
        game.eliminate(victim);
    }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) {
        return overflowItems.remove(item.getUniqueId());
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean canPlace(Player player, Block block) { return true; }

    @Override
    public boolean canExplode(Block block) { return true; }

    @Override
    public boolean canBreak(Player player, Block block) { return true; }

    @Override
    public int timeLimitSeconds() { return 420; }
}
