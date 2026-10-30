package uk.co.playerready.pulsegames.core.world;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Server;
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
import java.util.Random;
import java.lang.reflect.InvocationTargetException;
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

    public World loadReturnWorld(String name) {
        if (name == null || !name.matches("[a-z0-9_-]{1,64}") || name.startsWith(prefix) || name.startsWith("pulsebuild_")) return null;
        World existing=Bukkit.getWorld(name);if(existing!=null)return existing;
        WorldCreator creator=new WorldCreator(name);File folder=worldDirectory(creator);
        if (!folder.isDirectory() || Files.isSymbolicLink(folder.toPath()) || !new File(folder,"region").isDirectory()) return null;
        boolean marked=new File(folder,".pulsegames-void").isFile();
        if (!name.startsWith("rc_build_") && !marked) return null;
        if (marked) creator.generator(new VoidGenerator()).generateStructures(false);
        World loaded=creator.createWorld();
        if (loaded!=null) buildLifecycle().manage(loaded);
        return loaded;
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
        WorldCreator creator = new WorldCreator(worldName)
                .environment(World.Environment.NORMAL).generator(new VoidGenerator());
        File target = worldDirectory(creator);
        boolean modern = modernLevelDirectory() != null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> failSafe("World clone for " + template, () -> {
            if (!source.isDirectory()) {
                throw new IOException("Missing world template: " + source.getPath());
            }
            if (modern && Files.exists(source.toPath().resolve("level.dat"))) {
                throw new IOException("Legacy template must be imported and re-captured on this Paper version: " + template);
            }
            if (!Files.isDirectory(source.toPath().resolve("region"))) {
                throw new IOException("Template has no saved region data: " + template);
            }
            if (target.exists()) throw new IOException("Instance folder already exists: " + target);
            copyDirectory(source.toPath(), target.toPath());
            Bukkit.getScheduler().runTask(plugin, () -> failSafe("World load for " + template, () -> {
                World world = creator.createWorld();
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
            }, onFail, plugin.getLogger()));
        }, () -> Bukkit.getScheduler().runTask(plugin, onFail), plugin.getLogger()));
    }

    interface Step { void run() throws Exception; }

    /**
     * Runs one step of the clone/load. Anything it throws, not only an IOException (an unchecked error in the copy
     * used to leave the instance in LOADING for good, holding one of the concurrent-instance slots), is logged and
     * fails the instance so the slot is freed.
     */
    static void failSafe(String what, Step step, Runnable onFail, java.util.logging.Logger log) {
        try {
            step.run();
        } catch (Throwable failure) {
            log.severe(what + " failed: " + failure);
            try { onFail.run(); } catch (Throwable ignored) { /* the slot is freed by cleanup; nothing more to do */ }
        }
    }

    /** Saves a live world and copies it into the template folder (used by /admin games setup save). */
    public void saveAsTemplate(World world, String template, Runnable onDone, Consumer<String> onFail) {
        File target;
        try {
            target = templateDirForWrite(template);
        } catch (IOException | IllegalArgumentException ex) {
            onFail.accept(ex.getMessage());
            return;
        }
        // Flush queued region and saved-data writes before the off-thread snapshot.
        world.save(true);
        buildLifecycle().retain(world);
        File source = world.getWorldFolder();
        File staging = new File(target.getParentFile(), target.getName() + ".tmp");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Copy into a sibling staging folder first: replacing the template in
                // place means a mid-copy failure destroys the only copy of the map.
                deleteRecursively(staging.toPath());
                copyDirectory(source.toPath(), staging.toPath());
                // Bind review evidence to this exact capture, even if the separate review ledger write fails.
                Files.writeString(staging.toPath().resolve(".pulse-capture-revision"), java.util.UUID.randomUUID().toString());
                deleteRecursively(target.toPath());
                move(staging.toPath(), target.toPath());
                Bukkit.getScheduler().runTask(plugin, () -> { buildLifecycle().release(world); onDone.run(); });
            } catch (IOException ex) {
                deleteQuietly(staging.toPath());
                Bukkit.getScheduler().runTask(plugin, () -> { buildLifecycle().release(world); onFail.accept(ex.getMessage()); });
            }
        });
    }

    private BuildWorldLifecycle buildLifecycle() {
        return ((uk.co.playerready.pulsegames.PulseGamesPlugin) plugin).buildWorlds();
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
            diagnosticWorld(world, "unload-retry", "attempt="+attempt);
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
        diagnosticWorld(world, "world-unloaded", "Unloaded before deletion");
        if (!plugin.isEnabled()) {
            deleteQuietly(folder.toPath());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { deleteRecursively(folder.toPath()); diagnosticWorld(world,"world-deleted","Instance folder removed"); }
            catch(IOException ex){diagnosticWorld(world,"delete-failed",ex.getClass().getSimpleName());}
        });
    }

    private void diagnosticWorld(World world,String event,String detail) {
        if(plugin instanceof uk.co.playerready.pulsegames.PulseGamesPlugin games && games.diagnostics()!=null
                && world.getName().startsWith(prefix)) {
            games.diagnostics().record(world.getName().substring(prefix.length()),"world","cleanup",world.getName(),event,detail);
        }
    }

    /** Deletes leftover instance worlds from a previous run/crash. */
    public void purgeLeftovers() {
        purgeDirectory(Bukkit.getWorldContainer());
        Path level = modernLevelDirectory();
        if (level != null) purgeDirectory(level.resolve("dimensions/minecraft").toFile());
    }

    private void purgeDirectory(File parent) {
        File[] leftovers = parent.listFiles((d, n) -> n.startsWith(prefix));
        if (leftovers == null) return;
        for (File dir : leftovers) {
            if (!dir.isDirectory() || Files.isSymbolicLink(dir.toPath())) continue;
            boolean loaded = Bukkit.getWorlds().stream()
                    .anyMatch(world -> world.getWorldFolder().toPath().toAbsolutePath().normalize()
                            .equals(dir.toPath().toAbsolutePath().normalize()));
            if (loaded) continue;
            deleteQuietly(dir.toPath());
            plugin.getLogger().info("Purged leftover instance world " + dir.getName());
        }
    }

    /** Paper 26.1 moved custom worlds below the level directory, keyed by namespace. */
    public static File worldDirectory(WorldCreator creator) {
        return resolveWorldDirectory(Bukkit.getWorldContainer().toPath(), modernLevelDirectory(),
                creator.name(), creator.key().getNamespace(), creator.key().getKey()).toFile();
    }

    static Path resolveWorldDirectory(Path container, Path level, String name, String namespace, String key) {
        return level == null ? container.resolve(name) : level.resolve("dimensions").resolve(namespace).resolve(key);
    }

    private static Path modernLevelDirectory() {
        try {
            return (Path) Server.class.getMethod("getLevelDirectory").invoke(Bukkit.getServer());
        } catch (NoSuchMethodException legacy) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Cannot resolve Paper world storage", ex);
        }
    }

    static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // Paper 26.1 stores the world UUID here instead of uid.dat. Each
                // clone needs a fresh identity, including simultaneous copies of one map.
                boolean identity = source.relativize(file).equals(Path.of("data", "paper", "metadata.dat"));
                if (!identity && !SKIP_FILES.contains(file.getFileName().toString())) {
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
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            // No terrain exists to search for. Matches use their configured arena spawn.
            return new Location(world, 0.5, 100, 0.5);
        }
    }
}
