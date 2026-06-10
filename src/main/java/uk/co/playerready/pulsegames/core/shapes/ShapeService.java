package uk.co.playerready.pulsegames.core.shapes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.playerready.pulsegames.core.util.Text;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates world geometry from JSON "shape schematics" - data files describing
 * primitives (boxes, cylinders, spheres, pillars, rings, scatters...) that are
 * pasted relative to where you stand. Lets maps be authored as code/data
 * (e.g. by an AI assistant) and stamped into a build world in-game.
 *
 * File format (plugins/PulseGames/shapes/<name>.json):
 * <pre>
 * {
 *   "name": "Spleef Arena",
 *   "shapes": [
 *     {"type": "cylinder", "material": "SNOW_BLOCK", "center": [0,0,0], "radius": 20, "height": 1},
 *     {"type": "box", "from": [-22,-5,-22], "to": [22,-5,22], "material": "BARRIER"},
 *     {"type": "sphere", "center": [0,25,0], "radius": 8, "material": "GLASS", "hollow": true},
 *     {"type": "pillars", "origin": [-12,0,-12], "countX": 4, "countZ": 4,
 *      "spacing": 8, "height": 12, "material": "QUARTZ_BLOCK"},
 *     {"type": "ring", "center": [0,40,0], "radius": 6, "material": "GOLD_BLOCK"},
 *     {"type": "checker", "from": [-10,0,-10], "to": [10,0,10],
 *      "materials": ["WHITE_CONCRETE", "BLACK_CONCRETE"]},
 *     {"type": "scatter", "from": [-20,1,-20], "to": [20,1,20], "density": 0.1,
 *      "materials": ["POPPY", "DANDELION", "SHORT_GRASS"]}
 *   ]
 * }
 * </pre>
 * All coordinates are offsets from the paste origin. "material" (single) or
 * "materials" (random pick per block) work on every shape.
 */
public final class ShapeService {

    private static final int BLOCKS_PER_TICK = 4000;

    private final JavaPlugin plugin;
    private final Map<UUID, Map<Location, BlockData>> undoBuffers = new HashMap<>();

    public ShapeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private File shapesDir() {
        File dir = new File(plugin.getDataFolder(), "shapes");
        if (!dir.exists()) {
            dir.mkdirs();
            for (String example : List.of("spleef-arena.json", "lucky-pillars.json", "skywars-island.json")) {
                try {
                    plugin.saveResource("shapes/" + example, false);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return dir;
    }

    public List<String> available() {
        File[] files = shapesDir().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return List.of();
        return java.util.Arrays.stream(files).map(f -> f.getName().replace(".json", "")).sorted().toList();
    }

    public void paste(Player player, String name) {
        File file = new File(shapesDir(), name + ".json");
        if (!file.exists()) {
            player.sendMessage(Text.msg("<red>No shape file <yellow>" + name + ".json</yellow>. Available: <white>"
                    + String.join(", ", available())));
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file.toPath())).getAsJsonObject();
        } catch (Exception ex) {
            player.sendMessage(Text.msg("<red>Invalid JSON in " + file.getName() + ": " + ex.getMessage()));
            return;
        }
        Location origin = player.getLocation().toBlockLocation();
        Map<Location, BlockData> placements = new LinkedHashMap<>();
        try {
            for (JsonElement element : root.getAsJsonArray("shapes")) {
                buildShape(element.getAsJsonObject(), origin, placements);
            }
        } catch (Exception ex) {
            player.sendMessage(Text.msg("<red>Shape error: " + ex.getMessage()));
            return;
        }
        // Record what we overwrite for /pulse shape undo.
        Map<Location, BlockData> undo = new LinkedHashMap<>();
        for (Location loc : placements.keySet()) {
            undo.put(loc, loc.getBlock().getBlockData());
        }
        undoBuffers.put(player.getUniqueId(), undo);
        String displayName = root.has("name") ? root.get("name").getAsString() : name;
        player.sendMessage(Text.msg("Pasting <yellow>" + displayName + "</yellow> <gray>("
                + placements.size() + " blocks)..."));
        applyBatched(placements, () -> player.sendMessage(
                Text.msg("<green>Paste complete!</green> <gray>Undo with <yellow>/pulse shape undo")));
    }

    public void undo(Player player) {
        Map<Location, BlockData> undo = undoBuffers.remove(player.getUniqueId());
        if (undo == null) {
            player.sendMessage(Text.msg("<red>Nothing to undo."));
            return;
        }
        player.sendMessage(Text.msg("Undoing <yellow>" + undo.size() + "</yellow> blocks..."));
        applyBatched(undo, () -> player.sendMessage(Text.msg("<green>Undo complete.")));
    }

    private void applyBatched(Map<Location, BlockData> placements, Runnable onDone) {
        List<Map.Entry<Location, BlockData>> entries = new ArrayList<>(placements.entrySet());
        new java.util.function.Consumer<Integer>() {
            @Override
            public void accept(Integer start) {
                int end = Math.min(start + BLOCKS_PER_TICK, entries.size());
                for (int i = start; i < end; i++) {
                    Block block = entries.get(i).getKey().getBlock();
                    block.setBlockData(entries.get(i).getValue(), false);
                }
                if (end < entries.size()) {
                    final var self = this;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> self.accept(end), 1L);
                } else {
                    onDone.run();
                }
            }
        }.accept(0);
    }

    // ---- primitives ----------------------------------------------------------------

    private void buildShape(JsonObject shape, Location origin, Map<Location, BlockData> out) {
        String type = shape.get("type").getAsString().toLowerCase();
        switch (type) {
            case "box" -> box(shape, origin, out);
            case "cylinder" -> cylinder(shape, origin, out);
            case "sphere", "dome" -> sphere(shape, origin, out, type.equals("dome"));
            case "pillars" -> pillars(shape, origin, out);
            case "ring" -> ring(shape, origin, out);
            case "checker" -> checker(shape, origin, out);
            case "scatter" -> scatter(shape, origin, out);
            default -> throw new IllegalArgumentException("Unknown shape type: " + type);
        }
    }

    private void box(JsonObject shape, Location origin, Map<Location, BlockData> out) {
        int[] from = vec(shape, "from");
        int[] to = vec(shape, "to");
        boolean hollow = bool(shape, "hollow");
        int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
        int minY = Math.min(from[1], to[1]), maxY = Math.max(from[1], to[1]);
        int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    if (hollow && x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) continue;
                    put(out, origin, x, y, z, material(shape));
                }
    }

    private void cylinder(JsonObject shape, Location origin, Map<Location, BlockData> out) {
        int[] center = vec(shape, "center");
        double radius = shape.get("radius").getAsDouble();
        int height = shape.has("height") ? shape.get("height").getAsInt() : 1;
        boolean hollow = bool(shape, "hollow");
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                if (hollow && dist < radius - 1.5) continue;
                for (int y = 0; y < height; y++) {
                    put(out, origin, center[0] + x, center[1] + y, center[2] + z, material(shape));
                }
            }
    }

    private void sphere(JsonObject shape, Location origin, Map<Location, BlockData> out, boolean dome) {
        int[] center = vec(shape, "center");
        double radius = shape.get("radius").getAsDouble();
        boolean hollow = bool(shape, "hollow");
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++)
            for (int y = dome ? 0 : -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (dist > radius) continue;
                    if (hollow && dist < radius - 1.5) continue;
                    put(out, origin, center[0] + x, center[1] + y, center[2] + z, material(shape));
                }
    }

    private void pillars(JsonObject shape, Location origin, Map<Location, BlockData> out) {
        int[] start = vec(shape, "origin");
        int countX = shape.get("countX").getAsInt();
        int countZ = shape.get("countZ").getAsInt();
        int spacing = shape.get("spacing").getAsInt();
        int height = shape.get("height").getAsInt();
        for (int ix = 0; ix < countX; ix++)
            for (int iz = 0; iz < countZ; iz++)
                for (int y = 0; y < height; y++) {
                    put(out, origin, start[0] + ix * spacing, start[1] + y, start[2] + iz * spacing,
                            material(shape));
                }
    }

    private void ring(JsonObject shape, Location origin, Map<Location, BlockData> out) {
        int[] center = vec(shape, "center");
        double radius = shape.get("radius").getAsDouble();
        int segments = (int) Math.max(16, radius * 8);
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            int x = (int) Math.round(center[0] + radius * Math.cos(angle));
            int z = (int) Math.round(center[2] + radius * Math.sin(angle));
            put(out, origin, x, center[1], z, material(shape));
        }
    }

    private void checker(JsonObject shape, Location origin, Map<Location, BlockData> out) {
        int[] from = vec(shape, "from");
        int[] to = vec(shape, "to");
        List<Material> materials = materials(shape);
        int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
        int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                Material material = materials.get(Math.abs(x + z) % materials.size());
                out.put(new Location(origin.getWorld(), origin.getBlockX() + x,
                        origin.getBlockY() + from[1], origin.getBlockZ() + z), material.createBlockData());
            }
    }

    private void scatter(JsonObject shape, Location origin, Map<Location, BlockData> out) {
        int[] from = vec(shape, "from");
        int[] to = vec(shape, "to");
        double density = shape.has("density") ? shape.get("density").getAsDouble() : 0.1;
        var random = ThreadLocalRandom.current();
        int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
        int minY = Math.min(from[1], to[1]), maxY = Math.max(from[1], to[1]);
        int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    if (random.nextDouble() < density) {
                        put(out, origin, x, y, z, material(shape));
                    }
                }
    }

    // ---- helpers ----------------------------------------------------------------------

    private void put(Map<Location, BlockData> out, Location origin, int x, int y, int z, Material material) {
        World world = origin.getWorld();
        int worldY = origin.getBlockY() + y;
        if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) return;
        out.put(new Location(world, origin.getBlockX() + x, worldY, origin.getBlockZ() + z),
                material.createBlockData());
    }

    private int[] vec(JsonObject shape, String key) {
        JsonArray array = shape.getAsJsonArray(key);
        if (array == null || array.size() != 3) {
            throw new IllegalArgumentException("Shape needs \"" + key + "\": [x, y, z]");
        }
        return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()};
    }

    private boolean bool(JsonObject shape, String key) {
        return shape.has(key) && shape.get(key).getAsBoolean();
    }

    private Material material(JsonObject shape) {
        List<Material> materials = materials(shape);
        return materials.size() == 1 ? materials.get(0)
                : materials.get(ThreadLocalRandom.current().nextInt(materials.size()));
    }

    private List<Material> materials(JsonObject shape) {
        List<Material> result = new ArrayList<>();
        if (shape.has("materials")) {
            for (JsonElement element : shape.getAsJsonArray("materials")) {
                Material material = Material.matchMaterial(element.getAsString());
                if (material == null) throw new IllegalArgumentException("Unknown material " + element.getAsString());
                result.add(material);
            }
        } else if (shape.has("material")) {
            Material material = Material.matchMaterial(shape.get("material").getAsString());
            if (material == null) throw new IllegalArgumentException("Unknown material " + shape.get("material").getAsString());
            result.add(material);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Shape needs \"material\" or \"materials\"");
        return result;
    }
}
