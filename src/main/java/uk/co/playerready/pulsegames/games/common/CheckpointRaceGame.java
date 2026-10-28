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

    // ---- race pack (opt-in via racePack(); PulseKarts keeps its own results) ----
    protected RaceFinish finishes;
    private long startMs = -1;
    private boolean raceClosed;
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Long> lastGap = new HashMap<>();
    private final Map<UUID, Long> lastGateAt = new HashMap<>();

    protected CheckpointRaceGame(GameInstance game) {
        super(game);
    }

    /** Number of laps for this mode. */
    protected abstract int laps();

    /** Fast vehicle races may opt into ordered swept gate detection. */
    protected boolean sweptCheckpoints() { return false; }

    /** Opt in to finish places with a grace window, splits, HUD place and personal bests. */
    protected boolean racePack() { return false; }

    /** False for ghost runners and anyone else whose passes must not set splits or places. */
    protected boolean countsForRace(Player player) { return true; }

    protected boolean raceClosed() { return raceClosed; }

    /** Race clock source; overridable so timing can be tested. */
    protected long nowMs() { return System.currentTimeMillis(); }

    /** Extra text appended to the "You are 3rd" action bar (Elytra adds speed). */
    protected String hudExtra(Player player) { return ""; }

    /** Hook after a checkpoint/ring is passed (race-pack effects). */
    protected void onGatePassed(Player player, Progress p, Cuboid gate) {}

    @Override
    public boolean managesCompletion() { return racePack(); }

    @Override
    public void onStart() {
        checkpoints = game.arena().regionsByPrefix("checkpoint");
        finish = game.arena().region("finish");
        for (Player player : game.alivePlayers()) {
            Progress p = new Progress();
            p.lastSafe = player.getLocation().clone();
            progress.put(player.getUniqueId(), p);
            names.put(player.getUniqueId(), player.getName());
        }
        if (racePack()) {
            finishes = new RaceFinish(game.alivePlayers().stream().map(Player::getUniqueId).toList(),
                    game.arena().settings().getInt("finish-grace", RaceFinish.DEFAULT_GRACE_SECONDS));
            startMs = nowMs() + startFreezeSeconds() * 1000L;
        }
    }

    protected long elapsedMs() { return startMs < 0 ? 0 : Math.max(0, nowMs() - startMs); }

    private void announceGate(Player player, Progress p) {
        String line = "<green>Checkpoint " + p.nextCheckpoint + "/" + checkpoints.size()
                + (laps() > 1 ? " <gray>(lap " + p.lap + "/" + laps() + ")" : "");
        if (racePack() && finishes != null && countsForRace(player)) {
            UUID id = player.getUniqueId();
            long gap = finishes.split(progressValue(id), id, elapsedMs());
            lastGap.put(id, gap);
            lastGateAt.put(id, nowMs());
            line += " <gray>| " + placeLine(id) + " <gray>| " + (gap == 0 ? "<gold>leader" : "<red>" + RaceFinish.gap(gap));
        }
        player.sendActionBar(Text.mm(line));
        player.playSound(player.getLocation(), "minecraft:block.note_block.bell", 1f, 1.5f);
        if (racePack() && finishes != null && p.nextCheckpoint >= 1 && p.nextCheckpoint <= checkpoints.size())
            onGatePassed(player, p, checkpoints.get(p.nextCheckpoint - 1));
    }

    /** Everyone in race order: finishers by time, then the rest by checkpoint progress. */
    protected List<UUID> placings() {
        return finishes.fullOrder(standings().stream().map(Player::getUniqueId).toList());
    }

    protected String placeLine(UUID id) {
        List<UUID> order = placings();
        int idx = order.indexOf(id);
        return idx < 0 ? "<gray>Ghost runner" : "<yellow>You are " + RaceFinish.ordinal(idx + 1) + "<gray>/" + order.size();
    }

    /** Action bar with place, gap to the leader at the last gate, and any game extra. */
    protected void sendPlaceBar(Player player) {
        if (finishes == null || raceClosed) return;
        UUID id = player.getUniqueId();
        long gap = lastGap.getOrDefault(id, 0L);
        String extra = hudExtra(player);
        player.sendActionBar(Text.mm(placeLine(id) + " <gray>| " + (gap == 0 ? "<gold>leader" : "<red>" + RaceFinish.gap(gap))
                + (extra.isEmpty() ? "" : " <gray>| " + extra)));
    }

    @Override
    public void onSecond(int gameTime) {
        if (finishes == null || raceClosed) return;
        long now = elapsedMs();
        if (finishes.shouldEnd(now) || game.alivePlayers().isEmpty()) {
            closeRace("race-complete");
            return;
        }
        boolean closing = finishes.hasFinishers();
        for (Player player : game.alivePlayers()) {
            if (nowMs() - lastGateAt.getOrDefault(player.getUniqueId(), 0L) < 2500) continue;
            if (closing && finishes.graceRemainingSeconds(now) <= 10) {
                player.sendActionBar(Text.mm("<red>Race closes in " + finishes.graceRemainingSeconds(now) + "s <gray>| "
                        + placeLine(player.getUniqueId())));
            } else sendPlaceBar(player);
        }
    }

    private void recordFinish(Player player) {
        UUID id = player.getUniqueId();
        long ms = elapsedMs();
        if (raceClosed || !finishes.finish(id, ms)) return;
        int place = finishes.placeOf(id);
        String time = RaceFinish.time(ms);
        RaceAwards.announceFinish(game, player, place, ms);
        game.broadcast("<gold>#" + place + " <yellow>" + player.getName() + " <gray>finished in <white>" + time);
        game.recordFinish(player); // engine placements, podium and 2nd/3rd bonuses follow the race's finish order
        game.finishParticipant(player);
        player.sendMessage(Text.mm("<green>Course complete! <gray>You can watch the remaining racers."));
        onSecond(game.gameTime());
    }

    protected void closeRace(String reason) { closeRace(reason, null); }

    /** Ends the race; {@code winnersOverride} (e.g. last runner standing) replaces the first-place finisher. */
    protected void closeRace(String reason, List<Player> winnersOverride) {
        if (raceClosed) return;
        raceClosed = true;
        List<UUID> order = placings();
        for (int i = 0; i < Math.min(order.size(), 5); i++) {
            UUID id = order.get(i);
            long t = finishes.timeOf(id);
            game.broadcast("<gray>" + (i + 1) + ". <white>" + names.getOrDefault(id, "Racer")
                    + " <gray>" + (t >= 0 ? RaceFinish.time(t) : "did not finish"));
        }
        List<Player> winners = finishes.results().stream().filter(r -> r.place() == 1)
                .map(r -> game.player(r.player())).filter(java.util.Objects::nonNull).toList();
        // Finishers are already recorded; rank the rest in the order listed above so the engine agrees.
        if (winnersOverride != null) winnersOverride.forEach(game::recordFinish);
        order.stream().map(game::player).filter(java.util.Objects::nonNull).forEach(game::recordFinish);
        game.end(winnersOverride != null ? winnersOverride : winners, reason);
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
                announceGate(player, p);
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
            announceGate(player, p);
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
        if (finishes != null && !raceClosed) {
            finishes.depart(player.getUniqueId());
            onSecond(game.gameTime());
        }
    }

    /** A player completed all laps. Default: they win. */
    protected void onFinish(Player player) {
        if (racePack() && finishes != null) {
            recordFinish(player);
            return;
        }
        game.end(List.of(player), "finish");
    }

    @Override
    public void onTimeUp() {
        if (finishes != null && finishes.hasFinishers()) {
            closeRace("time-race");
            return;
        }
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
        if (racePack() && finishes != null) {
            List<UUID> order = placings();
            UUID me = player.getUniqueId();
            int shown = 0;
            for (int i = 0; i < order.size(); i++) {
                boolean mine = order.get(i).equals(me);
                if (i >= 8 && !mine) continue;
                if (i >= 8 && shown >= 9) continue;
                long t = finishes.timeOf(order.get(i));
                lines.add((mine ? "<yellow>#" : "<gray>#") + (i + 1) + " <white>" + shorten(names.getOrDefault(order.get(i), "Racer"))
                        + (t >= 0 ? " <gray>" + RaceFinish.time(t) : ""));
                shown++;
            }
            return lines;
        }
        List<Player> standings = standings();
        for (int i = 0; i < Math.min(3, standings.size()); i++) {
            lines.add("<gray>#" + (i + 1) + " <white>" + standings.get(i).getName());
        }
        return lines;
    }

    private static String shorten(String name) { return name.length() > 12 ? name.substring(0, 12) : name; }
}
