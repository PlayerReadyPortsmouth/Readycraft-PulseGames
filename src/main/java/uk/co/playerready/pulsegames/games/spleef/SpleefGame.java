package uk.co.playerready.pulsegames.games.spleef;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spleef: dig the floor out from under the other players.
 * Modes: classic, decay (floor rots away), splegg (egg launcher).
 */
public final class SpleefGame extends MiniGame {

    private List<Cuboid> floors = List.of();
    private List<Block> floorBlocks = List.of();

    public SpleefGame(GameInstance game) {
        super(game);
    }

    private boolean isSplegg() { return game.mode().id().equals("splegg"); }
    private boolean isDecay() { return game.mode().id().equals("decay"); }

    @Override
    public void onStart() {
        floors = game.arena().regionsByPrefix("floor");
        List<Block> blocks = new ArrayList<>();
        floors.forEach(region -> blocks.addAll(region.blocks(game.world())));
        floorBlocks = blocks;
        for (Player player : game.alivePlayers()) {
            if (isSplegg()) {
                ItemStack launcher = new ItemStack(Material.GOLDEN_HOE);
                launcher.editMeta(meta -> meta.displayName(Text.mm("<gold><b>Splegg Launcher</b> <gray>(right-click)")));
                player.getInventory().setItem(0, launcher);
            } else {
                ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
                shovel.addUnsafeEnchantment(Enchantment.EFFICIENCY, 10);
                player.getInventory().setItem(0, shovel);
            }
        }
        game.broadcast(isSplegg() ? "Shoot the floor out from under your enemies!"
                : "Dig the floor out from under your enemies!");
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        return !isSplegg() && inFloor(block);
    }

    private boolean inFloor(Block block) {
        return floors.stream().anyMatch(region -> region.contains(block.getLocation()));
    }

    @Override
    public void onSecond(int gameTime) {
        if (isDecay() && gameTime > 15 && !floorBlocks.isEmpty()) {
            int amount = Math.min(3 + gameTime / 15, 12);
            var random = ThreadLocalRandom.current();
            for (int i = 0; i < amount; i++) {
                Block block = floorBlocks.get(random.nextInt(floorBlocks.size()));
                if (!block.getType().isAir()) block.setType(Material.AIR);
            }
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (isSplegg() && event.getAction().isRightClick()
                && event.getItem() != null && event.getItem().getType() == Material.GOLDEN_HOE) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player.hasCooldown(Material.GOLDEN_HOE)) return;
            player.setCooldown(Material.GOLDEN_HOE, 10);
            player.launchProjectile(Egg.class, player.getLocation().getDirection().multiply(2.0));
        }
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Egg && event.getHitBlock() != null && inFloor(event.getHitBlock())) {
            event.getHitBlock().setType(Material.AIR);
        }
        if (event.getEntity() instanceof Egg) event.setCancelled(true);
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        game.broadcast("<red>" + victim.getName() + "</red> fell!");
        game.eliminate(victim);
    }

    @Override
    public int timeLimitSeconds() { return 300; }
}
