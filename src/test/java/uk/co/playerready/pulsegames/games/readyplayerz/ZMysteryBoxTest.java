package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZMysteryBoxTest {
    @Test void priceRisesTenPercentPerUseAndStartsAt950() {
        assertEquals(950, ZMysteryBox.price(0));
        assertEquals(1045, ZMysteryBox.price(1));
        assertEquals(1150, ZMysteryBox.price(2));
        assertEquals(950, ZMysteryBox.price(-3));
    }
    @Test void rollFollowsWeightsAtBothEnds() {
        var r = mock(RandomGenerator.class);
        when(r.nextInt(anyInt())).thenReturn(0);
        assertEquals(ZMysteryBox.Prize.STONE_SHARP, ZMysteryBox.roll(r));
        int total = ZMysteryBox.prizes().stream().mapToInt(p -> p.weight).sum();
        when(r.nextInt(anyInt())).thenReturn(total - 1);
        assertEquals(ZMysteryBox.Prize.REGEN, ZMysteryBox.roll(r));
    }
    @Test void onlyWeaponsArePunchable() {
        assertTrue(ZMysteryBox.punchable(Material.DIAMOND_SWORD));
        assertTrue(ZMysteryBox.ranged(Material.BOW));
        assertFalse(ZMysteryBox.punchable(Material.EMERALD));
    }
}
