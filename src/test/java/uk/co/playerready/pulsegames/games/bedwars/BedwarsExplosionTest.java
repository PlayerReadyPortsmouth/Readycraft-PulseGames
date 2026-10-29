package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Standard Bedwars: fireballs and TNT never break a bed (anyone's, including the firer's); only mining does. */
class BedwarsExplosionTest {
    private static Block block(BedwarsBotLifecycleTest.Fixture f, Material type, int x, int y, int z) {
        Block b = mock(Block.class);
        when(b.getType()).thenReturn(type);
        when(b.getLocation()).thenReturn(new Location(f.world, x, y, z));
        return b;
    }

    @Test void explosionNeverBreaksABedAndTheBedStaysAlive() throws Exception {
        var f = new BedwarsBotLifecycleTest.Fixture();
        for (Material bed : new Material[]{Material.RED_BED, Material.BLUE_BED, Material.WHITE_BED}) {
            assertFalse(f.logic.canExplode(block(f, bed, 0, 64, 2)), bed + " must survive an explosion");
            assertFalse(f.logic.canExplode(block(f, bed, 40, 64, 40)), bed + " away from a region must survive too");
        }
        assertTrue(f.alive.get(0), "an explosion must not mark the bed destroyed");
        verify(f.game, never()).broadcast(anyString());
    }

    @Test void explosionStillBreaksPlacedBlocksButNotTheMap() throws Exception {
        var f = new BedwarsBotLifecycleTest.Fixture();
        Block wool = block(f, Material.RED_WOOL, 5, 64, 5);
        f.logic.canPlace(mock(Player.class), wool);
        assertTrue(f.logic.canExplode(wool));
        assertFalse(f.logic.canExplode(block(f, Material.END_STONE, 6, 64, 6)));
    }
}
