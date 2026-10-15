package uk.co.playerready.pulsegames.games.readyplayerz;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
class ZCombatBalanceTest {
    @Test void earlyRoundDisabledVariantsDoNotBecomeExtraRunners(){
        RandomGenerator random=mock(RandomGenerator.class);int runners=0;
        for(int roll=0;roll<100;roll++){when(random.nextInt(100)).thenReturn(roll);if(ZCombatBalance.variant(1,false,random)==ZCombatBalance.Variant.RUNNER)runners++;}
        assertEquals(25,runners);
    }
    @Test
    void bossAndRoundScalingRemainBounded() {
        var low = ZCombatBalance.stats(1, ZCombatBalance.Variant.WALKER);
        for (var variant : ZCombatBalance.Variant.values()) {
            var high = ZCombatBalance.stats(Integer.MAX_VALUE, variant);
            assertTrue(high.health() <= 300);
            assertTrue(high.speed() <= .36);
            assertTrue(high.damage() <= 12);
            assertTrue(high.scale() <= 1.5);
        }
        assertTrue(ZCombatBalance.stats(10, ZCombatBalance.Variant.WALKER).health() > low.health());
        assertEquals(1.5, ZCombatBalance.stats(5, ZCombatBalance.Variant.BOSS).scale());
    }
    @Test
    void nukeNeedsMinimumRoundAndThreeRoundSeparation() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextDouble()).thenReturn(0.0);
        when(random.nextInt(100)).thenReturn(0);
        assertNotEquals("nuke", ZCombatBalance.drop(4, -100, 600, 0, .03, random));
        assertEquals("nuke", ZCombatBalance.drop(5, -100, 600, 0, .03, random));
        assertNotEquals("nuke", ZCombatBalance.drop(7, 5, 600, 0, .03, random));
        assertEquals("nuke", ZCombatBalance.drop(8, 5, 600, 0, .03, random));
    }
    @Test
    void cooldownChanceAndNukeWeightAreIndependent() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextDouble()).thenReturn(.029);
        when(random.nextInt(100)).thenReturn(2);
        assertNull(ZCombatBalance.drop(5, -100, 599, 600, .03, random));
        assertEquals("insta-kill", ZCombatBalance.drop(5, -100, 600, 600, .03, random));
        when(random.nextDouble()).thenReturn(.03);
        assertNull(ZCombatBalance.drop(5, -100, 600, 0, .03, random));
        assertNull(ZCombatBalance.drop(5, -100, 600, 0, Double.NaN, random));
    }
    @Test
    void bossSelectionDoesNotDependOnRandomRoll() {
        RandomGenerator random = mock(RandomGenerator.class);
        assertEquals(ZCombatBalance.Variant.BOSS, ZCombatBalance.variant(5, true, random));
        verifyNoInteractions(random);
    }
}
