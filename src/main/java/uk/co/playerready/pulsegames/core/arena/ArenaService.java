package uk.co.playerready.pulsegames.core.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Loads arena YAMLs and performs random map selection. */
public final class ArenaService {

    private final JavaPlugin plugin;
    private final List<Arena> arenas = new ArrayList<>();

    public ArenaService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        File dir = new File(plugin.getDataFolder(), "arenas");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files == null) return;
        for (File file : files) {
            try {
                Arena arena = parse(YamlConfiguration.loadConfiguration(file));
                if (arena != null) arenas.add(arena);
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to load arena " + file.getName() + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
    }

    private Arena parse(YamlConfiguration yml) {
        String id = yml.getString("id");
        String game = yml.getString("game");
        if (id == null || game == null) return null;
        Map<String, Cuboid> regions = new HashMap<>();
        ConfigurationSection regionSec = yml.getConfigurationSection("regions");
        if (regionSec != null) {
            for (String name : regionSec.getKeys(false)) {
                ConfigurationSection r = regionSec.getConfigurationSection(name);
                regions.put(name.toLowerCase(Locale.ROOT), Cuboid.parse(r.getString("min"), r.getString("max")));
            }
        }
        return new Arena(
                id,
                game.toLowerCase(Locale.ROOT),
                yml.getString("display-name", id),
                yml.getString("world-template", id),
                yml.getStringList("modes"),
                yml.getInt("min-players", 2),
                yml.getInt("max-players", 16),
                yml.getString("lobby", "0.5,100,0.5"),
                yml.getString("spectator"),
                yml.getStringList("spawns"),
                regions,
                yml.getConfigurationSection("settings"));
    }

    public List<Arena> forGame(String gameId, String modeId) {
        return arenas.stream()
                .filter(a -> a.gameId().equalsIgnoreCase(gameId) && a.supportsMode(modeId))
                .toList();
    }

    /** Random map selection for a game+mode. Null if no arena is configured. */
    public Arena pickRandom(String gameId, String modeId) {
        List<Arena> pool = forGame(gameId, modeId);
        return pool.isEmpty() ? null : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public Arena byId(String gameId, String arenaId) {
        return arenas.stream()
                .filter(a -> a.gameId().equalsIgnoreCase(gameId) && a.id().equalsIgnoreCase(arenaId))
                .findFirst().orElse(null);
    }

    public List<Arena> all() { return List.copyOf(arenas); }
}
