package uk.co.playerready.pulsegames.games.parkour;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.games.common.CheckpointRaceGame;

import java.util.List;

/**
 * Parkour Race: first through the course wins. Falling sends you back to your checkpoint.
 * Modes: sprint, elimination (last place is knocked out periodically).
 */
public final class ParkourGame extends CheckpointRaceGame {

    public ParkourGame(GameInstance game) {
        super(game);
    }

    private boolean isElimination() { return game.mode().id().equals("elimination"); }

    private int elimInterval() { return Math.max(1, game.arena().settings().getInt("elim-interval", 45)); }

    @Override
    protected int laps() { return 1; }

    @Override
    public void onStart() {
        super.onStart();
        game.broadcast(isElimination()
                ? "Race! Every " + elimInterval() + "s the player in last place is eliminated!"
                : "Race to the finish - falling sends you back to your last checkpoint!");
    }

    @Override
    public void onSecond(int gameTime) {
        if (!isElimination() || gameTime == 0 || gameTime % elimInterval() != 0) return;
        List<Player> standings = standings();
        if (standings.size() > 1) {
            Player last = standings.get(standings.size() - 1);
            if (samePosition(last, standings.get(standings.size() - 2))) {
                game.broadcast("<yellow>Last place is tied — nobody is eliminated this time.");
                return;
            }
            game.broadcast("<red>" + last.getName() + "</red> was in last place and is eliminated!");
            game.eliminate(last);
        }
    }

    @Override
    public void onTimeUp() {
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
        var ordered = new java.util.ArrayList<>(game.alivePlayers());
        ordered.sort(java.util.Comparator.comparingInt((Player p) -> progressValue(p.getUniqueId())).reversed()
                .thenComparingDouble(this::distanceToNext));
        return ordered;
    }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 480);
    }
}
