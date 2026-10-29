package uk.co.playerready.pulsegames.core.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import uk.co.playerready.pulsegames.core.util.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-player sidebar rendering (one private scoreboard per player). The last drawn lines are cached per
 * player so each refresh only touches entries that changed. {@code debug.sidebar} logs the saving.
 */
public final class SidebarService {

    private record Drawn(Scoreboard board, String title, List<String> entries) { }

    private final Map<UUID, Drawn> drawn = new ConcurrentHashMap<>();
    // Debug counters: what the old reset-and-rewrite cost, against what is actually written now.
    private final AtomicLong calls = new AtomicLong(), baselineOps = new AtomicLong(), actualOps = new AtomicLong();

    public void show(Player player, String title, List<String> miniMessageLines) {
        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective("pulse");
        Drawn last = drawn.get(player.getUniqueId());
        // Someone else replaced the board or the objective: forget the cache and draw everything.
        if (obj == null || last == null || last.board() != board) last = null;
        if (obj == null) {
            obj = board.registerNewObjective("pulse", Criteria.DUMMY, Text.mm(title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (last == null || !title.equals(last.title())) {
            obj.displayName(Text.mm(title));
        }
        List<String> entries = SidebarDiff.uniqueEntries(miniMessageLines.stream().map(l -> Text.legacy(Text.mm(l))).toList());
        SidebarDiff.Changes changes;
        if (last == null) {
            // Unknown state: clear whatever is on the board and write every line, as before.
            int existing = board.getEntries().size();
            for (String entry : board.getEntries()) board.resetScores(entry);
            changes = SidebarDiff.diff(null, entries);
            baselineOps.addAndGet(existing + entries.size());
            actualOps.addAndGet(existing + entries.size());
        } else {
            changes = SidebarDiff.diff(last.entries(), entries);
            baselineOps.addAndGet(last.entries().size() + entries.size());
            actualOps.addAndGet(changes.total());
            for (String entry : changes.remove()) board.resetScores(entry);
        }
        for (Map.Entry<String, Integer> e : changes.set().entrySet()) obj.getScore(e.getKey()).setScore(e.getValue());
        calls.incrementAndGet();
        drawn.put(player.getUniqueId(), new Drawn(board, title, entries));
    }

    public void clear(Player player) {
        drawn.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** One-line summary since the last call (and resets), for the {@code debug.sidebar} timer; null when idle. */
    public String drainStats() {
        long c = calls.getAndSet(0), before = baselineOps.getAndSet(0), after = actualOps.getAndSet(0);
        if (c == 0) return null;
        return "sidebar: " + c + " refreshes, line operations " + after + " (old method: " + before + ")";
    }
}
