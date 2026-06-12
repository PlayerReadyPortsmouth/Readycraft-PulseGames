package uk.co.playerready.pulsegames.core.npc;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.LocUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * NPC definitions backed by the Citizens plugin (soft dependency).
 *
 * Two kinds: GAME NPCs open a game's play menu when right-clicked; GREETER
 * NPCs chat to players who walk near them. Place them in the lobby world, or
 * in an arena template world (via /pulse world) to have them appear inside
 * every game instance cloned from that map.
 *
 * Only {@link CitizensBridge} touches the Citizens API, and it is only loaded
 * when Citizens is installed, so the rest of the plugin runs fine without it.
 */
public final class NpcService implements Listener {

    private final PulseGamesPlugin plugin;
    private final File file;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
    private CitizensBridge bridge;

    public NpcService(PulseGamesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        load();
    }

    public boolean available() { return bridge != null; }

    public Collection<NpcDefinition> all() { return definitions.values(); }

    public NpcDefinition byId(String id) { return definitions.get(id.toLowerCase(Locale.ROOT)); }

    /** Call once on enable, after Citizens (a softdepend) has had its chance to load. */
    public void enable() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            plugin.getLogger().info("Citizens not found - NPC features disabled.");
            return;
        }
        bridge = new CitizensBridge(plugin);
        Bukkit.getPluginManager().registerEvents(bridge, plugin);
        for (World world : Bukkit.getWorlds()) {
            spawnStatic(world);
        }
    }

    public void shutdown() {
        if (bridge != null) bridge.removeAll();
    }

    // ---- lifecycle hooks ---------------------------------------------------------

    /** Spawns definitions placed directly in {@code world} (lobby, build worlds). */
    private void spawnStatic(World world) {
        if (bridge == null) return;
        for (NpcDefinition def : definitions.values()) {
            if (def.world().equals(world.getName())) {
                bridge.spawn(def, world);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // Covers admin build worlds loaded later with /pulse world. Instance
        // clones never match by name; spawnForInstance handles those.
        spawnStatic(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        // Drop our copies so a later reload spawns them fresh (no duplicates).
        if (bridge != null) bridge.despawnWorld(event.getWorld());
    }

    /** Spawns copies of this map's NPCs into a freshly cloned instance world. */
    public void spawnForInstance(GameInstance instance) {
        if (bridge == null || instance.world() == null) return;
        for (NpcDefinition def : definitions.values()) {
            if (def.world().equals(instance.arena().worldTemplate())) {
                bridge.spawn(def, instance.world());
            }
        }
    }

    /** Removes this instance's NPC copies before its world is deleted. */
    public void despawnForInstance(GameInstance instance) {
        if (bridge != null && instance.world() != null) bridge.despawnWorld(instance.world());
    }

    // ---- admin operations ----------------------------------------------------------

    /** Creates a definition at the player's spot and spawns it immediately. */
    public NpcDefinition create(Player at, NpcDefinition.Kind kind, String gameId, String name) {
        String id = nextId(kind);
        NpcDefinition def = new NpcDefinition(id, kind, at.getWorld().getName(),
                LocUtil.serialize(at.getLocation()), name);
        def.setGameId(gameId);
        def.setRadius(plugin.getConfig().getDouble("npc.chat-radius", 4));
        definitions.put(id, def);
        save();
        if (bridge != null) bridge.spawn(def, at.getWorld());
        return def;
    }

    public boolean remove(String id) {
        NpcDefinition def = definitions.remove(id.toLowerCase(Locale.ROOT));
        if (def == null) return false;
        save();
        if (bridge != null) bridge.despawnDefinition(def);
        return true;
    }

    public void addLine(NpcDefinition def, String line) {
        def.lines().add(line);
        save();
    }

    public void setSkin(NpcDefinition def, String skin) {
        def.setSkin(skin);
        save();
        if (bridge != null) bridge.respawn(def);
    }

    public void setRadius(NpcDefinition def, double radius) {
        def.setRadius(radius);
        save();
    }

    private String nextId(NpcDefinition.Kind kind) {
        String prefix = kind.name().toLowerCase(Locale.ROOT) + "-";
        int n = 1;
        while (definitions.containsKey(prefix + n)) n++;
        return prefix + n;
    }

    // ---- persistence -----------------------------------------------------------------

    private void load() {
        definitions.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            NpcDefinition.Kind kind;
            try {
                kind = NpcDefinition.Kind.valueOf(s.getString("kind", "GREETER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("npcs.yml: unknown kind for '" + id + "', skipping.");
                continue;
            }
            NpcDefinition def = new NpcDefinition(id.toLowerCase(Locale.ROOT), kind,
                    s.getString("world", "world"), s.getString("location", "0,100,0"),
                    s.getString("name", id));
            def.setSkin(s.getString("skin"));
            def.setGameId(s.getString("game"));
            def.setRadius(s.getDouble("radius", 4));
            def.lines().addAll(s.getStringList("lines"));
            definitions.put(def.id(), def);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (NpcDefinition def : definitions.values()) {
            String base = "npcs." + def.id() + ".";
            yaml.set(base + "kind", def.kind().name());
            yaml.set(base + "name", def.name());
            yaml.set(base + "world", def.world());
            yaml.set(base + "location", def.rawLocation());
            if (def.skin() != null) yaml.set(base + "skin", def.skin());
            if (def.gameId() != null) yaml.set(base + "game", def.gameId());
            yaml.set(base + "radius", def.radius());
            if (!def.lines().isEmpty()) yaml.set(base + "lines", def.lines());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save npcs.yml: " + e.getMessage());
        }
    }
}
