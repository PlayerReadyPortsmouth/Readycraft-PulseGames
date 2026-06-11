package uk.co.playerready.pulsegames.games.tntrun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;

import java.util.HashSet;
import java.util.Set;

/**
 * TNT Run: the floor crumbles behind you, keep moving!
 * Modes: classic, doublejump.
 */
public final class TntRunGame extends MiniGame {

    private final Set<Location> crumbling = new HashSet<>();

    public TntRunGame(GameInstance game) {
        super(game);
    }

    private boolean isDoubleJump() { return game.mode().id().equals("doublejump"); }

    @Override
    public void onStart() {
        game.broadcast("The floor falls away behind you - keep moving!");
        if (isDoubleJump()) {
            game.broadcast("<yellow>Double-jump enabled! Tap jump twice in the air.");
            for (Player p : game.alivePlayers()) {
                if (uk.co.playerready.pulsegames.core.util.BedrockUtil.isBedrock(p)) {
                    // Geyser doesn't relay double-tap flight toggles; give a boost item instead.
                    org.bukkit.inventory.ItemStack boost = new org.bukkit.inventory.ItemStack(Material.FEATHER);
                    boost.editMeta(meta -> meta.displayName(
                            uk.co.playerready.pulsegames.core.util.Text.mm("<aqua><b>Boost</b> <gray>(tap to leap)")));
                    p.getInventory().setItem(0, boost);
                } else {
                    p.setAllowFlight(true);
                }
            }
        }
    }

    @Override
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!isDoubleJump() || event.getItem() == null || event.getItem().getType() != Material.FEATHER
                || !event.getAction().isRightClick()) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.hasCooldown(Material.FEATHER)) return;
        player.setCooldown(Material.FEATHER, 60);
        boost(player);
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        if (game.frozen()) return;
        crumble(to.clone().subtract(0, 1, 0));
        // Catch the block stood on when jumping from a block edge.
        crumble(from.clone().subtract(0, 1, 0));
    }

    private void crumble(Location under) {
        Block block = under.getBlock();
        if (block.getType().isAir()) {
            block = under.clone().subtract(0, 1, 0).getBlock();
        }
        if (block.getType().isAir()) return;
        Location key = block.getLocation();
        if (!crumbling.add(key)) return;
        final Block target = block;
        game.runLater(8L, () -> {
            // Remove this block and the support block beneath it (classic 2-layer maps).
            Block below = target.getRelative(0, -1, 0);
            target.getWorld().spawnParticle(org.bukkit.Particle.BLOCK,
                    target.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, target.getBlockData());
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_SAND_BREAK, 0.6f, 0.8f);
            target.setType(Material.AIR);
            if (!below.getType().isAir()) below.setType(Material.AIR);
            crumbling.remove(key);
        });
    }

    @Override
    public void onToggleFlight(Player player, PlayerToggleFlightEvent event) {
        if (!isDoubleJump()) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        boost(player);
        game.runLater(60L, () -> {
            if (game.isAlive(player)) player.setAllowFlight(true);
        });
    }

    private void boost(Player player) {
        player.setVelocity(player.getLocation().getDirection().multiply(0.6).add(new Vector(0, 0.9, 0)));
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.5f);
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        game.broadcast("<red>" + victim.getName() + "</red> fell!");
        game.eliminate(victim);
    }

    @Override
    public int timeLimitSeconds() { return 300; }
}
