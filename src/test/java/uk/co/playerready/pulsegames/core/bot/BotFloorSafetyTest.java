package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotFloorSafetyTest {
    @Test void everyFloorTheLiveMapsUseIsStandable() {
        for (Material m : List.of(Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK,
                Material.CUT_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER, Material.CALCITE, Material.SMOOTH_BASALT,
                Material.WHITE_WOOL, Material.LIGHT_GRAY_WOOL, Material.WHITE_TERRACOTTA, Material.YELLOW_CONCRETE,
                Material.GLASS, Material.RED_STAINED_GLASS, Material.BARRIER, Material.SMOOTH_SANDSTONE, Material.QUARTZ_PILLAR,
                Material.GRASS_BLOCK, Material.SNOW_BLOCK, Material.MUD_BRICKS, Material.END_STONE))
            assertTrue(BotFloorSafety.fullCube(m), m + " should hold a bot");
    }

    @Test void hazardsAndPartialShapesAreRefused() {
        for (Material m : List.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE,
                Material.LAVA, Material.POWDER_SNOW, Material.POINTED_DRIPSTONE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
                Material.AIR, Material.OAK_SLAB, Material.OAK_STAIRS, Material.OAK_TRAPDOOR, Material.OAK_FENCE,
                Material.COBBLESTONE_WALL, Material.WHITE_CARPET, Material.SNOW, Material.SOUL_SAND, Material.CHEST))
            assertFalse(BotFloorSafety.fullCube(m), m + " must not hold a bot");
    }

    @Test void collisionShapeTopDecidesNotTheName() {
        assertTrue(BotFloorSafety.fullTop(List.of(new BoundingBox(0, 0, 0, 1, 1, 1))));
        assertTrue(BotFloorSafety.fullTop(List.of(new BoundingBox(0, .5, 0, 1, 1, 1)))); // top slab
        assertFalse(BotFloorSafety.fullTop(List.of(new BoundingBox(0, 0, 0, 1, .5, 1)))); // bottom slab
        assertFalse(BotFloorSafety.fullTop(List.of(new BoundingBox(.375, 0, .375, .625, 1.5, .625)))); // fence post
        assertFalse(BotFloorSafety.fullTop(List.of()));
    }

    @Test void footingUsesTheShapeWhenTheWorldGivesOne() {
        Block block = mock(Block.class); VoxelShape shape = mock(VoxelShape.class);
        when(block.getType()).thenReturn(Material.IRON_BLOCK);
        when(block.getCollisionShape()).thenReturn(shape);
        when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, 1, 1)));
        assertTrue(BotFloorSafety.footing(block));
        when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, .5, 1)));
        assertFalse(BotFloorSafety.footing(block));
        when(block.getType()).thenReturn(Material.MAGMA_BLOCK);
        when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, 1, 1)));
        assertFalse(BotFloorSafety.footing(block));
    }

    @Test void footingFallsBackToTheMaterialWithoutAShape() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CALCITE);
        assertTrue(BotFloorSafety.footing(block));
    }
}
