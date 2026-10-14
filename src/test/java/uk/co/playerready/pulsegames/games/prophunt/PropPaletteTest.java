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
    @Test void absentOverrideUsesGlobalButExplicitSelectionIsNarrowed() {
        var config=new MemoryConfiguration();entry(config,"stone","STONE","ready:stone");entry(config,"head","PLAYER_HEAD","ready:head");
        var settings=new MemoryConfiguration();assertEquals(2,PropPalette.read(config,settings).size());
        settings.set("prop-palette",java.util.List.of("PLAYER_HEAD"));
        var result=PropPalette.read(config,settings);assertEquals(1,result.size());assertEquals(Material.PLAYER_HEAD,result.get(0).material());
        assertEquals(.5,result.get(0).height());assertEquals(2,PropPalette.read(config).size());
    }
    @Test void invalidArenaOverrideNeverFallsBackToBroaderGlobalChoices() {
        var config=new MemoryConfiguration();entry(config,"stone","STONE","ready:stone");
        for(Object bad:java.util.List.of("STONE",java.util.List.of(),java.util.List.of("TNT"),java.util.List.of("STONE","STONE"),java.util.List.of("BOOKSHELF"),java.util.Arrays.asList("STONE",null),java.util.List.of("STONE",42))) {
            var settings=new MemoryConfiguration();settings.set("prop-palette",bad);
            assertThrows(IllegalArgumentException.class,()->PropPalette.read(config,settings));
        }
    }
    @Test void expandedCatalogueFitsSelectorAndPreservesDimensions() {
        var config = new MemoryConfiguration();
        for (Material material : PropPalette.ALLOWED)
            entry(config, material.name().toLowerCase(java.util.Locale.ROOT), material.name(), "ready:prop_" + material.name().toLowerCase(java.util.Locale.ROOT));
        var options = PropPalette.read(config);
        assertEquals(27, options.size());
        assertEquals(26, options.stream().filter(option -> option.height() == 1.0).count());
        assertTrue(options.stream().anyMatch(option -> option.material() == Material.BARREL));
        assertTrue(options.stream().anyMatch(option -> option.material() == Material.LOOM));
    }
    @Test void rolloutFragmentAndDefaultsCoverExactlySupportedModels() throws Exception {
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        assertEquals(10, PropPalette.read(config).size());
        var additions = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("docs/prophunt-expanded-palette.yml"));
        var section = additions.getConfigurationSection("content.prophunt.block-palette");
        for (String key : section.getKeys(false)) {
            var value = section.getConfigurationSection(key);
            for (String field : value.getKeys(false)) config.set("content.prophunt.block-palette." + key + "." + field, value.get(field));
        }
        var options = PropPalette.read(config);
        assertEquals(PropPalette.ALLOWED, options.stream().map(PropPalette.Option::material).collect(java.util.stream.Collectors.toSet()));
        assertEquals(27, options.size());
    }
    private void entry(MemoryConfiguration config, String key, String material, String model) {
        config.set("content.prophunt.block-palette." + key + ".material", material);
        config.set("content.prophunt.block-palette." + key + ".model", model);
    }
}
