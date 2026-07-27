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

    public void addWin(Player player, String game) { bump(player, game, "wins"); }
    public void addLoss(Player player, String game) { bump(player, game, "losses"); }
    public void addKill(Player player, String game) { bump(player, game, "kills"); }
    public void addPlayed(Player player, String game) { bump(player, game, "played"); }

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
