package uk.co.playerready.pulsegames.core.ranked;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ranked ratings kept in a {@code _ranked} section of stats.yml (same file, same flush as the other counters).
 * Every call locks on {@code lock}, which is the StatsService, so a flush never sees a half-written change.
 */
public final class RankedStore {
    public record Rating(int rating, int games, int peak) {
        public static final Rating NEW = new Rating(Elo.START, 0, Elo.START);
    }
    public record Row(UUID player, String name, int rating, int games) { }

    private static final int HISTORY = 10;

    private final ConfigurationSection root;
    private final Object lock;
    private final Runnable dirty;

    public RankedStore(ConfigurationSection root, Object lock, Runnable dirty) {
        this.root = root;
        this.lock = lock;
        this.dirty = dirty;
    }

    private static String path(UUID player, String kit) { return "players." + player + "." + kit; }

    public Rating get(UUID player, String kit) {
        synchronized (lock) {
            String base = path(player, kit);
            if (!root.contains(base + ".rating")) return Rating.NEW;
            int rating = root.getInt(base + ".rating", Elo.START);
            return new Rating(rating, root.getInt(base + ".games"), root.getInt(base + ".peak", rating));
        }
    }

    public void put(UUID player, String name, String kit, Rating value) {
        synchronized (lock) {
            root.set("players." + player + ".name", name);
            String base = path(player, kit);
            root.set(base + ".rating", value.rating());
            root.set(base + ".games", value.games());
            root.set(base + ".peak", Math.max(value.peak(), value.rating()));
            dirty.run();
        }
    }

    /** Newest first, at most ten. */
    public List<String> history(UUID player) {
        synchronized (lock) { return List.copyOf(root.getStringList("players." + player + ".history")); }
    }

    public void addHistory(UUID player, String line) {
        synchronized (lock) {
            List<String> lines = new ArrayList<>(root.getStringList("players." + player + ".history"));
            lines.addFirst(line);
            while (lines.size() > HISTORY) lines.removeLast();
            root.set("players." + player + ".history", lines);
            dirty.run();
        }
    }

    /** Players who have finished placement, highest rating first. */
    public List<Row> top(String kit, int limit) {
        synchronized (lock) {
            ConfigurationSection players = root.getConfigurationSection("players");
            List<Row> rows = new ArrayList<>();
            if (players != null) for (String key : players.getKeys(false)) {
                ConfigurationSection mine = players.getConfigurationSection(key + "." + kit);
                if (mine == null || !Elo.placed(mine.getInt("games"))) continue;
                try {
                    rows.add(new Row(UUID.fromString(key), players.getString(key + ".name", "Player"),
                            mine.getInt("rating", Elo.START), mine.getInt("games")));
                } catch (IllegalArgumentException ignored) { }
            }
            rows.sort(Comparator.comparingInt(Row::rating).reversed().thenComparing(Row::name));
            return List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        }
    }

    // ---- season ---------------------------------------------------------------------------

    public String seasonId() { synchronized (lock) { return root.getString("season.id"); } }
    public boolean seasonClosed() { synchronized (lock) { return root.getBoolean("season.closed"); } }

    public void setSeason(String id, boolean closed) {
        synchronized (lock) {
            root.set("season.id", id);
            root.set("season.closed", closed);
            dirty.run();
        }
    }

    /**
     * Archives each player's peak division of the season as {@code archive.<id>.<uuid>.<kit>}, then moves every
     * rating halfway back to 1000 and restarts the peak there. Returns how many ratings were reset.
     */
    public int archiveAndSoftReset(String seasonId, Divisions divisions) {
        synchronized (lock) {
            ConfigurationSection players = root.getConfigurationSection("players");
            int count = 0;
            if (players != null) for (String key : players.getKeys(false)) {
                ConfigurationSection mine = players.getConfigurationSection(key);
                for (String kit : mine.getKeys(false)) {
                    ConfigurationSection r = mine.getConfigurationSection(kit);
                    if (r == null || !r.contains("rating")) continue;
                    int games = r.getInt("games");
                    if (Elo.placed(games))
                        root.set("archive." + seasonId + "." + key + "." + kit,
                                divisions.of(r.getInt("peak", r.getInt("rating"))).name());
                    int next = SeasonRules.softReset(r.getInt("rating"));
                    r.set("rating", next);
                    r.set("peak", next);
                    count++;
                }
            }
            dirty.run();
            return count;
        }
    }

    /** Peak division the player finished a past season in, or null. */
    public String archived(String seasonId, UUID player, String kit) {
        synchronized (lock) { return root.getString("archive." + seasonId + "." + player + "." + kit); }
    }
}
