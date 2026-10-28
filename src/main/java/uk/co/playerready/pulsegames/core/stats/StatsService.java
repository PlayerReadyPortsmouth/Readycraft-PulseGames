package uk.co.playerready.pulsegames.core.stats;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/** Simple YAML-backed per-player, per-game stats. Swap for a database later. */
public final class StatsService {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private boolean dirty;
    private final WeeklyStats weekly;

    public StatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        var weeklySection = data.getConfigurationSection("_weekly");
        this.weekly = new WeeklyStats(weeklySection != null ? weeklySection : data.createSection("_weekly"));
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 60, 20L * 60);
    }

    private void bump(Player player, String game, String key) {
        if (player.hasMetadata("NPC")) return;
        if (plugin instanceof uk.co.playerready.pulsegames.PulseGamesPlugin games) {
            var instance = games.instances().byPlayer(player);
            if (instance != null && !instance.ranked()) return;
        }
        String path = player.getUniqueId() + "." + game + "." + key;
        synchronized (this) {
            data.set(path, data.getInt(path) + 1);
            if (key.equals("wins")) weekly.recordWin(player.getUniqueId(), player.getName(), game);
            dirty = true;
        }
    }

    public void addWin(Player player, String game) { bump(player, game, "wins"); }
    public void addLoss(Player player, String game) { bump(player, game, "losses"); }
    public void addDraw(Player player, String game) { bump(player, game, "draws"); }
    public void addKill(Player player, String game) { bump(player, game, "kills"); }
    public void addPlayed(Player player, String game) { bump(player, game, "played"); }

    public synchronized java.util.List<WeeklyStats.Entry> topWeekly(String game, int limit) { return weekly.top(game, limit); }

    public synchronized java.util.Map<String, WeeklyStats.Entry> weeklyLeaders(java.util.List<String> gameIds) { return weekly.leaders(gameIds); }

    public synchronized int weeklyWins(Player player) { return weekly.wins(player.getUniqueId()); }

    /** The week the live board belongs to, read before {@link #weeklyRollover} replaces it. */
    public synchronized String weeklyWeek() { return weekly.storedWeek(); }

    /** Closes a finished week and returns its top three, or null if the week has not changed. */
    public synchronized java.util.List<WeeklyStats.Entry> weeklyRollover(java.time.LocalDate today) {
        var result = weekly.rollover(today);
        if (result != null) dirty = true;
        return result;
    }

    /**
     * Best time in ms for one map, or -1. Stored beside the counters as
     * {@code <uuid>.<game>.best-ms.<map>}, so old stats files load unchanged.
     */
    public synchronized long bestTime(Player player, String game, String map) {
        return data.getLong(bestPath(player, game, map), -1L);
    }

    /** Saves {@code ms} if it beats the stored time (or none exists). Bots and NPCs never save. */
    public boolean submitBestTime(Player player, String game, String map, long ms) {
        if (player.hasMetadata("NPC") || ms <= 0) return false;
        String path = bestPath(player, game, map);
        synchronized (this) {
            long old = data.getLong(path, -1L);
            if (old > 0 && old <= ms) return false;
            data.set(path, ms);
            dirty = true;
            return true;
        }
    }

    private static String bestPath(Player player, String game, String map) {
        return player.getUniqueId() + "." + game + ".best-ms." + map.replace('.', '_');
    }

    /** Keeps the highest value seen under a shared record key (e.g. a per-map best round). True if raised. */
    public synchronized boolean recordBest(String key, int value) {
        String path = "_records." + key;
        if (value <= data.getInt(path)) return false;
        data.set(path, value);
        dirty = true;
        return true;
    }

    public synchronized int record(String key) {
        return data.getInt("_records." + key);
    }

    public synchronized int get(Player player, String game, String key) {
        return data.getInt(player.getUniqueId() + "." + game + "." + key);
    }

    /**
     * Off-thread flush for callers on the main thread (every game end). A full-file YAML
     * write is far too slow to sit in a tick; falls back to a blocking write during
     * shutdown, when the scheduler no longer accepts tasks.
     */
    public void flushAsync() {
        if (!plugin.isEnabled()) {
            flush();
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::flush);
    }

    private final Object ioLock = new Object();

    /**
     * Snapshot under the data lock (cheap), then dump and write outside it: the main thread reads this store on
     * every token and stat lookup and must not wait for a YAML dump. The write is temp-file-then-move.
     */
    public void flush() {
        synchronized (ioLock) {
            org.bukkit.configuration.file.YamlConfiguration copy;
            synchronized (this) {
                if (!dirty) return;
                copy = uk.co.playerready.pulsegames.core.util.YamlFiles.snapshot(data);
                dirty = false;
            }
            String yaml = copy.saveToString();
            try {
                uk.co.playerready.pulsegames.core.util.YamlFiles.writeAtomically(file, yaml);
            } catch (IOException ex) {
                synchronized (this) { dirty = true; }
                plugin.getLogger().warning("Could not save stats.yml: " + ex.getMessage());
            }
        }
    }
}
