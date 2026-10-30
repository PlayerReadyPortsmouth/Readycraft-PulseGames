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
        shipped().items().stream().filter(c -> c.category() != Cosmetic.Category.NAME_STYLE).forEach(c -> assertNotNull(c.effect(), c.id()));
    }

    @Test void everyFifthLevelHasExactlyOneReward() {
        var levels = shipped().items().stream().filter(Cosmetic::levelUnlock)
                .filter(c -> c.category() != Cosmetic.Category.NAME_STYLE).map(Cosmetic::level).sorted().toList();
        for (int level = XpRules.UNLOCK_EVERY; level <= XpRules.MAX_LEVEL; level += XpRules.UNLOCK_EVERY)
            assertEquals(1, java.util.Collections.frequency(levels, level), "level " + level);
        assertEquals(20, levels.size());
    }

    @Test void sixteenNameStylesWithUniqueIncreasingLevels() {
        var styles = shipped().items().stream().filter(c -> c.category() == Cosmetic.Category.NAME_STYLE).toList();
        assertEquals(16, styles.size());
        var ids = styles.stream().map(Cosmetic::nameStyleId).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("red", "orange", "yellow", "green", "aqua", "blue", "purple", "pink", "sunset", "ocean",
                "forest", "candy", "rainbow", "shimmer", "pulse", "sparkle"), ids);
        var levels = styles.stream().map(Cosmetic::level).toList();
        assertEquals(16, new HashSet<>(levels).size());
        assertEquals(levels.stream().sorted().toList(), levels, "listed in unlock order");
        assertTrue(levels.stream().allMatch(l -> l >= 1 && l <= XpRules.MAX_LEVEL));
    }

    @Test void nameStyleNeedsAnNsIdAndAValidLevel() {
        var yaml = new YamlConfiguration();
        yaml.set("cosmetics.bad.slot", "name_style"); yaml.set("cosmetics.bad.name", "B"); yaml.set("cosmetics.bad.icon", "STICK");
        yaml.set("cosmetics.bad.level", 3);
        yaml.set("cosmetics.ns-x.slot", "name_style"); yaml.set("cosmetics.ns-x.name", "X"); yaml.set("cosmetics.ns-x.icon", "STICK");
        yaml.set("cosmetics.ns-x.level", 101);
        yaml.set("cosmetics.ns-a.slot", "name_style"); yaml.set("cosmetics.ns-a.name", "A"); yaml.set("cosmetics.ns-a.icon", "STICK");
        yaml.set("cosmetics.ns-a.level", 3);
        yaml.set("cosmetics.ns-b.slot", "name_style"); yaml.set("cosmetics.ns-b.name", "B"); yaml.set("cosmetics.ns-b.icon", "STICK");
        yaml.set("cosmetics.ns-b.level", 3);
        var parsed = CosmeticCatalogue.parse(yaml.getConfigurationSection("cosmetics"), Set.of());
        assertEquals(List.of("ns-a"), parsed.items().stream().map(Cosmetic::id).toList());
        assertEquals(3, parsed.problems().size());
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
