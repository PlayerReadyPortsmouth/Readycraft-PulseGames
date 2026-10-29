package uk.co.playerready.pulsegames.games.bedwars;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopCatalogue;
import uk.co.playerready.pulsegames.games.bedwars.shop.ShopRules;
import uk.co.playerready.pulsegames.core.ui.shop.ShopRules.Status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Team upgrade tier maths, forge rates and the cooldown clock. */
class KitMathTest {
    private static final ShopCatalogue SHOP = ShopCatalogue.loadBundled();

    private static ShopCatalogue.Upgrade upgrade(String id) {
        return SHOP.upgrades().stream().filter(u -> u.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void upgradeTiersChargeEachLevelThenReportMaxed() {
        ShopCatalogue.Upgrade prot = upgrade("protection");
        assertEquals(4, prot.maxLevel());
        assertEquals(Status.NEED_MORE, ShopRules.upgradeVerdict(prot, 0, 4).status());
        assertEquals(1, ShopRules.upgradeVerdict(prot, 0, 4).shortfall());
        assertEquals(Status.CAN_BUY, ShopRules.upgradeVerdict(prot, 3, 30).status());
        assertEquals(Status.OWNED_THIS, ShopRules.upgradeVerdict(prot, 4, 99).status());
        assertEquals(2, upgrade("haste").maxLevel());
        assertEquals(4, upgrade("forge").maxLevel());
        assertEquals(1, upgrade("heal_pool").maxLevel());
    }

    @Test void everyUpgradeGetsDearerWithEachLevel() {
        for (ShopCatalogue.Upgrade u : SHOP.upgrades())
            for (int i = 1; i < u.costs().size(); i++) assertTrue(u.costs().get(i) > u.costs().get(i - 1), u.id());
    }

    @Test void forgeAddsHalfAgainPerLevelAndTheDropsAddUp() {
        assertEquals(1.0, ForgeRates.multiplier(0));
        assertEquals(2.0, ForgeRates.multiplier(2));
        assertEquals(3.0, ForgeRates.multiplier(4));
        assertEquals(3.0, ForgeRates.multiplier(9), "capped at level 4");
        for (int level = 0; level <= 4; level++) {
            int total = 0;
            for (int event = 0; event < 10; event++) total += 1 + ForgeRates.extraDrops(level, event);
            assertEquals((int) (10 * ForgeRates.multiplier(level)), total, "level " + level);
        }
    }

    @Test void cooldownsAreEnforcedPerPlayerAndItemAndExpire() {
        AtomicLong now = new AtomicLong(1_000);
        Cooldowns cd = new Cooldowns(now::get);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(cd.ready(a, "grappling_hook"));
        cd.start(a, "grappling_hook", 5);
        assertFalse(cd.ready(a, "grappling_hook"));
        assertEquals(5_000, cd.remaining(a, "grappling_hook"));
        assertTrue(cd.ready(b, "grappling_hook"), "another player");
        assertTrue(cd.ready(a, "ice_road"), "another item");
        now.addAndGet(4_999);
        assertFalse(cd.ready(a, "grappling_hook"));
        now.addAndGet(1);
        assertTrue(cd.ready(a, "grappling_hook"));
        cd.start(a, "x", 0);
        assertTrue(cd.ready(a, "x"), "zero seconds means no cooldown");
    }

    @Test void botKitChoicesComeAfterTheCoreOnes() {
        var cfg = BedwarsBotConfig.defaults();
        var calm = new BedwarsBotItems.Facts(20, 10, false, false, false, 0, 0, true);
        assertEquals(BedwarsBotItems.Use.POTION, BedwarsBotItems.choose(calm, new BedwarsBotItems.Kit(true, true, true), cfg));
        assertEquals(BedwarsBotItems.Use.BRIDGE_EGG, BedwarsBotItems.choose(
                new BedwarsBotItems.Facts(20, 14, false, false, false, 0, 0, true), new BedwarsBotItems.Kit(false, true, true), cfg));
        assertEquals(BedwarsBotItems.Use.POPUP_TOWER, BedwarsBotItems.choose(
                new BedwarsBotItems.Facts(6, 4, false, false, false, 0, 0, true), new BedwarsBotItems.Kit(false, true, true), cfg));
        assertEquals(BedwarsBotItems.Use.GOLDEN_APPLE, BedwarsBotItems.choose(
                new BedwarsBotItems.Facts(6, 4, true, false, false, 0, 0, true), new BedwarsBotItems.Kit(true, true, true), cfg));
        assertEquals(BedwarsBotItems.Use.NONE, BedwarsBotItems.choose(calm, cfg));
    }
}
