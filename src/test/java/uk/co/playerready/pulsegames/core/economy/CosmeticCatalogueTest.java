package uk.co.playerready.pulsegames.core.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.progression.XpRules;

import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticCatalogueTest {
    private static CosmeticCatalogue.Parsed shipped() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                CosmeticCatalogueTest.class.getResourceAsStream("/cosmetics.yml")));
        return CosmeticCatalogue.parse(yaml.getConfigurationSection("cosmetics"),
                CosmeticsService.BUILTIN.stream().map(Cosmetic::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void shippedFileParsesWithNoProblemsAndTheCatalogueReachesForty() {
        var parsed = shipped();
        assertEquals(List.of(), parsed.problems());
        assertTrue(CosmeticsService.BUILTIN.size() + parsed.items().size() >= 40);
    }

    @Test void idsAreUniqueAcrossBuiltInAndData() {
        Set<String> ids = new HashSet<>();
        CosmeticsService.BUILTIN.forEach(c -> assertTrue(ids.add(c.id()), c.id()));
        shipped().items().forEach(c -> assertTrue(ids.add(c.id()), c.id()));
    }

    @Test void everyItemHasASlotAndAPriceOrALevelButNotBoth() {
        var all = new java.util.ArrayList<>(CosmeticsService.BUILTIN);
        all.addAll(shipped().items());
        for (Cosmetic c : all) {
            assertNotNull(c.category(), c.id());
            assertTrue(c.price() > 0 ^ c.level() > 0, c.id());
        }
        shipped().items().forEach(c -> assertNotNull(c.effect(), c.id()));
    }

    @Test void everyFifthLevelHasExactlyOneReward() {
        var levels = shipped().items().stream().filter(Cosmetic::levelUnlock).map(Cosmetic::level).sorted().toList();
        for (int level = XpRules.UNLOCK_EVERY; level <= XpRules.MAX_LEVEL; level += XpRules.UNLOCK_EVERY)
            assertEquals(1, java.util.Collections.frequency(levels, level), "level " + level);
        assertEquals(20, levels.size());
    }

    @Test void badEntriesAreSkippedAndReported() {
        var yaml = new YamlConfiguration();
        yaml.set("cosmetics.ok.slot", "trail"); yaml.set("cosmetics.ok.name", "Ok"); yaml.set("cosmetics.ok.icon", "STICK");
        yaml.set("cosmetics.ok.price", 100); yaml.set("cosmetics.ok.effect.particle", "FLAME");
        yaml.set("cosmetics.noslot.name", "X"); yaml.set("cosmetics.noslot.icon", "STICK"); yaml.set("cosmetics.noslot.price", 5);
        yaml.set("cosmetics.both.slot", "trail"); yaml.set("cosmetics.both.name", "B"); yaml.set("cosmetics.both.icon", "STICK");
        yaml.set("cosmetics.both.price", 5); yaml.set("cosmetics.both.level", 5); yaml.set("cosmetics.both.effect.particle", "FLAME");
        yaml.set("cosmetics.dust.slot", "trail"); yaml.set("cosmetics.dust.name", "D"); yaml.set("cosmetics.dust.icon", "STICK");
        yaml.set("cosmetics.dust.price", 5); yaml.set("cosmetics.dust.effect.particle", "DUST");
        yaml.set("cosmetics.tr-flame.slot", "trail"); yaml.set("cosmetics.tr-flame.name", "Dupe"); yaml.set("cosmetics.tr-flame.icon", "STICK");
        yaml.set("cosmetics.tr-flame.price", 5); yaml.set("cosmetics.tr-flame.effect.particle", "FLAME");
        var parsed = CosmeticCatalogue.parse(yaml.getConfigurationSection("cosmetics"), Set.of("tr-flame"));
        assertEquals(List.of("ok"), parsed.items().stream().map(Cosmetic::id).toList());
        assertEquals(4, parsed.problems().size());
    }
}
