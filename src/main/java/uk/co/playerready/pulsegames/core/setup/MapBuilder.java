package uk.co.playerready.pulsegames.core.setup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.game.GameType;
import uk.co.playerready.pulsegames.core.util.Text;
import uk.co.playerready.pulsegames.core.world.WorldService;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headless map generation from a JSON "blueprint" - one file describing both the
 * world geometry (via {@link uk.co.playerready.pulsegames.core.shapes.ShapeService}
 * primitives) and the arena definition (spawns, regions, lobby, settings...). A
 * blueprint can be built entirely from the server console with no player and no
 * in-game wand work, so maps can be authored as code by an assistant and stamped
 * straight into a live template + arena file.
 *
 * <p>File: plugins/PulseGames/blueprints/&lt;name&gt;.json
 * <pre>
 * {
 *   "arena-id": "frostpeak",
 *   "game": "spleef",
 *   "display-name": "&lt;aqua&gt;Frostpeak",
 *   "modes": ["classic", "decay"],
 *   "min-players": 2,
 *   "max-players": 12,
 *   "origin": [0, 100, 0],          // shape offsets are relative to this
 *   "shapes": [ ... ShapeService shapes, coords offset from origin ... ],
 *   "lobby": [0.5, 106, 0.5, 0, 0], // ABSOLUTE world coords (x,y,z[,yaw,pitch])
 *   "spectator": [0.5, 120, 0.5],
 *   "spawns": [[18.5, 102, 0.5, 90, 0], [-17.5, 102, 0.5, -90, 0]],
 *   "regions": { "floor1": {"min": [-20,100,-20], "max": [20,100,20]} },
 *   "settings": { "void-y": 90 }
 * }
 * </pre>
 * Geometry (shapes) is relocatable via origin; arena points are absolute so they
 * line up exactly with where the blocks land in the template world.
 */
public final class MapBuilder {

    private final PulseGamesPlugin plugin;
    /** Only one build at a time: capturing a world while another is mid-build
     *  races on chunk saving and silently produces an empty template. */
    private boolean building;

    public MapBuilder(PulseGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private File blueprintsDir() {
        File dir = new File(plugin.getDataFolder(), "blueprints");
        if (!dir.exists()) {
            dir.mkdirs();
            try {
                plugin.saveResource("blueprints/example-spleef.json", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return dir;
    }

    public List<String> available() {
        File[] files = blueprintsDir().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return List.of();
        return java.util.Arrays.stream(files).map(f -> f.getName().replace(".json", "")).sorted().toList();
    }

    /** Builds the named blueprint into a fresh template world and live arena file. */
    public void build(CommandSender sender, String name) {
        File file = new File(blueprintsDir(), name + ".json");
        if (!file.exists()) {
            msg(sender, "<red>No blueprint <yellow>" + name + ".json</yellow>. Available: <white>"
                    + String.join(", ", available()));
            return;
        }
        if (building) {
            msg(sender, "<red>Another map build is in progress - wait for it to finish, then retry.");
            return;
        }
        JsonObject bp;
        try {
            bp = JsonParser.parseString(Files.readString(file.toPath())).getAsJsonObject();
        } catch (Exception ex) {
            msg(sender, "<red>Invalid JSON in " + file.getName() + ": " + ex.getMessage());
            return;
        }

        String arenaId;
        GameType game;
        try {
            arenaId = req(bp, "arena-id").getAsString().toLowerCase(Locale.ROOT);
            game = plugin.registry().get(req(bp, "game").getAsString());
            if (game == null) throw new IllegalArgumentException("unknown game '" + bp.get("game").getAsString() + "'");
        } catch (Exception ex) {
            msg(sender, "<red>Blueprint error: " + ex.getMessage());
            return;
        }

        String worldName = "pulsebuild_" + game.id() + "_" + arenaId;
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            plugin.worlds().unloadAndDelete(existing);
            msg(sender, "<gray>Cleared a previous build world; run the command again in a moment.");
            return;
        }
        File leftover = new File(Bukkit.getWorldContainer(), worldName);
        if (leftover.exists()) {
            msg(sender, "<red>Build world folder still exists; try again shortly.");
            return;
        }

        World world = new WorldCreator(worldName)
                .environment(World.Environment.NORMAL)
                .generator(new WorldService.VoidGenerator())
                .createWorld();
        if (world == null) {
            msg(sender, "<red>Could not create build world.");
            return;
        }
        world.setAutoSave(false);

        int[] origin = bp.has("origin") ? vec(bp.getAsJsonArray("origin")) : new int[]{0, 100, 0};
        Location originLoc = new Location(world, origin[0], origin[1], origin[2]);
        JsonArray shapes = bp.has("shapes") ? bp.getAsJsonArray("shapes") : new JsonArray();

        msg(sender, "Building <yellow>" + arenaId + "</yellow> (" + game.id() + ") - stamping "
                + shapes.size() + " shape(s)...");
        building = true;
        try {
            plugin.shapes().build(world, originLoc, shapes,
                    () -> finish(sender, bp, game, arenaId, worldName, world));
        } catch (Exception ex) {
            building = false;
            msg(sender, "<red>Geometry error: " + ex.getMessage());
            plugin.worlds().unloadAndDelete(world);
        }
    }

    private void finish(CommandSender sender, JsonObject bp, GameType game, String arenaId,
                        String worldName, World world) {
        try {
            writeArenaFile(bp, game, arenaId);
        } catch (Exception ex) {
            building = false;
            msg(sender, "<red>Could not write arena file: " + ex.getMessage());
            plugin.worlds().unloadAndDelete(world);
            return;
        }
        msg(sender, "<gray>Geometry done. Capturing world template <yellow>" + arenaId + "</yellow>...");
        plugin.worlds().saveAsTemplate(world, arenaId, () -> {
            building = false;
            plugin.worlds().unloadAndDelete(world);
            plugin.arenas().load();
            msg(sender, "<green><b>Arena " + arenaId + " is live!</b></green> <gray>Try <yellow>/play "
                    + game.id());
        }, error -> {
            building = false;
            msg(sender, "<red>World capture failed: " + error);
            plugin.worlds().unloadAndDelete(world);
        });
    }

    private void writeArenaFile(JsonObject bp, GameType game, String arenaId) throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("id", arenaId);
        yml.set("game", game.id());
        yml.set("display-name", bp.has("display-name") ? bp.get("display-name").getAsString() : arenaId);
        yml.set("world-template", arenaId);
        if (bp.has("modes")) {
            List<String> modes = new ArrayList<>();
            for (JsonElement m : bp.getAsJsonArray("modes")) modes.add(m.getAsString());
            yml.set("modes", modes);
        }
        yml.set("min-players", bp.has("min-players") ? bp.get("min-players").getAsInt() : 2);
        yml.set("max-players", bp.has("max-players") ? bp.get("max-players").getAsInt() : 16);
        yml.set("lobby", loc(req(bp, "lobby").getAsJsonArray()));
        if (bp.has("spectator")) yml.set("spectator", loc(bp.getAsJsonArray("spectator")));

        List<String> spawns = new ArrayList<>();
        for (JsonElement s : req(bp, "spawns").getAsJsonArray()) spawns.add(loc(s.getAsJsonArray()));
        yml.set("spawns", spawns);

        if (bp.has("regions")) {
            JsonObject regions = bp.getAsJsonObject("regions");
            for (String key : regions.keySet()) {
                JsonObject r = regions.getAsJsonObject(key);
                int[] min = vec(r.getAsJsonArray("min"));
                int[] max = vec(r.getAsJsonArray("max"));
                yml.set("regions." + key.toLowerCase(Locale.ROOT) + ".min", min[0] + "," + min[1] + "," + min[2]);
                yml.set("regions." + key.toLowerCase(Locale.ROOT) + ".max", max[0] + "," + max[1] + "," + max[2]);
            }
        }
        if (bp.has("settings")) {
            JsonObject settings = bp.getAsJsonObject("settings");
            for (String key : settings.keySet()) {
                JsonElement v = settings.get(key);
                if (v.isJsonArray()) {
                    yml.set("settings." + key, loc(v.getAsJsonArray()));
                } else if (v.getAsJsonPrimitive().isNumber()) {
                    double d = v.getAsDouble();
                    yml.set("settings." + key, d == Math.floor(d) ? (Object) (int) d : (Object) d);
                } else if (v.getAsJsonPrimitive().isBoolean()) {
                    yml.set("settings." + key, v.getAsBoolean());
                } else {
                    yml.set("settings." + key, v.getAsString());
                }
            }
        }

        File file = new File(new File(plugin.getDataFolder(), "arenas"), game.id() + "-" + arenaId + ".yml");
        file.getParentFile().mkdirs();
        yml.save(file);
    }

    // ---- helpers -----------------------------------------------------------------

    private JsonElement req(JsonObject obj, String key) {
        if (!obj.has(key)) throw new IllegalArgumentException("missing required field '" + key + "'");
        return obj.get(key);
    }

    private int[] vec(JsonArray array) {
        if (array.size() < 3) throw new IllegalArgumentException("coordinate needs [x, y, z]");
        return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()};
    }

    /** Absolute location array [x,y,z] or [x,y,z,yaw,pitch] -> "x,y,z,yaw,pitch" string. */
    private String loc(JsonArray a) {
        if (a.size() < 3) throw new IllegalArgumentException("location needs at least [x, y, z]");
        double x = a.get(0).getAsDouble();
        double y = a.get(1).getAsDouble();
        double z = a.get(2).getAsDouble();
        double yaw = a.size() > 3 ? a.get(3).getAsDouble() : 0;
        double pitch = a.size() > 4 ? a.get(4).getAsDouble() : 0;
        return "%.2f,%.2f,%.2f,%.1f,%.1f".formatted(x, y, z, yaw, pitch);
    }

    private void msg(CommandSender sender, String miniMessage) {
        sender.sendMessage(Text.msg(miniMessage));
    }
}
