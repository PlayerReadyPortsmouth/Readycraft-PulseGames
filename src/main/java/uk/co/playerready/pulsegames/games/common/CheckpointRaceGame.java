package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Location;
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

    /** Fast vehicle races may opt into ordered swept gate detection. */
    protected boolean sweptCheckpoints() { return false; }

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
        if (sweptCheckpoints()) {
            moveSwept(player, p, from, to);
            return;
        }
        if (p.nextCheckpoint < checkpoints.size()) {
            if (checkpoints.get(p.nextCheckpoint).contains(to)) {
                p.nextCheckpoint++;
                recordCheckpoint(player, p, to);
                player.sendActionBar(Text.mm("<green>Checkpoint " + p.nextCheckpoint + "/" + checkpoints.size()
                        + (laps() > 1 ? " <gray>(lap " + p.lap + "/" + laps() + ")" : "")));
                player.playSound(player.getLocation(), "minecraft:block.note_block.bell", 1f, 1.5f);
            }
        } else if (finish != null && finish.contains(to)) {
            if (p.lap < laps()) {
                p.lap++;
                p.nextCheckpoint = 0;
                recordSafe(player, p, to);
                game.broadcast("<yellow>" + player.getName() + "</yellow> started lap <yellow>" + p.lap + "/" + laps());
            } else {
                onFinish(player);
            }
        }
    }

    private void moveSwept(Player player, Progress p, Location from, Location to) {
        if (from.getWorld() != game.world() || to.getWorld() != game.world()) return;
        double previous = 0;
        while (p.nextCheckpoint < checkpoints.size()) {
            double entry = RaceGateCrossing.entry(checkpoints.get(p.nextCheckpoint), from, to);
            if (Double.isNaN(entry) || entry < previous) return;
            previous = entry;
            p.nextCheckpoint++;
            recordCheckpoint(player, p, to);
            player.sendActionBar(Text.mm("<green>Checkpoint " + p.nextCheckpoint + "/" + checkpoints.size()
                    + (laps() > 1 ? " <gray>(lap " + p.lap + "/" + laps() + ")" : "")));
            player.playSound(player.getLocation(), "minecraft:block.note_block.bell", 1f, 1.5f);
        }
        if (finish == null) return;
        double entry = RaceGateCrossing.entry(finish, from, to);
        if (Double.isNaN(entry) || entry < previous) return;
        if (p.lap < laps()) {
            p.lap++;
            p.nextCheckpoint = 0;
            // A reviewed grid spawn is safer than a moving kart's finish-line position.
            p.lastSafe = game.spawnFor(player).clone();
            game.broadcast("<yellow>" + player.getName() + "</yellow> started lap <yellow>" + p.lap + "/" + laps());
        } else onFinish(player);
    }

    /**
     * Records a respawn point only where the player was actually supported. A position
     * captured while free-falling past a checkpoint sends them straight back into the
     * same fall, looping until the timer expires. Kart passengers are never "on ground", so their supported state counts too.
     * Gliding crossings require an authored launch point rather than an arbitrary air position.
     */
    private void recordCheckpoint(Player player, Progress p, Location at) {
        Location reviewed = game.arena().settingLocation("checkpoint-spawn" + p.nextCheckpoint, game.world());
        if (reviewed != null && validAuthoredReset(reviewed)) p.lastSafe = reviewed.clone();
        else recordSafe(player, p, at);
    }

    protected boolean validAuthoredReset(Location location) {
        return location.getWorld() == game.world() && Double.isFinite(location.getX())
                && Double.isFinite(location.getY()) && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw()) && Float.isFinite(location.getPitch())
                && uk.co.playerready.pulsegames.games.readyplayerz.ZSpawnSearch.safe(location, 1.8);
    }

    private void recordSafe(Player player, Progress p, Location at) {
        if (player.isOnGround() || player.isInsideVehicle()) {
            p.lastSafe = at.clone();
        }
    }

    /** Per-round progress must not outlive the player who left. */
    @Override
    public void onQuit(Player player) {
        progress.remove(player.getUniqueId());
    }

    /** A player completed all laps. Default: they win. */
    protected void onFinish(Player player) {
        game.end(List.of(player), "finish");
    }

    @Override
    public void onTimeUp() {
        game.broadcast("<yellow>Time is up. Nobody completed the course.");
        game.end(List.of(), "time-unfinished");
    }

    /** Send a player back to their last checkpoint. */
    protected void sendBack(Player player) {
        Location target = resetLocation(player);
        player.setVelocity(new org.bukkit.util.Vector());
        player.setFallDistance(0);
        if (!player.teleport(target)) return;
        player.setVelocity(new org.bukkit.util.Vector());
        afterReset(player, target);
        player.sendActionBar(Text.mm("<red>Back to your last checkpoint!"));
    }

    /** Mode-specific relaunch runs only after a successful reset. */
    protected void afterReset(Player player, Location target) {}

    /** A defensive copy lets vehicle controllers reset without teleporting a passenger. */
    protected Location resetLocation(Player player) {
        Progress p = progress.get(player.getUniqueId());
        return ((p != null && p.lastSafe != null) ? p.lastSafe : game.spawnFor(player)).clone();
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
