package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;

/**
 * Optional arcade features read from an arena. A map with none of these entries yields {@link #NONE}, so
 * existing maps (Copper Canyon Raceway) are untouched. Parsing is pure: no world access.
 */
final class KartArcadeConfig {
    static final KartArcadeConfig NONE = new KartArcadeConfig(List.of(), Set.of(), List.of(), 1);

    private final List<Cuboid> boostPanels;
    private final Set<Material> boostBlocks;
    private final List<Cuboid> jumps;
    private final double jumpPower;

    private KartArcadeConfig(List<Cuboid> boostPanels, Set<Material> boostBlocks, List<Cuboid> jumps, double jumpPower) {
        this.boostPanels = List.copyOf(boostPanels);
        this.boostBlocks = Set.copyOf(boostBlocks);
        this.jumps = List.copyOf(jumps);
        this.jumpPower = jumpPower;
    }

    List<Cuboid> boostPanels() { return boostPanels; }
    Set<Material> boostBlocks() { return boostBlocks; }
    List<Cuboid> jumps() { return jumps; }
    double jumpPower() { return jumpPower; }

    /** Throws {@link IllegalArgumentException} with a builder-facing message for a malformed optional entry. */
    static KartArcadeConfig load(Map<String, Object> settings, Map<String, Cuboid> regions) {
        return new KartArcadeConfig(numberedRegions(regions, "boost"), blockList(settings.get("boost-block")),
                numberedRegions(regions, "jump"), jumpPower(settings.get("jump-power")));
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

    /** Vertical scale for every jump: 0.5 to 1.5, default 1. */
    static double jumpPower(Object raw) {
        if (!(raw instanceof Number) && !(raw instanceof String text && !text.isBlank())) return 1;
        try {
            double power = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(raw.toString().trim());
            if (power >= .5 && power <= 1.5) return power;
        } catch (NumberFormatException ignored) { /* reported below */ }
        throw new IllegalArgumentException("jump-power must be a number from 0.5 to 1.5.");
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
