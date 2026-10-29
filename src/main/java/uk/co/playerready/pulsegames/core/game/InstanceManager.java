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
    private record Rejoin(GameInstance game, Player previous, long expires, boolean waiting) {}
    private final Map<UUID, Rejoin> rejoins = new HashMap<>();

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

    public void remove(GameInstance instance) {
        instances.remove(instance);
        rejoins.entrySet().removeIf(entry -> entry.getValue().game() == instance);
    }

    public boolean hasReservedSeats(GameInstance instance) {
        return rejoins.values().stream().anyMatch(ticket -> ticket.waiting() && ticket.game() == instance);
    }

    public void disconnect(Player player) {
        GameInstance instance = byPlayer(player);
        if (instance == null) return;
        boolean waiting = instance.state() == GameState.WAITING || instance.state() == GameState.COUNTDOWN;
        int seconds = Math.max(0, Math.min(60, plugin.getConfig().getInt("rejoin.grace-seconds", 30)));
        if (seconds == 0 || rejoins.size() >= 2048) { instance.remove(player, false); return; }
        Rejoin ticket = new Rejoin(instance, player, System.currentTimeMillis() + seconds * 1000L, waiting);
        if (waiting && instance.isParticipant(player)) {
            rejoins.put(player.getUniqueId(), ticket);
            instance.suspendWaiting(player);
        } else {
            instance.remove(player, false); // A running disconnect forfeits the life; reconnect is observation only.
            if (instance.state() != GameState.RUNNING) return;
            rejoins.put(player.getUniqueId(), ticket);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!rejoins.remove(player.getUniqueId(), ticket)) return;
            if (ticket.waiting()) ticket.game().remove(ticket.previous(), false);
        }, seconds * 20L);
    }

    public boolean rejoin(Player player) {
        Rejoin ticket = rejoins.remove(player.getUniqueId());
        if (ticket == null) return false;
        if (System.currentTimeMillis() >= ticket.expires()) {
            if (ticket.waiting()) ticket.game().remove(ticket.previous(), false);
            return false;
        }
        return ticket.waiting() ? ticket.game().resumeWaiting(player) : ticket.game().returnSpectator(player);
    }

    /**
     * Finds a joinable instance for the game+mode with room for {@code groupSize}
     * players, or spins up a new one on a random eligible map.
     */
    public GameInstance findOrCreate(GameType type, GameMode mode, int groupSize) {
        return findOrCreate(type, mode, groupSize, instance -> true);
    }

    public GameInstance findOrCreate(GameType type, GameMode mode, int groupSize,
                                     java.util.function.Predicate<GameInstance> acceptsGroup) {
        for (GameInstance instance : instances) {
            if (!instance.practice() && !instance.isPrivate() && instance.type() == type && instance.mode().id().equals(mode.id()) && instance.joinable(groupSize) && acceptsGroup.test(instance)) {
                return instance;
            }
        }
        int max = plugin.getConfig().getInt("instances.max-concurrent", 20);
        if (instances.size() >= max) return null;
        var candidates = new ArrayList<>(plugin.arenas().forGame(type.id(), mode.id()));
        java.util.Collections.shuffle(candidates);
        GameInstance instance = null;
        for (Arena arena : candidates) {
            if (type.id().equals("duels") && mode.rulesId().equals("crystal")
                    && (!arena.settings().getBoolean("crystal-enabled", false) || arena.region("combat") == null)) continue;
            if (type.id().equals("duels") && mode.rulesId().equals("mace")
                    && !arena.settings().getBoolean("mace-enabled", false)) continue;
            GameInstance candidate = new GameInstance(plugin, type, mode, arena);
            if (candidate.maxPlayers() < Math.max(groupSize, candidate.minPlayers())) continue;
            if (mode.isTeams() && candidate.teamCount() < 2) continue;
            instance = candidate;
            break;
        }
        if (instance == null) return null;
        instances.add(instance);
        instance.init();
        return instance;
    }

    public GameInstance createPractice(GameType type, GameMode mode, Arena arena) {
        return createPractice(type, mode, arena, uk.co.playerready.pulsegames.core.bot.BotDifficulty.NORMAL, 1);
    }

    public GameInstance createPractice(GameType type, GameMode mode, Arena arena,
            uk.co.playerready.pulsegames.core.bot.BotDifficulty difficulty, int botCount) {
        if (instances.size() >= plugin.getConfig().getInt("instances.max-concurrent", 20)) return null;
        GameInstance instance = new GameInstance(plugin, type, mode, arena);
        if (instance.maxPlayers() < Math.max(1, instance.minPlayers())
                || (mode.isTeams() && instance.teamCount() < 2)) return null;
        instance.configurePractice(difficulty, botCount);
        instances.add(instance);
        instance.init();
        return instance;
    }

    /** A party-only game on a chosen arena; null when the server is full or the map cannot hold the group. */
    public GameInstance createPrivate(GameType type, GameMode mode, Arena arena,
                                      uk.co.playerready.pulsegames.core.play.PrivateSetup setup, int groupSize) {
        if (instances.size() >= plugin.getConfig().getInt("instances.max-concurrent", 20)) return null;
        GameInstance instance = new GameInstance(plugin, type, mode, arena);
        if (instance.maxPlayers() < Math.max(groupSize, 1) || (mode.isTeams() && instance.teamCount() < 2)) return null;
        instance.configurePrivate(setup);
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
