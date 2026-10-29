package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Per-game weekly counters (wins, kills, tokens) for the lobby boards, kept in a section of stats.yml as
 * {@code week}, {@code names.<uuid>} and {@code players.<uuid>.<game>.<stat>}. The week is an ISO week; the
 * first read or write in a later week clears the counters. Network-wide tokens live under the game
 * {@link #NETWORK}. The same ranking code reads the all-time counters, which share the layout.
 */
public final class WeeklyCounters {
    public static final String NETWORK = "network";

    public record Row(UUID player, String name, int value) { }

    private final ConfigurationSection root;

    public WeeklyCounters(ConfigurationSection root) { this.root = root; }

    /** Clears the counters if {@code today} is in a later week than the stored one. True if it did. */
    public synchronized boolean rollover(LocalDate today) {
        String current = WeeklyStats.weekKey(today);
        if (current.equals(root.getString("week"))) return false;
        root.set("players", null);
        root.set("names", null);
        root.set("week", current);
        return true;
    }

    public synchronized void add(LocalDate today, UUID id, String name, String game, String stat, int amount) {
        rollover(today);
        String path = "players." + id + "." + game + "." + stat;
        root.set(path, root.getInt(path) + amount);
        root.set("names." + id, name);
    }

    public synchronized List<Row> top(LocalDate today, String game, String stat, int limit) {
        rollover(today);
        return rank(root.getConfigurationSection("players"), root.getConfigurationSection("names"), game, stat, limit);
    }

    public synchronized String week() { return root.getString("week"); }

    /**
     * Best {@code limit} players for {@code stat}: in one {@code game}, or summed across games when it is null
     * (tokens are only counted under {@link #NETWORK}). {@code players} maps uuid to game to stat.
     */
    public static List<Row> rank(ConfigurationSection players, ConfigurationSection names, String game, String stat, int limit) {
        List<Row> rows = new ArrayList<>();
        if (players == null) return rows;
        for (String key : players.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException skip) { continue; }
            ConfigurationSection p = players.getConfigurationSection(key);
            if (p == null) continue;
            int value = 0;
            if (game != null) value = p.getInt(game + "." + stat);
            else if (stat.equals("tokens")) value = p.getInt(NETWORK + ".tokens");
            else for (String g : p.getKeys(false)) if (!g.equals(NETWORK)) value += p.getInt(g + "." + stat);
            if (value > 0) rows.add(new Row(id, names == null ? "Player" : names.getString(key, "Player"), value));
        }
        rows.sort(Comparator.comparingInt(Row::value).reversed().thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    /** Fastest {@code limit} times in ms stored at {@code <uuid>.<game>.best-ms.<map>}; value is the time. */
    public static List<Row> fastest(ConfigurationSection players, ConfigurationSection names, String game, String map, int limit) {
        List<Row> rows = new ArrayList<>();
        if (players == null) return rows;
        for (String key : players.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException skip) { continue; }
            ConfigurationSection p = players.getConfigurationSection(key);
            if (p == null) continue;
            long ms = p.getLong(game + ".best-ms." + map, -1L);
            if (ms > 0) rows.add(new Row(id, names == null ? "Player" : names.getString(key, "Player"), (int) Math.min(ms, Integer.MAX_VALUE)));
        }
        rows.sort(Comparator.comparingInt(Row::value).thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }
}
