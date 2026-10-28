package uk.co.playerready.pulsegames.games.buildbattle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildBattleFenceTest {
    private static Location at(int x, int y, int z) {
        Location l = mock(Location.class);
        when(l.getBlockX()).thenReturn(x);
        when(l.getBlockY()).thenReturn(y);
        when(l.getBlockZ()).thenReturn(z);
        return l;
    }

    @Test void creativePaletteHazardsAreBanned() {
        for (Material m : new Material[]{Material.LAVA_BUCKET, Material.WATER_BUCKET, Material.AXOLOTL_BUCKET,
                Material.WARDEN_SPAWN_EGG, Material.ZOMBIE_SPAWN_EGG, Material.OAK_SIGN, Material.CHERRY_HANGING_SIGN,
                Material.WRITABLE_BOOK, Material.WRITTEN_BOOK, Material.NAME_TAG, Material.FLINT_AND_STEEL,
                Material.FIRE_CHARGE, Material.TNT, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.PAINTING,
                Material.TNT_MINECART, Material.COMMAND_BLOCK, Material.SPAWNER}) {
            assertTrue(BuildBattleRules.banned(m), m.name());
        }
        for (Material m : new Material[]{Material.STONE, Material.OAK_PLANKS, Material.GLASS, Material.WHITE_WOOL,
                Material.PISTON, Material.OAK_DOOR, Material.TORCH}) {
            assertFalse(BuildBattleRules.banned(m), m.name());
        }
    }

    @Test void pistonsAndLiquidMayOnlyActInsideOnePlot() {
        var game = new BuildBattleGame(mock(GameInstance.class));
        try {
            var f = BuildBattleGame.class.getDeclaredField("plots");
            f.setAccessible(true);
            f.set(game, List.of(new Cuboid(0, 0, 0, 9, 9, 9), new Cuboid(20, 0, 0, 29, 9, 9)));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        assertTrue(game.fenceAllows(at(1, 1, 1), at(2, 1, 1)));
        assertFalse(game.fenceAllows(at(9, 1, 1), at(10, 1, 1)), "edge of plot into the gap");
        assertFalse(game.fenceAllows(at(9, 1, 1), at(20, 1, 1)), "across plots");
        assertFalse(game.fenceAllows(at(40, 1, 1), at(41, 1, 1)), "outside every plot");
        assertTrue(game.fenced());
        assertFalse(game.environmentalDamage());
    }
}
