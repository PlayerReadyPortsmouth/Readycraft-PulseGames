package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skywars: loot your island, bridge out, knock everyone else into the void.
 * Modes: solo, doubles, insane (OP loot).
 * Chests are filled lazily (random loot) the first time each one is opened.
 */
public final class SkywarsGame extends MiniGame {

    private static final List<ItemStack> BASIC_LOOT = List.of(
            new ItemStack(Material.STONE_SWORD), new ItemStack(Material.IRON_SWORD),
            new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 8),
            new ItemStack(Material.IRON_HELMET), new ItemStack(Material.IRON_CHESTPLATE),
            new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_BOOTS),
            new ItemStack(Material.CHAINMAIL_CHESTPLATE), new ItemStack(Material.LEATHER_BOOTS),
            new ItemStack(Material.OAK_PLANKS, 32), new ItemStack(Material.COBBLESTONE, 32),
            new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.COOKED_BEEF, 6),
            new ItemStack(Material.STONE_AXE), new ItemStack(Material.IRON_PICKAXE),
            new ItemStack(Material.SNOWBALL, 8), new ItemStack(Material.ENDER_PEARL, 1),
            new ItemStack(Material.WATER_BUCKET), new ItemStack(Material.FISHING_ROD));

    private static final List<ItemStack> INSANE_LOOT = List.of(
            new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.DIAMOND_CHESTPLATE),
            new ItemStack(Material.DIAMOND_HELMET), new ItemStack(Material.DIAMOND_LEGGINGS),
            new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.ENDER_PEARL, 2),
            new ItemStack(Material.GOLDEN_APPLE, 4), new ItemStack(Material.BOW),
            new ItemStack(Material.ARROW, 24), new ItemStack(Material.OAK_PLANKS, 64),
            new ItemStack(Material.TNT, 4), new ItemStack(Material.LAVA_BUCKET));

    private final Set<Location> filledChests = new HashSet<>();
    private final Set<Location> playerBlocks = new HashSet<>();

    public SkywarsGame(GameInstance game) {
        super(game);
    }

    private boolean isInsane() { return game.mode().id().equals("insane"); }

    @Override
    public void onStart() {
        game.broadcast("Loot up and fight! Last " + (game.mode().isTeams() ? "team" : "player")
                + " standing wins!");
    }

    private int refillAt() {
        return game.arena().settings().getInt("refill-seconds", 300);
    }

    @Override
    public void onSecond(int gameTime) {
        if (gameTime == refillAt() - 60) {
            game.broadcast("<yellow>Chests refill in <gold>60s</gold>!");
        } else if (gameTime == refillAt()) {
            filledChests.clear();
            game.broadcast("<gold><b>CHESTS REFILLED!</b></gold> <gray>Open them again for fresh loot.");
            for (org.bukkit.entity.Player p : game.alivePlayers()) {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.2f);
            }
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getState() instanceof Chest chest) {
            Location key = event.getClickedBlock().getLocation();
            if (filledChests.add(key)) {
                fillChest(chest);
            }
        }
    }

    private void fillChest(Chest chest) {
        var random = ThreadLocalRandom.current();
        List<ItemStack> pool = isInsane() ? INSANE_LOOT : BASIC_LOOT;
        chest.getBlockInventory().clear();
        int items = random.nextInt(4, 7);
        for (int i = 0; i < items; i++) {
            int slot = random.nextInt(chest.getBlockInventory().getSize());
            chest.getBlockInventory().setItem(slot, pool.get(random.nextInt(pool.size())).clone());
        }
    }

    @Override
    public boolean canBreak(Player player, Block block) { return true; }

    @Override
    public boolean canPlace(Player player, Block block) {
        playerBlocks.add(block.getLocation());
        return true;
    }

    @Override
    public boolean pvp() { return true; }

    @Override
    public boolean fallDamage() { return true; }

    @Override
    public boolean hunger() { return true; }

    @Override
    public boolean itemDrops() { return true; }

    @Override
    public boolean itemPickup(Player player, org.bukkit.entity.Item item) { return true; }

    @Override
    public void onDeath(Player victim, Player killer) {
        // Drop their loot for the killer.
        for (ItemStack item : victim.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                game.world().dropItemNaturally(victim.getLocation(), item);
            }
        }
        super.onDeath(victim, killer);
    }

    @Override
    public int startFreezeSeconds() { return 5; }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 900);
    }
}
