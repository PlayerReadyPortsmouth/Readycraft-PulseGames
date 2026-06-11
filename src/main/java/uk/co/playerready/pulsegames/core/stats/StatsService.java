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

    public StatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 60, 20L * 60);
    }

    private void bump(Player player, String game, String key) {
        String path = player.getUniqueId() + "." + game + "." + key;
        synchronized (this) {
            data.set(path, data.getInt(path) + 1);
            dirty = true;
        }
    }

    public void addWin(Player player, String game) {
        bump(player, game, "wins");
        // Weekly tally for lobby leaderboards.
        String week = currentWeek();
        String path = "week." + week + "." + player.getUniqueId() + "." + game;
        synchronized (this) {
            data.set(path, data.getInt(path) + 1);
            dirty = true;
        }
    }

    public static String currentWeek() {
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        return now.get(wf.weekBasedYear()) + "-W" + now.get(wf.weekOfWeekBasedYear());
    }

    /** Total of a stat across all games (e.g. lifetime wins). */
    public synchronized int total(Player player, String key) {
        var section = data.getConfigurationSection(player.getUniqueId().toString());
        if (section == null) return 0;
        int sum = 0;
        for (String game : section.getKeys(false)) {
            sum += data.getInt(player.getUniqueId() + "." + game + "." + key);
        }
        return sum;
    }

    /** This week's top winners for a game ("overall" = all games combined). */
    public synchronized java.util.List<java.util.Map.Entry<java.util.UUID, Integer>> topWeekly(String game, int limit) {
        var section = data.getConfigurationSection("week." + currentWeek());
        if (section == null) return java.util.List.of();
        java.util.Map<java.util.UUID, Integer> totals = new java.util.HashMap<>();
        for (String rawUuid : section.getKeys(false)) {
            java.util.UUID uuid;
            try {
                uuid = java.util.UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            var games = section.getConfigurationSection(rawUuid);
            if (games == null) continue;
            int sum = 0;
            for (String g : games.getKeys(false)) {
                if (game.equals("overall") || game.equalsIgnoreCase(g)) {
                    sum += games.getInt(g);
                }
            }
            if (sum > 0) totals.put(uuid, sum);
        }
        return totals.entrySet().stream()
                .sorted(java.util.Map.Entry.<java.util.UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }
    public void addLoss(Player player, String game) { bump(player, game, "losses"); }
    public void addKill(Player player, String game) { bump(player, game, "kills"); }
    public void addPlayed(Player player, String game) { bump(player, game, "played"); }

    public synchronized int get(Player player, String game, String key) {
        return data.getInt(player.getUniqueId() + "." + game + "." + key);
    }

    public synchronized void flush() {
        if (!dirty) return;
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save stats.yml: " + ex.getMessage());
        }
    }
}
