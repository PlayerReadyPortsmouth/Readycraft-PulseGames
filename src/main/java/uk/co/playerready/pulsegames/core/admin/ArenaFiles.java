package uk.co.playerready.pulsegames.core.admin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Arena YAML files on disk. Disabling an arena moves its file into {@code arenas/disabled/}; enabling moves it
 * back. {@code ArenaService.load()} only reads {@code arenas/*.yml}, so a reload makes the change real.
 */
public final class ArenaFiles {
    public static final String DISABLED = "disabled";

    /** One arena file and whether it is currently live. */
    public record Entry(String game, String id, String displayName, boolean enabled, Path file, boolean botsEnabled) { }

    private final Path arenasDir;

    public ArenaFiles(Path arenasDir) { this.arenasDir = arenasDir; }

    public Path disabledDir() { return arenasDir.resolve(DISABLED); }

    /** Where a live arena file goes when disabled. */
    public Path disabledPath(Path file) { return disabledDir().resolve(file.getFileName()); }

    /** Where a disabled arena file goes when enabled again. */
    public Path enabledPath(Path file) { return arenasDir.resolve(file.getFileName()); }

    public List<Entry> list(String game) {
        List<Entry> out = new ArrayList<>();
        scan(arenasDir, true, game, out);
        scan(disabledDir(), false, game, out);
        out.sort(java.util.Comparator.comparing(Entry::id, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private void scan(Path dir, boolean enabled, String game, List<Entry> out) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> Files.isRegularFile(f) && (f.toString().endsWith(".yml") || f.toString().endsWith(".yaml")))
                    .forEach(f -> {
                        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f.toFile());
                        String id = yml.getString("id"), g = yml.getString("game");
                        if (id == null || g == null || !g.equalsIgnoreCase(game)) return;
                        out.add(new Entry(g.toLowerCase(java.util.Locale.ROOT), id, yml.getString("display-name", id), enabled, f,
                                yml.getBoolean("settings.bots-enabled", false)));
                    });
        } catch (IOException ignored) {
            // an unreadable folder simply lists nothing
        }
    }

    /** Moves the file to the other folder; returns the new path. */
    public Path toggle(Entry entry) throws IOException {
        Path target = entry.enabled() ? disabledPath(entry.file()) : enabledPath(entry.file());
        Files.createDirectories(target.getParent());
        return Files.move(entry.file(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Writes {@code settings.bots-enabled} into the arena file. */
    public void setBots(Entry entry, boolean on) throws IOException {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(entry.file().toFile());
        yml.set("settings.bots-enabled", on);
        yml.save(entry.file().toFile());
    }
}
