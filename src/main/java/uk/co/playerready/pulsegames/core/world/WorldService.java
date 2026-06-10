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

/**
 * Clones world templates (plugins/PulseGames/maps/<template>/) into throwaway
 * instance worlds so many copies of one map can run simultaneously.
 */
public final class WorldService {

    private static final Set<String> SKIP_FILES = Set.of("uid.dat", "session.lock");

    private final JavaPlugin plugin;
    private final String prefix;

    public WorldService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfig().getString("instances.world-prefix", "pulse_");
    }

    public File templateDir(String template) {
        return new File(new File(plugin.getDataFolder(), "maps"), template);
    }

    /** Copies the template off-thread, then loads the world on the main thread. */
    public void cloneAndLoad(String template, String instanceId, Consumer<World> onReady, Runnable onFail) {
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
        world.save();
        File source = world.getWorldFolder();
        File target = templateDir(template);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteRecursively(target.toPath());
                copyDirectory(source.toPath(), target.toPath());
                Bukkit.getScheduler().runTask(plugin, onDone);
            } catch (IOException ex) {
                Bukkit.getScheduler().runTask(plugin, () -> onFail.accept(ex.getMessage()));
            }
        });
    }

    /** Unloads the instance world (no save) and deletes its folder off-thread. */
    public void unloadAndDelete(World world) {
        File folder = world.getWorldFolder();
        Bukkit.unloadWorld(world, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteRecursively(folder.toPath()));
    }

    /** Deletes leftover instance worlds from a previous run/crash. */
    public void purgeLeftovers() {
        File[] leftovers = Bukkit.getWorldContainer().listFiles((d, n) -> n.startsWith(prefix));
        if (leftovers == null) return;
        for (File dir : leftovers) {
            deleteRecursively(dir.toPath());
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

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) return;
        try {
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
        } catch (IOException ignored) {
        }
    }

    /** Empty generator so unexplored chunks in instance worlds stay void. */
    public static final class VoidGenerator extends ChunkGenerator {
    }
}
