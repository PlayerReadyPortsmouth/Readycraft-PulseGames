package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.Set;

/**
 * What a bot may stand on. The question is "does this block have a full-width solid top at its own height", asked of
 * the block's collision shape when the world can answer, and of its name (by exclusion, never by a list of known
 * good blocks) when only the material is known. Hazards are always refused.
 */
public final class BotFloorSafety {
    private BotFloorSafety() {}

    /** Blocks that hurt or trap a bot, whatever their shape. */
    public static final Set<Material> HAZARDS = Set.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE,
            Material.COBWEB, Material.TNT);

    /** Name fragments of blocks whose top is not a full 1x1 plane at full height (or is not a floor at all). */
    private static final String[] PARTIAL = {"STAIRS", "_SLAB", "FENCE", "_WALL", "TRAPDOOR", "_DOOR", "_PANE", "IRON_BARS", "_BED",
            "CARPET", "SIGN", "BANNER", "_HEAD", "_SKULL", "CANDLE", "BUTTON", "PRESSURE_PLATE", "RAIL", "TORCH", "LANTERN",
            "CHAIN", "LADDER", "VINE", "POT", "SAPLING", "FERN", "CAKE", "ANVIL", "CAULDRON", "HOPPER",
            "BREWING_STAND", "ENCHANTING_TABLE", "END_PORTAL", "DRAGON_EGG", "FARMLAND", "DIRT_PATH", "SOUL_SAND", 
            "HONEY_BLOCK", "COMPOSTER", "LECTERN", "GRINDSTONE", "STONECUTTER", "SCAFFOLDING", "LIGHTNING_ROD",
            "END_ROD", "AMETHYST_CLUSTER", "AMETHYST_BUD", "CONDUIT", "BELL", "PICKLE", "TURTLE_EGG", "FROGSPAWN", "DRIPLEAF",
            "DAYLIGHT_DETECTOR", "REPEATER", "COMPARATOR", "REDSTONE_WIRE", "TRIPWIRE", "PORTAL", 
            "STRUCTURE_VOID", "DEAD_BUSH", "FLOWER", "ROSE", "TULIP", "LILY", "MOSS_CARPET", "SEAGRASS", "KELP"};

    /** Whole names that would be wrongly caught by a fragment (GRASS_BLOCK, SNOW_BLOCK, LIGHT_GRAY_WOOL, MUD_BRICKS). */
    private static final Set<String> PARTIAL_EXACT = Set.of("SHORT_GRASS", "TALL_GRASS", "SNOW", "MUD", "LIGHT", "WATER",
            "AIR", "CAVE_AIR", "VOID_AIR", "CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "LEVER", "BAMBOO", "BAMBOO_SAPLING");

    /** Material-only fallback: a block that is not air, not a hazard and not a known partial shape. */
    public static boolean fullCube(Material material) {
        if (material == null || HAZARDS.contains(material) || !isBlock(material)) return false;
        String name = material.name();
        if (PARTIAL_EXACT.contains(name)) return false;
        for (String part : PARTIAL) if (name.contains(part)) return false;
        return true;
    }

    /** Item-only materials never hold a floor; if the registry is not up (plain unit tests) every name is judged on its shape words alone. */
    private static boolean isBlock(Material material) {
        try { return material.isBlock(); } catch (RuntimeException | LinkageError registryNotReady) { return true; }
    }

    /** True when some box reaches the top of the cell and spans the whole of it, so a body may stand anywhere on it. */
    public static boolean fullTop(Collection<BoundingBox> localBoxes) {
        if (localBoxes == null) return false;
        for (BoundingBox box : localBoxes)
            if (box.getMaxY() >= .999 && box.getMinX() <= .001 && box.getMinZ() <= .001
                    && box.getMaxX() >= .999 && box.getMaxZ() >= .999) return true;
        return false;
    }

    /** The block's collision shape decides when the server provides one; otherwise its material does. */
    public static boolean footing(Block block) {
        if (block == null || HAZARDS.contains(block.getType()) || block.isLiquid()) return false;
        var shape = block.getCollisionShape();
        if (shape == null) return fullCube(block.getType());
        return fullTop(shape.getBoundingBoxes());
    }
}
