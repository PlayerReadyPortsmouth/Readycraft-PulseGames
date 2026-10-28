package uk.co.playerready.pulsegames.games.parkour;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.games.common.CheckpointRaceGame;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Parkour Race: first through the course wins. Falling sends you back to your checkpoint.
 * Modes: sprint, elimination (last place becomes a ghost runner after a 10 s warning).
 * Finishers get places and times inside a grace window; see {@link CheckpointRaceGame#racePack()}.
 */
public final class ParkourGame extends CheckpointRaceGame {

    public ParkourGame(GameInstance game) {
        super(game);
    }

    /** Seconds of warning before each elimination cut. */
    static final int WARNING_SECONDS = 10;

    private final Set<UUID> ghosts = new HashSet<>();
    private final Set<UUID> ghostFinished = new HashSet<>();

    @Override protected boolean racePack() { return true; }

    @Override protected boolean countsForRace(Player player) { return !ghosts.contains(player.getUniqueId()); }

    boolean isGhost(Player player) { return ghosts.contains(player.getUniqueId()); }

    /** Seconds left before the next cut while inside the warning window, else 0. */
    static int warningSecondsLeft(int gameTime, int interval) {
        if (gameTime <= 0 || interval <= 1) return 0;
        int toCut = interval - (gameTime % interval);
        return toCut == interval || toCut > Math.min(WARNING_SECONDS, interval - 1) ? 0 : toCut;
    }

    private boolean isElimination() { return game.mode().id().equals("elimination"); }

    private int elimInterval() { return Math.max(1, game.arena().settings().getInt("elim-interval", 45)); }

    @Override
    protected int laps() { return 1; }

    @Override
    public void onStart() {
        super.onStart();
        game.broadcast(isElimination()
                ? "Race! Every " + elimInterval() + "s the player in last place becomes a ghost runner (you get a " + WARNING_SECONDS + "s warning)."
                : "Race to the finish - falling sends you back to your last checkpoint!");
    }

    @Override
    public void onSecond(int gameTime) {
        super.onSecond(gameTime);
        if (!isElimination() || finishes == null || raceClosed() || finishes.hasFinishers() || gameTime == 0) return;
        List<Player> runners = standings();
        if (runners.size() <= 1) return;
        int warn = warningSecondsLeft(gameTime, elimInterval());
        Player last = runners.get(runners.size() - 1);
        boolean tied = samePosition(last, runners.get(runners.size() - 2));
        if (warn > 0) {
            for (Player runner : runners) {
                if (runner == last && !tied) {
                    runner.sendActionBar(Text.mm("<red><b>You are in last place!</b> <gray>Ghost in " + warn + "s"));
                } else runner.sendActionBar(Text.mm("<yellow>Cut in " + warn + "s <gray>- last place becomes a ghost"));
                if (warn <= 3) runner.playSound(runner.getLocation(), "minecraft:block.note_block.hat", 0.8f, 1.5f);
                if (warn == Math.min(WARNING_SECONDS, elimInterval() - 1))
                    Text.title(runner, "<yellow>Cut in " + warn + "s", "<gray>Last place becomes a ghost runner");
            }
        } else if (gameTime % elimInterval() == 0) {
            if (tied) {
                game.broadcast("<yellow>Last place is tied - nobody becomes a ghost this time.");
                return;
            }
            ghostify(last);
            game.broadcast("<red>" + last.getName() + "</red> was in last place and is now a ghost runner!");
            List<Player> left = standings();
            if (left.size() == 1) closeRace("last-runner", List.of(left.get(0)));
        }
    }

    /** Out of the contest, still flying the course and collecting points per checkpoint. */
    private void ghostify(Player player) {
        ghosts.add(player.getUniqueId());
        finishes.depart(player.getUniqueId());
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        Text.title(player, "<gray><b>Ghost runner", "<gray>Fly the course - each checkpoint scores a point");
    }

    @Override
    protected void onGatePassed(Player player, Progress p, Cuboid gate) {
        if (!isGhost(player)) return;
        game.addScore(player, 1);
        player.sendActionBar(Text.mm("<gray>Ghost runner <white>+1 <gray>(" + game.score(player) + " pts)"));
    }

    @Override
    protected void onFinish(Player player) {
        if (isGhost(player)) {
            if (ghostFinished.add(player.getUniqueId())) {
                game.addScore(player, 1);
                player.sendActionBar(Text.mm("<gray>Ghost runner reached the finish <white>+1"));
            }
            return;
        }
        super.onFinish(player);
    }

    @Override
    public void onTimeUp() {
        if (finishes != null && finishes.hasFinishers()) {
            super.onTimeUp();
            return;
        }
        List<Player> standings = standings();
        boolean tied = standings.size() > 1 && samePosition(standings.get(0), standings.get(1));
        game.end(standings.isEmpty() || tied ? List.of() : List.of(standings.get(0)), tied ? "time-draw" : "time");
    }

    /** Ordered checkpoints are authoritative; proximity only breaks ties within one segment.
     * This is distance to the next gate, not claimed route progress on winding courses. */
    private double distanceToNext(Player player) {
        Progress p = progress.get(player.getUniqueId());
        var gate = p != null && p.nextCheckpoint < checkpoints.size() ? checkpoints.get(p.nextCheckpoint) : finish;
        var at = player.getLocation();
        if (gate == null || at == null || at.getWorld() != game.world()
                || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ()))
            return Double.POSITIVE_INFINITY;
        double dx = Math.max(0, Math.max(gate.minX() - at.getX(), at.getX() - (gate.maxX() + 1.0)));
        double dy = Math.max(0, Math.max(gate.minY() - at.getY(), at.getY() - (gate.maxY() + 1.0)));
        double dz = Math.max(0, Math.max(gate.minZ() - at.getZ(), at.getZ() - (gate.maxZ() + 1.0)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean samePosition(Player a, Player b) {
        if (progressValue(a.getUniqueId()) != progressValue(b.getUniqueId())) return false;
        double da = distanceToNext(a), db = distanceToNext(b);
        return Double.compare(da, db) == 0 || Math.abs(da - db) <= 0.25;
    }

    @Override protected List<Player> standings() {
        var ordered = new java.util.ArrayList<>(game.alivePlayers().stream()
                .filter(p -> !ghosts.contains(p.getUniqueId())).toList());
        ordered.sort(java.util.Comparator.comparingInt((Player p) -> progressValue(p.getUniqueId())).reversed()
                .thenComparingDouble(this::distanceToNext));
        return ordered;
    }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 480);
    }
}
