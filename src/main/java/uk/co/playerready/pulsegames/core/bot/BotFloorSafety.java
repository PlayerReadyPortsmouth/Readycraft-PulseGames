package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Material;

/** Conservative full-cube walking surfaces; bounding envelopes alone do not prove stair or trapdoor support. */
public final class BotFloorSafety {
    private BotFloorSafety() {}
    public static boolean fullCube(Material material) {
        if (material == null) return false;
        String name=material.name();
        if(name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_WOOL") || name.endsWith("_CONCRETE") || name.endsWith("_TERRACOTTA")) return true;
        return switch(material) {
            case STONE, SMOOTH_STONE, COBBLESTONE, MOSSY_COBBLESTONE, STONE_BRICKS, MOSSY_STONE_BRICKS,
                 CRACKED_STONE_BRICKS, CHISELED_STONE_BRICKS, DEEPSLATE, COBBLED_DEEPSLATE, POLISHED_DEEPSLATE,
                 DEEPSLATE_BRICKS, DEEPSLATE_TILES, GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL,
                 MYCELIUM, BEDROCK, QUARTZ_BLOCK, SMOOTH_QUARTZ, QUARTZ_BRICKS, QUARTZ_PILLAR,
                 TERRACOTTA, BRICKS, OBSIDIAN, CRYING_OBSIDIAN, NETHERRACK, END_STONE, END_STONE_BRICKS,
                 DIORITE, GRANITE, ANDESITE, POLISHED_DIORITE, POLISHED_GRANITE, POLISHED_ANDESITE,
                 SANDSTONE, SMOOTH_SANDSTONE, RED_SANDSTONE, SMOOTH_RED_SANDSTONE,
                 GLASS, TINTED_GLASS, ICE, PACKED_ICE, BLUE_ICE -> true;
            default -> false;
        };
    }
}
