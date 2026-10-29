package uk.co.playerready.pulsegames.core.economy;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.ui.Paginator;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ShopRulesTest {
    @Test void windowGrowsWithTheCategoryAndCapsAtSixRows() {
        assertEquals(2, ShopRules.rows(0));
        assertEquals(2, ShopRules.rows(5));
        assertEquals(2, ShopRules.rows(9));
        assertEquals(3, ShopRules.rows(10));
        assertEquals(6, ShopRules.rows(45));
        assertEquals(6, ShopRules.rows(200));
        assertEquals(45, ShopRules.perPage(6));
    }

    @Test void theTwentySeventhItemIsNoLongerDropped() {
        var all = IntStream.range(0, 27).boxed().toList();
        int per = ShopRules.perPage(ShopRules.rows(all.size()));
        assertEquals(27, Paginator.page(all, per, 0).items().size());
        var big = IntStream.range(0, 50).boxed().toList();
        int perBig = ShopRules.perPage(ShopRules.rows(big.size()));
        assertEquals(5, Paginator.page(big, perBig, 1).items().size());
    }

    @Test void confirmsAtOrAboveThePriceAndNeverShowsNegativeBalance() {
        assertFalse(ShopRules.needsConfirm(499, 500));
        assertTrue(ShopRules.needsConfirm(500, 500));
        assertTrue(ShopRules.needsConfirm(900, 500));
        assertEquals(150, ShopRules.balanceAfter(400, 250));
        assertEquals(0, ShopRules.balanceAfter(100, 250));
    }
}
