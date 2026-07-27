package uk.co.playerready.pulsegames.core.world;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Clones world templates (plugins/PulseGames/maps/<template>/) into throwaway
 * instance worlds so many copies of one map can run simultaneously.
 */
public final class WorldService {

    private static final Set<String> SKIP_FILES = Set.of("uid.dat", "session.lock");

    /** Template ids become folder names and are deleted recursively, so anything
     *  that could escape the maps folder ("..", "/", a drive letter) is rejected. */
    private static final Pattern SAFE_TEMPLATE_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final JavaPlugin plugin;
    private final String prefix;

    public WorldService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfig().getString("instances.world-prefix", "pulse_");
    }

    public static boolean isValidTemplateId(String template) {
        return template != null && SAFE_TEMPLATE_ID.matcher(template).matches();
    }

    public File mapsDir() {
        return new File(plugin.getDataFolder(), "maps");
    }

    public File templateDir(String template) {
        if (!isValidTemplateId(template)) {
            throw new IllegalArgumentException("Invalid map template id '" + template
                    + "' (allowed: a-z, 0-9, '_' and '-', up to 32 characters)");
        }
        return new File(mapsDir(), template);
    }

    /**
     * Second line of defence behind {@link #isValidTemplateId}: refuses to hand back a
     * path that resolves outside the maps folder, so no delete can ever walk out of it.
     */
    private File templateDirForWrite(String template) throws IOException {
        File target = templateDir(template);
        String maps = mapsDir().getCanonicalPath() + File.separator;
        if (!target.getCanonicalPath().startsWith(maps)) {
            throw new IOException("Refusing to write map template outside " + maps + ": "
                    + target.getCanonicalPath());
        }
        return target;
    }

    /** Copies the template off-thread, then loads the world on the main thread. */
    public void cloneAndLoad(String template, String instanceId, Consumer<World> onReady, Runnable onFail) {
        if (!isValidTemplateId(template)) {
            plugin.getLogger().severe("Arena refers to an invalid world template id '" + template + "'.");
            onFail.run();
            return;
        }
        File source = templateDir(template);
        String worldName = prefix + instanceId;
        File target = new File(Bukkit.getWorldContainer(), worldName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!source.isDirectory()) {
                    throw new IOException("Missing world template: " + source.getPath());
                }
                copyDirectory(source.toPath(), target.toPath());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    World world = new WorldCreator(worldName)
                            .environment(World.Environment.NORMAL)
                            .generator(new VoidGenerator())
                            .createWorld();
                    if (world == null) {
                        onFail.run();
                        return;
                    }
                    world.setAutoSave(false);
                    world.setDifficulty(Difficulty.NORMAL);
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                    world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
                    world.setGameRule(GameRule.KEEP_INVENTORY, true);
                    onReady.accept(world);
                });
            } catch (IOException ex) {
                plugin.getLogger().severe("World clone failed for " + template + ": " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, onFail);
            }
        });
    }

    /** Saves a live world and copies it into the template folder (used by /pulse setup save). */
    public void saveAsTemplate(World world, String template, Runnable onDone, Consumer<String> onFail) {
        File target;
        try {
            target = templateDirForWrite(template);
        } catch (IOException | IllegalArgumentException ex) {
            onFail.accept(ex.getMessage());
            return;
        }
        world.save();
        File source = world.getWorldFolder();
        File staging = new File(target.getParentFile(), target.getName() + ".tmp");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Copy into a sibling staging folder first: replacing the template in
                // place means a mid-copy failure destroys the only copy of the map.
                deleteRecursively(staging.toPath());
                copyDirectory(source.toPath(), staging.toPath());
                deleteRecursively(target.toPath());
                move(staging.toPath(), target.toPath());
                Bukkit.getScheduler().runTask(plugin, onDone);
            } catch (IOException ex) {
                deleteQuietly(staging.toPath());
                Bukkit.getScheduler().runTask(plugin, () -> onFail.accept(ex.getMessage()));
            }
        });
    }

    /** Unloads the instance world (no save) and deletes its folder off-thread. */
    public void unloadAndDelete(World world) {
        unloadAndDelete(world, 0);
    }

    private void unloadAndDelete(World world, int attempt) {
        File folder = world.getWorldFolder();
        // Evacuate anyone still inside so the world can actually unload. A loaded
        // world whose folder is deleted underneath it spams save errors every tick.
        World fallback = Bukkit.getWorlds().get(0);
        for (var player : world.getPlayers()) {
            if (player.isInsideVehicle()) player.leaveVehicle();
            player.teleport(fallback.getSpawnLocation());
        }
        if (!Bukkit.unloadWorld(world, false)) {
            // During shutdown the scheduler rejects new tasks, so there is no retry to
            // schedule; the server unloads every world moments later and purgeLeftovers()
            // clears the folder on the next boot.
            if (!plugin.isEnabled()) return;
            if (attempt >= 5) {
                plugin.getLogger().warning("Gave up unloading " + world.getName()
                        + " after " + attempt + " tries; not deleting its folder.");
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> unloadAndDelete(world, attempt + 1), 20L);
            return;
        }
        if (!plugin.isEnabled()) {
            deleteQuietly(folder.toPath());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteQuietly(folder.toPath()));
    }

    /** Deletes leftover instance worlds from a previous run/crash. */
    public void purgeLeftovers() {
        File[] leftovers = Bukkit.getWorldContainer().listFiles((d, n) -> n.startsWith(prefix));
        if (leftovers == null) return;
        for (File dir : leftovers) {
            deleteQuietly(dir.toPath());
            plugin.getLogger().info("Purged leftover instance world " + dir.getName());
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!SKIP_FILES.contains(file.getFileName().toString())) {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Best-effort delete for throwaway instance worlds, where a locked file is not
     *  worth failing a shutdown over - purgeLeftovers() sweeps it on the next boot. */
    private static void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException ignored) {
        }
    }

    /** Empty generator so unexplored chunks in instance worlds stay void. */
    public static final class VoidGenerator extends ChunkGenerator {
    }
}
