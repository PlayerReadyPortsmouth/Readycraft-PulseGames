package uk.co.playerready.pulsegames.core.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Safe persistence for the YAML stores. {@code YamlConfiguration.save} truncates the live file and then writes
 * it, so a kill or power cut mid-write leaves a torn file that loads as empty and is overwritten by the next
 * autosave. These helpers snapshot cheaply under the store's lock and write a temp file moved into place.
 */
public final class YamlFiles {
    private YamlFiles() {}

    /**
     * Detached copy of the data, cheap enough to take under a lock; serialise it ({@code saveToString}) off the
     * main thread. Nested sections are rebuilt, so later edits to the source do not leak into the copy.
     */
    public static YamlConfiguration snapshot(YamlConfiguration source) {
        YamlConfiguration copy = new YamlConfiguration();
        for (var entry : source.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) copy.set(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /** Writes {@code content} to a sibling temp file, then moves it over {@code target} in one step. */
    public static void writeAtomically(File target, String content) throws IOException {
        Path destination = target.toPath().toAbsolutePath();
        Path temp = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
