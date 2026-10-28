package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Weekly win board kept inside a section of stats.yml. One week is live at a time
 * (ISO weeks, Monday start); {@link #rollover} closes a finished week and reports its top three.
 */
public final class WeeklyStats {
    /** {@code reachedAt} is when the player last gained a win in this view: the earlier one wins a tie. */
    public record Entry(UUID player, String name, int wins, String topGame, int topGameWins, long reachedAt) {
        public Entry(UUID player, String name, int wins, String topGame, int topGameWins) {
            this(player, name, wins, topGame, topGameWins, Long.MAX_VALUE);
        }
    }

    private final ConfigurationSection root;

    public WeeklyStats(ConfigurationSection root) { this.root = root; }

    public static String weekKey(LocalDate day) {
        return day.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public synchronized void recordWin(UUID player, String name, String game) {
        recordWin(player, name, game, System.currentTimeMillis());
    }

    synchronized void recordWin(UUID player, String name, String game, long nowMs) {
        String base = "players." + player;
        root.set(base + ".at", nowMs);
        root.set(base + ".game-at." + game, nowMs);
        root.set(base + ".name", name);
        root.set(base + ".wins", root.getInt(base + ".wins") + 1);
        root.set(base + ".games." + game, root.getInt(base + ".games." + game) + 1);
    }

    /** Best {@code limit} players by wins, or by wins in {@code game} when it is non-null. Ties go to whoever got there first. */
    public synchronized List<Entry> top(String game, int limit) {
        List<Entry> rows = new ArrayList<>();
        ConfigurationSection players = root.getConfigurationSection("players");
        if (players == null) return rows;
        for (String key : players.getKeys(false)) {
            ConfigurationSection p = players.getConfigurationSection(key);
            if (p == null) continue;
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException bad) { continue; }
            int wins = game == null ? p.getInt("wins") : p.getInt("games." + game);
            if (wins <= 0) continue;
            String topGame = null; int topWins = 0;
            ConfigurationSection games = p.getConfigurationSection("games");
            if (games != null) for (String g : games.getKeys(false)) {
                int w = games.getInt(g);
                if (w > topWins || (w == topWins && topGame != null && g.compareTo(topGame) < 0)) { topGame = g; topWins = w; }
            }
            long reached = p.getLong(game == null ? "at" : "game-at." + game, Long.MAX_VALUE);
            rows.add(new Entry(id, p.getString("name", "Player"), wins, topGame, topWins, reached));
        }
        rows.sort(Comparator.comparingInt(Entry::wins).reversed().thenComparingLong(Entry::reachedAt)
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    /** The ISO week the stored board belongs to, or null before the first run. */
    public synchronized String storedWeek() { return root.getString("week"); }

    public synchronized int wins(UUID player) { return root.getInt("players." + player + ".wins"); }

    /** Best player in each game this week, for the per-game column. */
    public synchronized Map<String, Entry> leaders(List<String> gameIds) {
        Map<String, Entry> result = new java.util.LinkedHashMap<>();
        for (String id : gameIds) {
            List<Entry> top = top(id, 1);
            if (!top.isEmpty()) result.put(id, top.getFirst());
        }
        return result;
    }

    /**
     * If {@code today} is in a later week than the stored one, clears the board and returns the
     * finished week's top three (possibly empty). Returns null when nothing rolled over. The first
     * run only stamps the current week.
     */
    public synchronized List<Entry> rollover(LocalDate today) {
        String current = weekKey(today);
        String stored = root.getString("week");
        if (current.equals(stored)) return null;
        List<Entry> winners = stored == null ? List.of() : top(null, 3);
        root.set("players", null);
        root.set("week", current);
        return winners;
    }
}
