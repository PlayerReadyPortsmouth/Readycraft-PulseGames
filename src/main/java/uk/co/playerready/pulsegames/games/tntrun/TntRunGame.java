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
public final class TntRunGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    private final uk.co.playerready.pulsegames.core.bot.SurfaceMemory memory = new uk.co.playerready.pulsegames.core.bot.SurfaceMemory();
    private final java.util.Map<java.util.UUID, Location> destinations = new java.util.HashMap<>();
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
            game.alivePlayers().forEach(p -> p.setAllowFlight(true));
        }
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
        player.setVelocity(player.getLocation().getDirection().multiply(0.6).add(new Vector(0, 0.9, 0)));
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.5f);
        game.runLater(60L, () -> {
            if (game.isAlive(player)) player.setAllowFlight(true);
        });
    }

    @Override
    public Location botDestination(Player bot) {
        memory.remember(bot);
        Location here = bot.getLocation();
        Location previous = destinations.get(bot.getUniqueId());
        if (previous != null && previous.getWorld().equals(here.getWorld())
                && previous.getBlockY() == here.getBlockY()
                && (previous.getBlockX() != here.getBlockX() || previous.getBlockZ() != here.getBlockZ())
                && clearCorridor(here, previous)) return previous.clone();
        Location best = null;
        double bestScore = -Double.MAX_VALUE;
        // Run a short straight corridor continuously. Stopping at every tile is too slow
        // for the normal eight-tick crumble; every intermediate tile must still be safe.
        for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            for (int distance = 1; distance <= 3; distance++) {
                var point = new Location(here.getWorld(), here.getBlockX() + direction[0] * distance + 0.5,
                        here.getBlockY(), here.getBlockZ() + direction[1] * distance + 0.5);
                if (!safeTile(point)) break;
                double score = distance * 4 - memory.penalty(bot, point);
                if (score > bestScore) { bestScore = score; best = point; }
            }
        }
        if (best == null) destinations.remove(bot.getUniqueId());
        else destinations.put(bot.getUniqueId(), best.clone());
        return best;
    }

    private boolean safeTile(Location point) {
        var support = new Location(point.getWorld(), point.getBlockX(), point.getBlockY() - 1, point.getBlockZ());
        return !crumbling.contains(support) && uk.co.playerready.pulsegames.core.bot.GroundNavigation.walkable(point);
    }

    private boolean clearCorridor(Location from, Location to) {
        int dx = to.getBlockX() - from.getBlockX(), dz = to.getBlockZ() - from.getBlockZ();
        if (dx != 0 && dz != 0 || Math.abs(dx) + Math.abs(dz) > 3) return false;
        for (int step = 1; step <= Math.abs(dx) + Math.abs(dz); step++) {
            if (!safeTile(new Location(from.getWorld(), from.getBlockX() + Integer.signum(dx) * step + 0.5,
                    from.getBlockY(), from.getBlockZ() + Integer.signum(dz) * step + 0.5))) return false;
        }
        return true;
    }

    @Override public boolean straightNavigation() { return true; }
    @Override public int navigationIntervalTicks(uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) { return 5; }
    @Override public void botTick(Player bot, long tick, uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty) {
        bot.setSprinting(true);
    }
    @Override public void onCleanup() { memory.clear(); crumbling.clear(); destinations.clear(); }

    @Override
    public void onDeath(Player victim, Player killer) {
        game.broadcast("<red>" + victim.getName() + "</red> fell!");
        game.eliminate(victim);
    }

    @Override
    public int timeLimitSeconds() { return 300; }
}
