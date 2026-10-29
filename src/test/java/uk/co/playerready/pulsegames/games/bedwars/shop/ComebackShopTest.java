package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ComebackShopTest {
    @Test void comebackCategoryParses() {
        var c = ShopCatalogue.parse(new StringReader("""
                items:
                  - {id: cb_wool, category: comeback, material: WHITE_WOOL, amount: 16, currency: iron, cost: 2, name: Comeback Wool, team-colour: true}
                """));
        assertEquals(1, c.inCategory(ShopCatalogue.Category.COMEBACK).size());
        assertEquals(ShopCatalogue.Category.COMEBACK, c.byId("cb_wool").category());
    }

    @Test void bundledShelfIsWoolSwordAndArmourAtAboutHalfPrice() {
        var c = ShopCatalogue.loadBundled();
        var shelf = c.inCategory(ShopCatalogue.Category.COMEBACK);
        assertFalse(shelf.isEmpty());
        assertTrue(shelf.stream().anyMatch(i -> i.teamColour()), "wool");
        assertTrue(shelf.stream().anyMatch(i -> i.material().name().endsWith("_SWORD")), "sword");
        assertTrue(shelf.stream().anyMatch(i -> i.equip()), "armour");
        assertEquals(c.byId("wool").cost() / 2, c.byId("cb_wool").cost());
        assertEquals(c.byId("sword_stone").cost() / 2, c.byId("cb_sword_stone").cost());
        assertEquals(c.byId("armour_chain").cost() / 2, c.byId("cb_armour_chain").cost());
        assertEquals(c.byId("armour_iron").cost() / 2, c.byId("cb_armour_iron").cost());
        assertTrue(shelf.stream().noneMatch(ShopCatalogue.Item::quickBuy));
        assertEquals(Material.IRON_INGOT, c.byId("cb_wool").currency());
    }

    @Test void theShelfCostsLessThanTheNormalItems() {
        var c = ShopCatalogue.loadBundled();
        for (var cb : c.inCategory(ShopCatalogue.Category.COMEBACK)) {
            var normal = c.items().stream().filter(i -> i.category() != ShopCatalogue.Category.COMEBACK
                    && i.material() == cb.material() && i.currency() == cb.currency()).findFirst().orElseThrow();
            assertTrue(cb.cost() < normal.cost(), cb.id());
        }
    }
}
