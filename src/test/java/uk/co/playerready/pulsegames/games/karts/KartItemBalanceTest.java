package uk.co.playerready.pulsegames.games.karts;

import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

class KartItemBalanceTest {
    @Test void trailingDistributionKeepsLightningOccasional() {
        for (KartStyle style : KartStyle.values()) {
            int[] expected = style == KartStyle.TRICKSTER ? new int[]{20,65,15} : new int[]{35,55,10};
            assertDistribution(style,true,expected);
        }
    }
    @Test void leadingAndTiedRacersNeverReceiveLightning() {
        for (KartStyle style : KartStyle.values()) {
            int[] expected = style == KartStyle.TRICKSTER ? new int[]{40,60,0} : new int[]{60,40,0};
            assertDistribution(style,false,expected);
        }
    }
    @Test void signedRollsNormalizeWithoutChangingWeights() {
        for (KartStyle style : KartStyle.values()) for (boolean behind : new boolean[]{false,true}) {
            for (int roll=-200;roll<200;roll++)
                assertEquals(KartItemBalance.choose(style,Math.floorMod(roll,100),behind),KartItemBalance.choose(style,roll,behind));
            assertEquals(KartItemBalance.choose(style,Math.floorMod(Integer.MIN_VALUE,100),behind),KartItemBalance.choose(style,Integer.MIN_VALUE,behind));
            assertEquals(KartItemBalance.choose(style,Math.floorMod(Integer.MAX_VALUE,100),behind),KartItemBalance.choose(style,Integer.MAX_VALUE,behind));
        }
    }
    private static void assertDistribution(KartStyle style,boolean behind,int[] expected) {
        var counts=new EnumMap<PulseKartsGame.Item,Integer>(PulseKartsGame.Item.class);
        for(int roll=0;roll<100;roll++)counts.merge(KartItemBalance.choose(style,roll,behind),1,Integer::sum);
        assertEquals(expected[0],counts.getOrDefault(PulseKartsGame.Item.SHELL,0));
        assertEquals(expected[1],counts.getOrDefault(PulseKartsGame.Item.TURBO,0));
        assertEquals(expected[2],counts.getOrDefault(PulseKartsGame.Item.LIGHTNING,0));
    }
}
