package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropPaletteTest {
    @Test void parsesExplicitBlockAndHeadModelsWithCorrectHeights() {
        var config = new MemoryConfiguration();
        entry(config, "hay", "HAY_BLOCK", "ready:props/hay");
        entry(config, "head", "PLAYER_HEAD", "ready:props/head");
        var options = PropPalette.read(config);
        assertEquals(2, options.size());
        assertEquals(Material.HAY_BLOCK, options.get(0).material());
        assertEquals("ready:props/hay", options.get(0).model());
        assertEquals(1.0, options.get(0).height());
        assertEquals(0.5, options.get(1).height());
        assertThrows(UnsupportedOperationException.class, () -> options.clear());
    }
    @Test void rejectsMissingAndEmptyPalettes() {
        var config = new MemoryConfiguration();
        assertThrows(IllegalArgumentException.class, () -> PropPalette.read(config));
        config.createSection("content.prophunt.block-palette");
        assertThrows(IllegalArgumentException.class, () -> PropPalette.read(config));
    }
    @Test void rejectsDuplicateAndUnsafeMaterials() {
        var duplicate = new MemoryConfiguration();
        entry(duplicate, "one", "STONE", "ready:one");
        entry(duplicate, "two", "STONE", "ready:two");
        assertThrows(IllegalArgumentException.class, () -> PropPalette.read(duplicate));
        for (String material : new String[]{"TNT", "LAVA", "AIR", "OAK_SLAB", "NOT_A_MATERIAL"}) {
            var config = new MemoryConfiguration();
            entry(config, "prop", material, "ready:prop");
            assertThrows(IllegalArgumentException.class, () -> PropPalette.read(config), material);
        }
    }
    @Test void rejectsMalformedModelsAndUnsafeNames() {
        for (String model : new String[]{"", "missing_namespace", "Ready:prop", "ready:", "ready:has space", "ready:prop:again"}) {
            var config = new MemoryConfiguration();
            entry(config, "prop", "STONE", model);
            assertThrows(IllegalArgumentException.class, () -> PropPalette.read(config), model);
        }
        for (String name : new String[]{" ", "<red>Stone", "x".repeat(41)}) {
            var config = new MemoryConfiguration();
            entry(config, "prop", "STONE", "ready:stone");
            config.set("content.prophunt.block-palette.prop.name", name);
            assertThrows(IllegalArgumentException.class, () -> PropPalette.read(config), name);
        }
    }
    private void entry(MemoryConfiguration config, String key, String material, String model) {
        config.set("content.prophunt.block-palette." + key + ".material", material);
        config.set("content.prophunt.block-palette." + key + ".model", model);
    }
}
