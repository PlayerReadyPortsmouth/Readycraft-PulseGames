package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShopCatalogueTest {
    private static final String MINI = """
            groups:
              sword: [WOODEN_SWORD, STONE_SWORD, IRON_SWORD]
            items:
              - {id: a, category: melee, material: STONE_SWORD, currency: iron, cost: 10, name: Stone Sword, group: sword}
              - {id: b, category: melee, material: IRON_SWORD, currency: gold, cost: 7, name: Iron Sword, group: sword, quick-buy: true}
            upgrades:
              - {id: p, effect: protection, icon: DIAMOND_CHESTPLATE, name: Armour, costs: [8, 16]}
            """;

    private static ShopCatalogue parse(String yaml) { return ShopCatalogue.parse(new StringReader(yaml)); }

    @Test void bundledShopParsesWithinTheMenuLimits() {
        ShopCatalogue c = ShopCatalogue.loadBundled();
        assertTrue(c.quickBuy().size() <= ShopCatalogue.QUICK_CAPACITY && !c.quickBuy().isEmpty());
        for (ShopCatalogue.Category cat : ShopCatalogue.Category.values())
            assertTrue(c.inCategory(cat).size() <= ShopCatalogue.PAGE_CAPACITY);
        assertTrue(c.items().stream().filter(i -> i.equip()).allMatch(i -> i.group().equals("armour")));
        assertFalse(c.upgrades().isEmpty());
    }

    @Test void tiersComeFromPositionInTheChainAndLowerTiersAreReplaced() {
        ShopCatalogue c = parse(MINI);
        var iron = c.byMaterial(Material.IRON_SWORD);
        assertEquals(2, iron.tier());
        assertEquals(Material.GOLD_INGOT, iron.currency());
        assertEquals(Set.of(Material.WOODEN_SWORD, Material.STONE_SWORD), c.lowerTiers(iron));
        assertEquals(1, c.ownedTier(iron, List.of(Material.STONE_SWORD, Material.DIRT)));
        assertEquals(List.of(8, 16), c.upgrades().get(0).costs());
    }

    @Test void mistakesAreRejectedWithAReason() {
        assertThrows(IllegalArgumentException.class, () -> parse(MINI.replace("STONE_SWORD, currency", "NOPE, currency")));
        assertThrows(IllegalArgumentException.class, () -> parse(MINI.replace("cost: 10", "cost: 0")));
        assertThrows(IllegalArgumentException.class, () -> parse(MINI.replace("currency: iron", "currency: emerald")));
        assertThrows(IllegalArgumentException.class, () -> parse(MINI.replace("group: sword}", "group: axe}")));
        assertThrows(IllegalArgumentException.class, () -> parse("items: []"));
    }

    @Test void tooManyItemsForOneShelfIsRejected() {
        StringBuilder yaml = new StringBuilder("items:\n");
        for (int i = 0; i < 19; i++) yaml.append("  - {id: i").append(i).append(", category: blocks, material: DIRT, currency: iron, cost: 1, name: D}\n");
        assertThrows(IllegalArgumentException.class, () -> parse(yaml.toString()));
    }
}
