package uk.co.playerready.pulsegames.core.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;

/** Per-player sidebar rendering (one private scoreboard per player). */
public final class SidebarService {

    public void show(Player player, String title, List<String> miniMessageLines) {
        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective("pulse");
        if (obj == null) {
            obj = board.registerNewObjective("pulse", Criteria.DUMMY, Text.mm(title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(Text.mm(title));
        }
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        int score = miniMessageLines.size();
        for (String line : miniMessageLines) {
            String rendered = Text.legacy(Text.mm(line));
            // Ensure uniqueness for duplicate (e.g. blank) lines. The pad budget is
            // per line: shared across lines it runs out and later lines collide.
            int pad = 0;
            while (board.getEntries().contains(rendered)) {
                rendered += "§r";
                if (++pad > 32) break;
            }
            obj.getScore(rendered).setScore(score--);
        }
    }

    public void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
