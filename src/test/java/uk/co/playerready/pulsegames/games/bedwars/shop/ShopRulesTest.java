package uk.co.playerready.pulsegames.games.bedwars.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Status.*;

class ShopRulesTest {
    private final ShopCatalogue c = ShopCatalogue.loadBundled();
    private ShopCatalogue.Item item(String id) { return c.items().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(); }

    @Test void affordabilityCountsTheShortfall() {
        assertEquals(CAN_BUY, ShopRules.verdict(c, item("wool"), 4, List.of()).status());
        var poor = ShopRules.verdict(c, item("sword_stone"), 7, List.of(Material.WOODEN_SWORD));
        assertEquals(NEED_MORE, poor.status());
        assertEquals(3, poor.shortfall());
        assertEquals("Need 3 more iron", ShopRules.needMore(3, Material.IRON_INGOT));
        assertEquals("Need 1 more diamond", ShopRules.needMore(1, Material.DIAMOND));
    }

    @Test void betterTierBlocksALowerOneAndSameTierIsOwned() {
        List<Material> holdsIron = List.of(Material.IRON_SWORD);
        assertEquals(OWNED_BETTER, ShopRules.verdict(c, item("sword_stone"), 99, holdsIron).status());
        assertEquals(OWNED_THIS, ShopRules.verdict(c, item("sword_iron"), 99, holdsIron).status());
        assertEquals(CAN_BUY, ShopRules.verdict(c, item("sword_diamond"), 99, holdsIron).status());
        assertEquals(OWNED_BETTER, ShopRules.verdict(c, item("armour_chain"), 99, List.of(Material.IRON_CHESTPLATE)).status());
    }

    @Test void uniqueItemsAreOnlyBoughtOnce() {
        assertEquals(OWNED_THIS, ShopRules.verdict(c, item("bow"), 99, List.of(Material.BOW)).status());
        assertEquals(CAN_BUY, ShopRules.verdict(c, item("arrows"), 2, List.of(Material.ARROW)).status());
    }

    @Test void armourComesAsAMatchingSet() {
        assertArrayEquals(new Material[]{Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
                ShopRules.armourSet(Material.IRON_CHESTPLATE));
    }

    @Test void teamUpgradeLevelsCostEachStepAndStopAtTheTop() {
        var protection = c.upgrades().stream().filter(u -> u.effect() == ShopCatalogue.Effect.PROTECTION).findFirst().orElseThrow();
        assertEquals(CAN_BUY, ShopRules.upgradeVerdict(protection, 0, 8).status());
        assertEquals(8, ShopRules.upgradeVerdict(protection, 1, 8).shortfall());
        assertEquals(OWNED_THIS, ShopRules.upgradeVerdict(protection, protection.maxLevel(), 99).status());
    }
}
