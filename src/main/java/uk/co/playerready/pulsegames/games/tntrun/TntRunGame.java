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
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.HazardFeedback;
import uk.co.playerready.pulsegames.games.common.RegionSnapshot;
import uk.co.playerready.pulsegames.games.common.RoundController;

import java.util.HashSet;
import java.util.Set;

/**
 * TNT Run: the floor crumbles behind you, keep moving!
 * Modes: classic, doublejump.
 */
public final class TntRunGame extends MiniGame implements uk.co.playerready.pulsegames.core.bot.BotMovement {

    private static final int BASE_CRUMBLE_TICKS = 8;
    private final uk.co.playerready.pulsegames.core.bot.SurfaceMemory memory = new uk.co.playerready.pulsegames.core.bot.SurfaceMemory();
    private final java.util.Map<java.util.UUID, Location> destinations = new java.util.HashMap<>();
    private final Set<Location> crumbling = new HashSet<>();
    private RoundController rounds;
    private RegionSnapshot journal;
    private boolean lastFiveShown;
    private int started;

    /** Crumble delay in ticks: 8 at first, one tick quicker every {@code accelerationSeconds}, never below {@code min}. */
    static int crumbleDelay(int elapsedSeconds, int accelerationSeconds, int min) {
        if (accelerationSeconds <= 0) return BASE_CRUMBLE_TICKS;
        return Math.max(Math.min(min, BASE_CRUMBLE_TICKS), BASE_CRUMBLE_TICKS - Math.max(0, elapsedSeconds) / accelerationSeconds);
    }

    public TntRunGame(GameInstance game) {
        super(game);
    }

    private boolean isDoubleJump() { return game.mode().id().equals("doublejump"); }
    private boolean isRounds() { return RoundController.isRounds(game); }

    private int elapsed() { return rounds != null ? rounds.roundElapsed() : game.gameTime(); }

    private int crumbleTicks() {
        var settings = game.arena().settings();
        return crumbleDelay(elapsed(), settings.getInt("crumble-acceleration-seconds", 60),
                Math.max(1, settings.getInt("min-crumble-ticks", 4)));
    }

    @Override
    public void onStart() {
        started = game.players().size();
        if (isRounds()) {
            journal = RegionSnapshot.empty();
            rounds = new RoundController(game, new RoundController.Host() {
                @Override public void beginRound(int round) { crumbling.clear(); lastFiveShown = false; }
            }, journal, 75, "The floor falls away - last one standing scores most");
            rounds.start();
            return;
        }
        game.broadcast("The floor falls away behind you - keep moving!");
        if (isDoubleJump()) {
            game.broadcast("<yellow>Double-jump enabled! Tap jump twice in the air.");
            game.alivePlayers().forEach(p -> { p.setAllowFlight(true); p.setExp(1f); });
        }
    }

    private int aliveCount() { return rounds != null ? rounds.contestants().size() : game.alivePlayers().size(); }

    @Override
    public void onSecond(int gameTime) {
        if (rounds != null) { rounds.tick(); if (!rounds.active()) return; }
        int left = aliveCount();
        if (started > 5 && left <= 5 && left > 1) {
            if (!lastFiveShown) {
                lastFiveShown = true;
                for (Player p : game.players()) {
                    Text.title(p, "<gold><b>LAST FIVE", "<gray>Keep moving!");
                    p.playSound(p.getLocation(), "minecraft:block.note_block.bell", 1f, 1.2f);
                }
            }
            for (Player p : game.players()) p.sendActionBar(Text.mm("<gold>Last five: <white>" + left + " left"));
        } else if (isDoubleJump()) {
            for (Player p : game.alivePlayers()) {
                if (p.getAllowFlight()) p.sendActionBar(Text.mm("<green>Double jump: <white>READY"));
            }
        }
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        if (game.frozen()) return;
        if (rounds != null && !rounds.isContesting(player)) return;
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
        game.runLater(crumbleTicks(), () -> {
            if (rounds != null && !rounds.active()) { crumbling.remove(key); return; }
            if (journal != null) { journal.remember(target); journal.remember(target.getRelative(0, -1, 0)); }
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
        player.setExp(0f);
        player.sendActionBar(Text.mm("<red>Double jump: recharging"));
        game.runLater(20L, () -> player.setExp(0.33f));
        game.runLater(40L, () -> player.setExp(0.66f));
        game.runLater(60L, () -> {
            if (game.isAlive(player)) {
                player.setAllowFlight(true);
                player.setExp(1f);
                player.sendActionBar(Text.mm("<green>Double jump: <white>READY"));
                player.playSound(player.getLocation(), "minecraft:block.note_block.chime", 0.7f, 1.6f);
            }
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
    @Override public void onCleanup() { memory.clear(); crumbling.clear(); destinations.clear(); if (rounds != null) rounds.close(); }
    @Override public void onEnd(java.util.List<Player> winners) { if (rounds != null) rounds.close(); }
    @Override public void onQuit(Player player) { if (rounds != null) rounds.playerLeft(player); }
    @Override public void onTimeUp() { if (rounds != null) rounds.onTimeUp(); else super.onTimeUp(); }
    @Override public java.util.List<String> sidebar(Player player) { return rounds != null ? rounds.sidebar(player) : java.util.List.of(); }
    @Override public MiniGame.Cue eliminationSound() { return HazardFeedback.LOUD_ELIMINATION; }
    @Override public void onEliminated(Player player) {
        HazardFeedback.finalThree(game.players(), game.alivePlayers().size(), game.players().size());
    }

    @Override
    public void onDeath(Player victim, Player killer) {
        game.broadcast("<red>" + victim.getName() + "</red> fell!");
        if (rounds != null) rounds.knockOut(victim); else game.eliminate(victim);
    }

    @Override
    public int timeLimitSeconds() { return isRounds() ? RoundController.matchSeconds(game, 75) : 300; }
}
