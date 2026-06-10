package uk.co.playerready.pulsegames.games.luckypillars;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Lucky Pillars: stranded on pillars, random lucky drops every few seconds.
 * Bridge, fight and knock everyone else into the void!
 * Modes: classic, chaos (faster drops + wild events).
 */
public final class LuckyPillarsGame extends MiniGame {

    private final List<Consumer<Player>> drops = List.of(
            p -> give(p, named(enchant(new ItemStack(Material.STICK), Enchantment.KNOCKBACK, 2), "<gold>Yeet Stick"), "a Yeet Stick"),
            p -> give(p, new ItemStack(Material.SNOWBALL, 8), "8 snowballs"),
            p -> give(p, new ItemStack(Material.ENDER_PEARL), "an ender pearl"),
            p -> give(p, new ItemStack(Material.COBWEB, 2), "cobwebs"),
            p -> give(p, new ItemStack(Material.OAK_PLANKS, 12), "building blocks"),
            p -> give(p, new ItemStack(Material.STONE_AXE), "a stone axe"),
            p -> give(p, new ItemStack(Material.SHIELD), "a shield"),
            p -> give(p, new ItemStack(Material.FISHING_ROD), "a fishing rod"),
            p -> give(p, new ItemStack(Material.GOLDEN_APPLE), "a golden apple"),
            p -> give(p, new ItemStack(Material.SLIME_BLOCK, 3), "slime blocks"));

    public LuckyPillarsGame(GameInstance game) {
        super(game);
    }

    private boolean isChaos() { return game.mode().id().equals("chaos"); }

    private int dropInterval() {
        return game.arena().settings().getInt("drop-interval", isChaos() ? 6 : 10);
    }

    @Override
    public void onStart() {
        game.broadcast("Lucky drops every <yellow>" + dropInterval() + "s</yellow>! Knock the others off!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (gameTime == 0 || gameTime % dropInterval() != 0) return;
        var random = ThreadLocalRandom.current();
        for (Player player : game.alivePlayers()) {
            drops.get(random.nextInt(drops.size())).accept(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
        }
        if (isChaos() && random.nextInt(4) == 0) {
            chaosEvent();
        }
    }

    private void chaosEvent() {
        var random = ThreadLocalRandom.current();
        switch (random.nextInt(3)) {
            case 0 -> {
                game.broadcast("<light_purple><b>CHAOS:</b> Everybody up!");
                game.alivePlayers().forEach(p ->
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1)));
            }
            case 1 -> {
                game.broadcast("<light_purple><b>CHAOS:</b> Moon jumps!");
                game.alivePlayers().forEach(p ->
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 3)));
            }
            case 2 -> {
                List<Player> alive = game.alivePlayers();
                if (alive.size() < 2) return;
                game.broadcast("<light_purple><b>CHAOS:</b> Swap!");
                Player a = alive.get(random.nextInt(alive.size()));
                Player b = alive.get(random.nextInt(alive.size()));
                var locA = a.getLocation().clone();
                a.teleport(b.getLocation());
                b.teleport(locA);
            }
        }
    }

    private static ItemStack enchant(ItemStack item, Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    private static ItemStack named(ItemStack item, String name) {
        item.editMeta(meta -> meta.displayName(Text.mm(name)));
        return item;
    }

    private static void give(Player player, ItemStack item, String description) {
        player.getInventory().addItem(item);
        player.sendActionBar(Text.mm("<gold>Lucky drop: <yellow>" + description));
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
    public boolean pvp() { return true; }

    @Override
    public boolean canPlace(Player player, Block block) { return true; }

    @Override
    public boolean canBreak(Player player, Block block) { return true; }

    @Override
    public int timeLimitSeconds() { return 420; }
}
