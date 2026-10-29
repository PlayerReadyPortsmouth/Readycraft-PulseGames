package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import java.util.Set;

/** Which tool breaks a block fastest in Bedwars; pure so it can be tested without a server. */
final class BedwarsBotTools {
    private BedwarsBotTools() {}
    private static final Material[] PICKAXES = {Material.DIAMOND_PICKAXE, Material.IRON_PICKAXE, Material.STONE_PICKAXE, Material.WOODEN_PICKAXE};
    private static final Material[] AXES = {Material.IRON_AXE, Material.STONE_AXE, Material.WOODEN_AXE};

    /** Best held tool for the block, or null to keep whatever is held (beds, nothing suitable owned). */
    static Material preferred(Material block, Set<Material> held) {
        String name = block.name();
        if (name.endsWith("_WOOL")) return held.contains(Material.SHEARS) ? Material.SHEARS : null;
        if (name.endsWith("_PLANKS") || name.endsWith("_LOG")) return first(AXES, held);
        if (name.endsWith("_BED")) return null;
        return first(PICKAXES, held);
    }
    private static Material first(Material[] order, Set<Material> held) {
        for (Material m : order) if (held.contains(m)) return m;
        return null;
    }
}
