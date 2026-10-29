package uk.co.playerready.pulsegames.core.ui.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Status.*;

class ShopKitTest {
    private static final List<Material> SWORDS = List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD);

    @Test void affordabilityHasExactShortfall() {
        assertEquals(CAN_BUY, ShopRules.afford(50, 50).status());
        var poor = ShopRules.afford(47, 50);
        assertEquals(NEED_MORE, poor.status());
        assertEquals(3, poor.shortfall());
        assertFalse(poor.buyable());
        assertEquals("Need 3 more points", ShopRules.needMore(poor.shortfall(), "points"));
    }

    @Test void betterOrEqualTierIsNeverOfferedAgain() {
        int held = ShopRules.ownedTier(SWORDS, Set.of(Material.STONE_SWORD, Material.APPLE));
        assertEquals(1, held);
        assertEquals(OWNED_BETTER, ShopRules.tiered(held, 0, 99, 5).status());
        assertEquals(OWNED_THIS, ShopRules.tiered(held, 1, 99, 5).status());
        assertEquals(CAN_BUY, ShopRules.tiered(held, 2, 99, 5).status());
        assertEquals(NEED_MORE, ShopRules.tiered(held, 2, 1, 5).status());
        assertEquals(-1, ShopRules.ownedTier(SWORDS, Set.of(Material.BOW)));
    }

    @Test void buyingATierReplacesEverythingBelowIt() {
        assertEquals(Set.of(Material.WOODEN_SWORD, Material.STONE_SWORD), ShopRules.lowerTiers(SWORDS, 2));
        assertTrue(ShopRules.lowerTiers(SWORDS, 0).isEmpty());
    }

    @Test void layoutKeepsTabsQuickRowPageAndFooterApart() {
        assertEquals(9, ShopLayout.quickSlot(0));
        assertEquals(16, ShopLayout.quickSlot(ShopLayout.QUICK_CAPACITY - 1));
        assertEquals(44, ShopLayout.pageSlot(ShopLayout.PAGE_CAPACITY - 1));
        assertEquals(28, ShopLayout.spacedSlot(0));
        assertEquals(40, ShopLayout.spacedSlot(6));
        assertTrue(ShopLayout.HINT >= ShopLayout.PAGE && ShopLayout.HINT <= 44);
        assertEquals(53, ShopLayout.CLOSE);
    }

    @Test void feedbackSaysWhatIsMissingInPlainWords() {
        assertEquals("<red>Need 3 more iron for Wool.", ShopFeedback.message(ShopFeedback.Outcome.NEED_MORE, 3, "iron", "Wool"));
        assertEquals("<red>Need 1 more gold for X.", ShopFeedback.message(ShopFeedback.Outcome.NEED_MORE, 0, "gold", "X"));
        assertNull(ShopFeedback.message(ShopFeedback.Outcome.OK, 0, "x", "x"));
        assertNull(ShopFeedback.message(ShopFeedback.Outcome.OWNED, 0, "x", "x"));
        assertTrue(ShopFeedback.message(ShopFeedback.Outcome.NO_ROOM, 0, "x", "x").contains("bag is full"));
    }
}
