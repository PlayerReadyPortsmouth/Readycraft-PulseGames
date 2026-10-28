package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;

/**
 * Optional arcade features read from an arena. A map with none of these entries yields {@link #NONE}, so
 * existing maps (Copper Canyon Raceway) are untouched. Parsing is pure: no world access.
 */
final class KartArcadeConfig {
    static final KartArcadeConfig NONE = new KartArcadeConfig(List.of(), Set.of());

    private final List<Cuboid> boostPanels;
    private final Set<Material> boostBlocks;

    private KartArcadeConfig(List<Cuboid> boostPanels, Set<Material> boostBlocks) {
        this.boostPanels = List.copyOf(boostPanels);
        this.boostBlocks = Set.copyOf(boostBlocks);
    }

    List<Cuboid> boostPanels() { return boostPanels; }
    Set<Material> boostBlocks() { return boostBlocks; }

    /** Throws {@link IllegalArgumentException} with a builder-facing message for a malformed optional entry. */
    static KartArcadeConfig load(Map<String, Object> settings, Map<String, Cuboid> regions) {
        return new KartArcadeConfig(numberedRegions(regions, "boost"), blockList(settings.get("boost-block")));
    }

    static String validationError(Map<String, Object> settings, Map<String, Cuboid> regions) {
        try { load(settings, regions); return null; }
        catch (IllegalArgumentException problem) { return problem.getMessage(); }
    }

    /** Regions named prefix1, prefix2, ... in order; a gap is an error so a typo cannot silently drop a panel. */
    static List<Cuboid> numberedRegions(Map<String, Cuboid> regions, String prefix) {
        SortedMap<Integer, Cuboid> found = new TreeMap<>();
        regions.forEach((name, box) -> {
            if (!name.startsWith(prefix)) return;
            String suffix = name.substring(prefix.length());
            if (!suffix.matches("[1-9][0-9]{0,2}")) return; // boost-foo, boosting: not ours
            found.put(Integer.parseInt(suffix), box);
        });
        List<Cuboid> ordered = new ArrayList<>();
        for (var entry : found.entrySet()) {
            if (entry.getKey() != ordered.size() + 1)
                throw new IllegalArgumentException("Number " + prefix + " regions consecutively from 1.");
            ordered.add(entry.getValue());
        }
        return ordered;
    }

    /** Comma-separated block material names such as BLUE_GLAZED_TERRACOTTA. Non-text values are ignored. */
    static Set<Material> blockList(Object raw) {
        if (!(raw instanceof String text) || text.isBlank()) return Set.of();
        Set<Material> blocks = new LinkedHashSet<>();
        for (String name : text.split(",")) {
            Material material = Material.getMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null) throw new IllegalArgumentException("boost-block '" + name.trim() + "' is not a block material.");
            blocks.add(material);
        }
        return blocks;
    }
}
