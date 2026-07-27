package uk.co.playerready.pulsegames.core.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.LocUtil;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A map definition for one game, loaded from arenas/*.yml. Coordinates are template-relative. */
public final class Arena {

    private final String id;
    private final String gameId;
    private final String displayName;
    private final String worldTemplate;
    private final List<String> modes; // empty = all modes
    private final int minPlayers;
    private final int maxPlayers;
    private final String lobby;
    private final String spectator;
    private final List<String> spawns;
    private final Map<String, Cuboid> regions;
    private final ConfigurationSection settings;

    public Arena(String id, String gameId, String displayName, String worldTemplate, List<String> modes,
                 int minPlayers, int maxPlayers, String lobby, String spectator, List<String> spawns,
                 Map<String, Cuboid> regions, ConfigurationSection settings) {
        this.id = id;
        this.gameId = gameId;
        this.displayName = displayName;
        this.worldTemplate = worldTemplate;
        this.modes = List.copyOf(modes);
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.lobby = lobby;
        this.spectator = spectator;
        this.spawns = List.copyOf(spawns);
        this.regions = Map.copyOf(regions);
        this.settings = settings != null ? settings : new MemoryConfiguration();
        // spawn() is reached only once the instance is already RUNNING, where an
        // ArithmeticException strands it un-endable - so a spawn-less arena is
        // refused at load instead.
        if (this.spawns.isEmpty()) {
            throw new IllegalArgumentException("arena '" + id + "' has no spawns");
        }
    }

    public String id() { return id; }
    public String gameId() { return gameId; }
    public String displayName() { return displayName; }
    public String worldTemplate() { return worldTemplate; }
    public int minPlayers() { return minPlayers; }
    public int maxPlayers() { return maxPlayers; }
    public ConfigurationSection settings() { return settings; }
    public Map<String, Cuboid> regions() { return regions; }

    public boolean supportsMode(String modeId) {
        return modes.isEmpty() || modes.stream().anyMatch(m -> m.equalsIgnoreCase(modeId));
    }

    public Location lobby(World world) { return LocUtil.parse(lobby, world); }

    public Location spectator(World world) {
        return LocUtil.parse(spectator != null ? spectator : lobby, world);
    }

    public List<String> rawSpawns() { return spawns; }

    public Location spawn(int index, World world) {
        return LocUtil.parse(spawns.get(index % spawns.size()), world);
    }

    public int spawnCount() { return spawns.size(); }

    public Cuboid region(String name) { return regions.get(name.toLowerCase(Locale.ROOT)); }

    /** All regions whose name starts with the given prefix, in numeric order. */
    public List<Cuboid> regionsByPrefix(String prefix) {
        return regions.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingLong((Map.Entry<String, Cuboid> e) -> trailingIndex(e.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .toList();
    }

    /** Checkpoints are consumed in list order, so checkpoint2 has to come before
     *  checkpoint10 - which plain string ordering gets backwards. Names without a
     *  trailing number sort last, by name. */
    private static long trailingIndex(String name) {
        int start = name.length();
        while (start > 0 && Character.isDigit(name.charAt(start - 1))) start--;
        if (start == name.length()) return Long.MAX_VALUE;
        try {
            return Long.parseLong(name.substring(start));
        } catch (NumberFormatException tooManyDigits) {
            return Long.MAX_VALUE;
        }
    }

    /** Location from settings ("key: x,y,z,yaw,pitch"). Null if absent. */
    public Location settingLocation(String key, World world) {
        String raw = settings.getString(key);
        return raw == null ? null : LocUtil.parse(raw, world);
    }
}
