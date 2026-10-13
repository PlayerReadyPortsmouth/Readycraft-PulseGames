package uk.co.playerready.pulsegames.core.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import java.util.*;

/** Permanent editing worlds are saved and unloaded, never deleted. Main-thread only. */
public final class BuildWorldLifecycle {
    private final PulseGamesPlugin plugin;
    private final Set<String> managed = new HashSet<>();
    private final Map<UUID, Long> idle = new HashMap<>();
    private final Map<UUID, Integer> captures = new HashMap<>();
    public BuildWorldLifecycle(PulseGamesPlugin plugin) { this.plugin = plugin; }
    public void start() { Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(System.currentTimeMillis()), 20L, 20L); }
    public void manage(World world) { managed.add(world.getName()); }
    public void retain(World world) { captures.merge(world.getUID(), 1, Integer::sum); }
    public void release(World world) { captures.computeIfPresent(world.getUID(), (id, count) -> count > 1 ? count - 1 : null); }
    void tick(long now) {
        int seconds = plugin.getConfig().getInt("build-worlds.idle-unload-seconds", 15);
        if (seconds <= 0) { idle.clear(); return; }
        Set<UUID> loaded = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            UUID id = world.getUID(); loaded.add(id);
            boolean editing = managed.contains(world.getName()) || world.getName().startsWith("rc_build_")
                    || new java.io.File(world.getWorldFolder(), ".pulsegames-void").isFile();
            boolean protectedWorld = world == Bukkit.getWorlds().get(0)
                    || world.getName().equals(plugin.getConfig().getString("lobby.world", "world"))
                    || plugin.getConfig().getStringList("build-worlds.keep-loaded").contains(world.getName())
                    || plugin.instances().byWorld(world) != null;
            if (!editing || protectedWorld || !world.getPlayers().isEmpty() || captures.containsKey(id)) {
                idle.remove(id); continue;
            }
            long since = idle.computeIfAbsent(id, key -> now);
            if (now - since < Math.max(1, seconds) * 1000L) continue;
            try {
                // Bukkit's successful unload includes saving; failure never permits deletion.
                if (Bukkit.unloadWorld(world, true)) {
                    idle.remove(id);
                    plugin.getLogger().info("Saved and unloaded idle build world " + world.getName());
                } else idle.put(id, now); // Cancelled unloads get a bounded retry delay.
            } catch (RuntimeException ex) {
                idle.put(id, now);
                plugin.getLogger().warning("Could not save/unload build world " + world.getName() + ": " + ex);
            }
        }
        idle.keySet().retainAll(loaded);
    }
}
