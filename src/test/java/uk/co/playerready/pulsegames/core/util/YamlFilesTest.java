package uk.co.playerready.pulsegames.core.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YamlFilesTest {
    @Test void snapshotIsDetachedFromLaterEdits() {
        YamlConfiguration live = new YamlConfiguration();
        live.set("a.b.tokens", 5);
        live.set("a.list", java.util.List.of("x"));
        YamlConfiguration copy = YamlFiles.snapshot(live);
        live.set("a.b.tokens", 99);
        live.set("c", 1);
        assertEquals(5, copy.getInt("a.b.tokens"));
        assertEquals(java.util.List.of("x"), copy.getStringList("a.list"));
        assertFalse(copy.contains("c"));
    }

    @Test void writeReplacesTheFileWholeAndLeavesNoTempBehind(@TempDir Path dir) throws Exception {
        File target = dir.resolve("economy.yml").toFile();
        Files.writeString(target.toPath(), "old: 1\n");
        YamlFiles.writeAtomically(target, "new: 2\n");
        assertEquals("new: 2\n", Files.readString(target.toPath()));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "temp file must have been moved into place");
        }
    }
}
