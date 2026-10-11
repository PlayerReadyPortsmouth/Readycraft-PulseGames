package uk.co.playerready.pulsegames.games.dropper;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.*;

/** Sequential water landings. Completing a stage saves its next launch as the retry point. */
public final class DropperGame extends MiniGame {
    private List<Cuboid> checkpoints = List.of();
    private Cuboid finish;
    private final Map<UUID, Integer> progress = new HashMap<>();
    private final Map<UUID, Location> retries = new HashMap<>();
    public DropperGame(GameInstance game) { super(game); }
    @Override public boolean cooperative() { return true; } // Leaving never awards an unfinished race.
    @Override public boolean fallDamage() { return true; }
    @Override public int timeLimitSeconds() { return 600; }
    @Override public String startProblem(List<Player> participants) {
        if (game.arena().region("finish") == null) return "Dropper requires a finish landing";
        int count = game.arena().regionsByPrefix("checkpoint").size();
        for (int i = 1; i <= count; i++) {
            try {
                Location launch = game.arena().settingLocation("checkpoint-spawn" + i, game.world());
                if (launch == null) return "Missing checkpoint-spawn" + i;
                launch.checkFinite();
            } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
                return "Invalid checkpoint-spawn" + i;
            }
        }
        return null;
    }
    @Override public void onStart() {
        checkpoints = game.arena().regionsByPrefix("checkpoint");
        finish = game.arena().region("finish");
        for (Player player : game.alivePlayers()) {
            progress.put(player.getUniqueId(), 0);
            retries.put(player.getUniqueId(), game.spawnFor(player));
        }
        game.broadcast("Land in each water pool! Misses return you to your latest launch.");
    }
    @Override public void onMove(Player player, Location from, Location to) {
        if (!game.isActive(player) || to.getWorld() != game.world() || !to.getBlock().isLiquid()
                || to.getBlock().getType() != org.bukkit.Material.WATER) return;
        int stage = progress.getOrDefault(player.getUniqueId(), 0);
        if (stage < checkpoints.size()) {
            if (!checkpoints.get(stage).contains(to)) return;
            Location next = game.arena().settingLocation("checkpoint-spawn" + (stage + 1), game.world());
            progress.put(player.getUniqueId(), stage + 1);
            retries.put(player.getUniqueId(), next);
            retry(player);
        } else if (finish != null && finish.contains(to)) {
            game.end(List.of(player), "finished");
        }
    }
    @Override public void onDamage(Player player, EntityDamageEvent event) {
        event.setCancelled(true);
        retry(player);
    }
    @Override public void onDeath(Player victim, Player killer) { retry(victim); }
    private void retry(Player player) {
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector());
        player.teleport(retries.getOrDefault(player.getUniqueId(), game.spawnFor(player)).clone());
    }
    @Override public void onTimeUp() { game.end(List.of(), "time-unfinished"); }
    @Override public List<String> sidebar(Player player) {
        return List.of("<aqua>Stage: <white>" + (progress.getOrDefault(player.getUniqueId(), 0) + 1)
                + "/" + (checkpoints.size() + 1), "<gray>Land in water to advance");
    }
}
