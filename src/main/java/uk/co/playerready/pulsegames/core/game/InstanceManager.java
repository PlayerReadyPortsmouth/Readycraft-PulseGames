package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks live instances and finds/creates one for joining players. */
public final class InstanceManager {

    private final PulseGamesPlugin plugin;
    private final List<GameInstance> instances = new ArrayList<>();
    private final Map<UUID, GameInstance> byPlayer = new HashMap<>();

    public InstanceManager(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public List<GameInstance> all() { return List.copyOf(instances); }

    public GameInstance byPlayer(Player player) { return byPlayer.get(player.getUniqueId()); }

    public GameInstance byWorld(org.bukkit.World world) {
        for (GameInstance instance : instances) {
            if (world.equals(instance.world())) return instance;
        }
        return null;
    }

    public void index(Player player, GameInstance instance) { byPlayer.put(player.getUniqueId(), instance); }

    public void unindex(Player player) { byPlayer.remove(player.getUniqueId()); }

    public void remove(GameInstance instance) { instances.remove(instance); }

    /**
     * Finds a joinable instance for the game+mode with room for {@code groupSize}
     * players, or spins up a new one on a random eligible map.
     */
    public GameInstance findOrCreate(GameType type, GameMode mode, int groupSize) {
        for (GameInstance instance : instances) {
            if (instance.type() == type && instance.mode().id().equals(mode.id()) && instance.joinable(groupSize)) {
                return instance;
            }
        }
        int max = plugin.getConfig().getInt("instances.max-concurrent", 20);
        if (instances.size() >= max) return null;
        Arena arena = plugin.arenas().pickRandom(type.id(), mode.id());
        if (arena == null) return null;
        GameInstance instance = new GameInstance(plugin, type, mode, arena);
        instances.add(instance);
        instance.init();
        return instance;
    }

    /**
     * Shuts every instance down. One instance failing must not stop the rest, and must
     * not abort the caller: onDisable() flushes stats and token balances after this, and
     * those writes are the last chance to persist a finished round.
     */
    public void shutdownAll() {
        for (GameInstance instance : new ArrayList<>(instances)) {
            try {
                instance.cleanup();
            } catch (Throwable ex) {
                plugin.getLogger().severe("Instance cleanup failed during shutdown: " + ex);
            }
        }
    }
}
