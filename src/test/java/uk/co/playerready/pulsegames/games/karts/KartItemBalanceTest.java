package uk.co.playerready.pulsegames.games.karts;

import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import static uk.co.playerready.pulsegames.games.karts.PulseKartsGame.Item.*;
import static org.junit.jupiter.api.Assertions.*;

class KartItemBalanceTest {
    static final Set<PulseKartsGame.Item> ALL = EnumSet.noneOf(PulseKartsGame.Item.class);
    static EnumMap<PulseKartsGame.Item,Integer> counts(KartStyle style, KartItemBalance.Tier tier, Set<PulseKartsGame.Item> unavailable) {
        var counts = new EnumMap<PulseKartsGame.Item,Integer>(PulseKartsGame.Item.class);
        int total = KartItemBalance.total(style, tier, unavailable);
        for (int roll = 0; roll < total; roll++) counts.merge(KartItemBalance.choose(style, roll, tier, unavailable), 1, Integer::sum);
        return counts;
    }
    @Test void everyTierSumsToOneHundredAndMatchesTheDesignTable() {
        for (var tier : KartItemBalance.Tier.values()) assertEquals(100, KartItemBalance.total(KartStyle.BALANCED, tier, ALL));
        var leader = counts(KartStyle.BALANCED, KartItemBalance.Tier.LEADER, ALL);
        assertEquals(30, leader.get(BANANA)); assertEquals(25, leader.get(SHELL)); assertEquals(20, leader.get(TURBO));
        var back = counts(KartStyle.BALANCED, KartItemBalance.Tier.BACK, ALL);
        assertEquals(25, back.get(BULLET)); assertEquals(20, back.get(STAR)); assertEquals(15, back.get(LIGHTNING));
    }
    @Test void leadersNeverGetStrongItemsAndBackmarkersNeverGetBananas() {
        for (var style : KartStyle.values()) {
            var leader = counts(style, KartItemBalance.Tier.LEADER, ALL);
            for (var strong : new PulseKartsGame.Item[]{STAR, BULLET, LIGHTNING}) assertNull(leader.get(strong), style + " " + strong);
            assertNull(counts(style, KartItemBalance.Tier.BACK, ALL).get(BANANA));
        }
    }
    @Test void strongerItemsBecomeMoreLikelyTheFurtherBackYouAre() {
        double[] strong = new double[3];
        for (var tier : KartItemBalance.Tier.values()) {
            var c = counts(KartStyle.BALANCED, tier, ALL);
            strong[tier.ordinal()] = c.getOrDefault(STAR, 0) + c.getOrDefault(BULLET, 0) + c.getOrDefault(LIGHTNING, 0);
        }
        assertTrue(strong[0] < strong[1] && strong[1] < strong[2], java.util.Arrays.toString(strong));
    }
    @Test void tricksterDoublesTurboAndLightningOnly() {
        var normal = counts(KartStyle.BALANCED, KartItemBalance.Tier.BACK, ALL);
        var trick = counts(KartStyle.TRICKSTER, KartItemBalance.Tier.BACK, ALL);
        assertEquals(normal.get(TURBO) * 2, trick.get(TURBO)); assertEquals(normal.get(LIGHTNING) * 2, trick.get(LIGHTNING));
        assertEquals(normal.get(SHELL), trick.get(SHELL));
        assertNull(counts(KartStyle.TRICKSTER, KartItemBalance.Tier.LEADER, ALL).get(LIGHTNING), "leaders never get lightning");
    }
    @Test void unavailableItemsRenormaliseInsteadOfDisappearingFromTheRoll() {
        var noBullet = EnumSet.of(BULLET);
        var back = counts(KartStyle.BALANCED, KartItemBalance.Tier.BACK, noBullet);
        assertNull(back.get(BULLET)); assertEquals(75, KartItemBalance.total(KartStyle.BALANCED, KartItemBalance.Tier.BACK, noBullet));
        assertEquals(75, back.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(TURBO, KartItemBalance.choose(KartStyle.BALANCED, 7, KartItemBalance.Tier.BACK, EnumSet.allOf(PulseKartsGame.Item.class)));
    }
    @Test void tiersFollowPlaceFractionAndLoneRacersGetTheMiddleTable() {
        assertEquals(KartItemBalance.Tier.MIDDLE, KartItemBalance.tier(1, 1));
        assertEquals(KartItemBalance.Tier.LEADER, KartItemBalance.tier(1, 2));
        assertEquals(KartItemBalance.Tier.BACK, KartItemBalance.tier(2, 2));
        int leaders = 0, back = 0;
        for (int place = 1; place <= 12; place++) {
            var tier = KartItemBalance.tier(place, 12);
            if (tier == KartItemBalance.Tier.LEADER) { leaders++; assertTrue(place <= 4); }
            if (tier == KartItemBalance.Tier.BACK) { back++; assertTrue(place >= 9); }
        }
        assertEquals(4, leaders); assertEquals(4, back);
        assertEquals(KartItemBalance.Tier.BACK, KartItemBalance.tier(99, 4), "out-of-range places clamp");
    }
    @Test void signedRollsNormaliseWithoutChangingWeights() {
        for (var tier : KartItemBalance.Tier.values()) for (int roll = -300; roll < 300; roll++)
            assertEquals(KartItemBalance.choose(KartStyle.LIGHT, Math.floorMod(roll, 100), tier, ALL), KartItemBalance.choose(KartStyle.LIGHT, roll, tier, ALL));
        assertNotNull(KartItemBalance.choose(KartStyle.HEAVY, Integer.MIN_VALUE, KartItemBalance.Tier.MIDDLE, ALL));
    }
}
