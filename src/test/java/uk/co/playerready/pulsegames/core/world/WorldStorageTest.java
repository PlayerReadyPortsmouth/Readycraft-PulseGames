package uk.co.playerready.pulsegames.core.world;

import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WorldStorageTest {
    @TempDir Path root;

    @Test void modernClonesGoInsideTheLevelDimensionDirectory() {
        assertEquals(root.resolve("lobby/dimensions/minecraft/pulse_duel"),
                WorldService.resolveWorldDirectory(root, root.resolve("lobby"), "pulse_duel", "minecraft", "pulse_duel"));
    }

    @Test void legacyServersKeepSeparateWorldFolders() {
        assertEquals(root.resolve("pulse_duel"),
                WorldService.resolveWorldDirectory(root, null, "pulse_duel", "minecraft", "pulse_duel"));
    }

    @Test void cloneKeepsTerrainAndRulesButDoesNotReuseWorldIdentity() throws Exception {
        Path source = root.resolve("template");
        Files.createDirectories(source.resolve("region"));
        Files.createDirectories(source.resolve("data/paper"));
        Files.createDirectories(source.resolve("data/minecraft"));
        Files.writeString(source.resolve("region/r.0.0.mca"), "terrain");
        Files.writeString(source.resolve("data/paper/metadata.dat"), "old-uuid");
        Files.writeString(source.resolve("data/paper/level_overrides.dat"), "spawn");
        Files.writeString(source.resolve("data/minecraft/game_rules.dat"), "rules");
        Files.writeString(source.resolve("uid.dat"), "legacy-uuid");
        Files.writeString(source.resolve("session.lock"), "lock");
        Path first = root.resolve("first"), second = root.resolve("second");
        WorldService.copyDirectory(source, first);
        WorldService.copyDirectory(source, second);
        for (Path copy : new Path[]{first, second}) {
            assertEquals("terrain", Files.readString(copy.resolve("region/r.0.0.mca")));
            assertEquals("spawn", Files.readString(copy.resolve("data/paper/level_overrides.dat")));
            assertEquals("rules", Files.readString(copy.resolve("data/minecraft/game_rules.dat")));
            assertFalse(Files.exists(copy.resolve("data/paper/metadata.dat")));
            assertFalse(Files.exists(copy.resolve("uid.dat")));
            assertFalse(Files.exists(copy.resolve("session.lock")));
        }
        assertTrue(Files.exists(source.resolve("data/paper/metadata.dat")));
    }

    @Test void voidWorldHasAnExplicitSpawnInsteadOfSearchingForTerrain() {
        World world = mock(World.class);
        var spawn = new WorldService.VoidGenerator().getFixedSpawnLocation(world, new Random(1));
        assertNotNull(spawn);
        assertSame(world, spawn.getWorld());
        assertEquals(100, spawn.getBlockY());
    }
}
