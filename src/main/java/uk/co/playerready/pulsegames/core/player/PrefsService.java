package uk.co.playerready.pulsegames.core.player;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player preferences. Calm mode tones down the sensory load (no flashing
 * titles, no loud effect sounds, no particle bursts) for players who find the
 * full experience overwhelming - toggled with /calm, persisted across sessions.
 */
public final class PrefsService {

    private final JavaPlugin plugin;
    private final File file;
    private final Set<UUID> calm = new HashSet<>();

    public PrefsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "prefs.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String raw : data.getStringList("calm")) {
            try {
                calm.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public boolean isCalm(Player player) {
        return calm.contains(player.getUniqueId());
    }

    /** Toggles calm mode and returns the new state. */
    public boolean toggleCalm(Player player) {
        boolean nowCalm;
        if (calm.remove(player.getUniqueId())) {
            nowCalm = false;
        } else {
            calm.add(player.getUniqueId());
            nowCalm = true;
        }
        save();
        return nowCalm;
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("calm", calm.stream().map(UUID::toString).toList());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                data.save(file);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save prefs.yml: " + ex.getMessage());
            }
        });
    }
}
