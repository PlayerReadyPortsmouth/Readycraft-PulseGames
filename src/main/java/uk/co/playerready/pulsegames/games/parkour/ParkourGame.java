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

    private int elimInterval() { return game.arena().settings().getInt("elim-interval", 45); }

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
            game.broadcast("<red>" + last.getName() + "</red> was in last place and is eliminated!");
            game.eliminate(last);
        }
    }

    @Override
    public void onTimeUp() {
        List<Player> standings = standings();
        game.end(standings.isEmpty() ? List.of() : List.of(standings.get(0)), "time");
    }

    @Override
    public int timeLimitSeconds() {
        return game.arena().settings().getInt("time-limit", 480);
    }
}
