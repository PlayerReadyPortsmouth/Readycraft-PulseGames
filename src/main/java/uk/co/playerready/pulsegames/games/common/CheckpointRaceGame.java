package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared base for checkpoint races (Parkour, Elytra Racing, PulseKarts).
 * Requires regions checkpoint1..N and finish; players must pass them in order.
 */
public abstract class CheckpointRaceGame extends MiniGame {

    protected static final class Progress {
        public int lap = 1;
        public int nextCheckpoint = 0; // index into checkpoints; == size means head to finish
        public Location lastSafe;
    }

    protected List<Cuboid> checkpoints = List.of();
    protected Cuboid finish;
    protected final Map<UUID, Progress> progress = new HashMap<>();

    protected CheckpointRaceGame(GameInstance game) {
        super(game);
    }

    /** Number of laps for this mode. */
    protected abstract int laps();

    @Override
    public void onStart() {
        checkpoints = game.arena().regionsByPrefix("checkpoint");
        finish = game.arena().region("finish");
        for (Player player : game.alivePlayers()) {
            Progress p = new Progress();
            p.lastSafe = player.getLocation().clone();
            progress.put(player.getUniqueId(), p);
        }
    }

    @Override
    public void onMove(Player player, Location from, Location to) {
        Progress p = progress.get(player.getUniqueId());
        if (p == null) return;
        if (p.nextCheckpoint < checkpoints.size()) {
            if (checkpoints.get(p.nextCheckpoint).contains(to)) {
                p.nextCheckpoint++;
                p.lastSafe = to.clone();
                player.sendActionBar(Text.mm("<green>Checkpoint " + p.nextCheckpoint + "/" + checkpoints.size()
                        + (laps() > 1 ? " <gray>(lap " + p.lap + "/" + laps() + ")" : "")));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
            }
        } else if (finish != null && finish.contains(to)) {
            if (p.lap < laps()) {
                p.lap++;
                p.nextCheckpoint = 0;
                p.lastSafe = to.clone();
                game.broadcast("<yellow>" + player.getName() + "</yellow> started lap <yellow>" + p.lap + "/" + laps());
            } else {
                onFinish(player);
            }
        }
    }

    /** A player completed all laps. Default: they win. */
    protected void onFinish(Player player) {
        game.end(List.of(player), "finish");
    }

    /** Send a player back to their last checkpoint. */
    protected void sendBack(Player player) {
        Progress p = progress.get(player.getUniqueId());
        Location target = (p != null && p.lastSafe != null) ? p.lastSafe : game.spawnFor(player);
        player.setFallDistance(0);
        player.teleport(target);
        player.sendActionBar(Text.mm("<red>Back to your last checkpoint!"));
    }

    /** Falling into the void sends you back instead of killing you. */
    @Override
    public void onDeath(Player victim, Player killer) {
        sendBack(victim);
    }

    protected int progressValue(UUID uuid) {
        Progress p = progress.get(uuid);
        return p == null ? 0 : (p.lap - 1) * (checkpoints.size() + 1) + p.nextCheckpoint;
    }

    /** Players sorted by race position (best first). */
    protected List<Player> standings() {
        List<Player> alive = new ArrayList<>(game.alivePlayers());
        alive.sort(Comparator.comparingInt((Player pl) -> progressValue(pl.getUniqueId())).reversed());
        return alive;
    }

    @Override
    public List<String> sidebar(Player player) {
        List<String> lines = new ArrayList<>();
        Progress p = progress.get(player.getUniqueId());
        if (p != null) {
            lines.add("<gray>Checkpoint: <white>" + p.nextCheckpoint + "/" + checkpoints.size());
            if (laps() > 1) lines.add("<gray>Lap: <white>" + p.lap + "/" + laps());
        }
        List<Player> standings = standings();
        for (int i = 0; i < Math.min(3, standings.size()); i++) {
            lines.add("<gray>#" + (i + 1) + " <white>" + standings.get(i).getName());
        }
        return lines;
    }
}
