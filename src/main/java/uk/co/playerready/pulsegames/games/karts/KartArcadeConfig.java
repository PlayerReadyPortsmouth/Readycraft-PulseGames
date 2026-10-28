package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.Material;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;

/**
 * Optional arcade features read from an arena. A map with none of these entries yields {@link #NONE}, so
 * existing maps (Copper Canyon Raceway) are untouched. Parsing is pure: no world access.
 */
final class KartArcadeConfig {
    static final KartArcadeConfig NONE = new KartArcadeConfig(List.of(), Set.of(), List.of(), 1, List.of(), List.of());

    private final List<Cuboid> boostPanels;
    private final Set<Material> boostBlocks;
    private final List<Cuboid> jumps;
    private final double jumpPower;
    private final List<Cuboid> antiGravity;
    private final List<List<Pt>> antiGravityRails;

    record Pt(double x, double y, double z) {}

    private KartArcadeConfig(List<Cuboid> boostPanels, Set<Material> boostBlocks, List<Cuboid> jumps, double jumpPower,
                             List<Cuboid> antiGravity, List<List<Pt>> antiGravityRails) {
        this.antiGravity = List.copyOf(antiGravity);
        this.antiGravityRails = antiGravityRails.stream().map(List::copyOf).toList();
        this.boostPanels = List.copyOf(boostPanels);
        this.boostBlocks = Set.copyOf(boostBlocks);
        this.jumps = List.copyOf(jumps);
        this.jumpPower = jumpPower;
    }

    List<Cuboid> boostPanels() { return boostPanels; }
    Set<Material> boostBlocks() { return boostBlocks; }
    List<Cuboid> jumps() { return jumps; }
    double jumpPower() { return jumpPower; }
    List<Cuboid> antiGravity() { return antiGravity; }
    /** Rail points for each anti-gravity region, same order as {@link #antiGravity()}; ribbon visuals only. */
    List<List<Pt>> antiGravityRails() { return antiGravityRails; }
    boolean inAntiGravity(double x, double y, double z) {
        for (Cuboid box : antiGravity) if (KartPads.inside(box, x, y, z)) return true;
        return false;
    }

    /** Throws {@link IllegalArgumentException} with a builder-facing message for a malformed optional entry. */
    static KartArcadeConfig load(Map<String, Object> settings, Map<String, Cuboid> regions) {
        List<Cuboid> antiGravity = numberedRegions(regions, "antigrav");
        return new KartArcadeConfig(numberedRegions(regions, "boost"), blockList(settings.get("boost-block")),
                numberedRegions(regions, "jump"), jumpPower(settings.get("jump-power")),
                antiGravity, rails(settings, antiGravity));
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

    /**
     * {@code antigrav-path1..M} locations in order. Each point joins the anti-gravity region that contains it, so one
     * numbered list can run through several sections; a point outside every section is almost certainly a typo.
     */
    static List<List<Pt>> rails(Map<String, Object> settings, List<Cuboid> sections) {
        SortedMap<Integer, Pt> points = new TreeMap<>();
        settings.forEach((key, value) -> {
            if (!key.startsWith("antigrav-path") || !(value instanceof String text)) return;
            String suffix = key.substring("antigrav-path".length());
            if (!suffix.matches("[1-9][0-9]{0,2}")) throw new IllegalArgumentException("Use antigrav-path1, antigrav-path2, ...");
            try {
                var at = uk.co.playerready.pulsegames.core.util.LocUtil.parse(text, null);
                points.put(Integer.parseInt(suffix), new Pt(at.getX(), at.getY(), at.getZ()));
            } catch (RuntimeException bad) { throw new IllegalArgumentException(key + " has invalid coordinates."); }
        });
        List<List<Pt>> rails = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) rails.add(new ArrayList<>());
        int expected = 1;
        for (var entry : points.entrySet()) {
            if (entry.getKey() != expected++) throw new IllegalArgumentException("Number antigrav-path points consecutively from 1.");
            Pt p = entry.getValue();
            int section = -1;
            for (int i = 0; i < sections.size() && section < 0; i++) if (KartPads.inside(sections.get(i), p.x(), p.y(), p.z())) section = i;
            if (section < 0) throw new IllegalArgumentException("antigrav-path" + entry.getKey() + " is not inside any antigrav region.");
            rails.get(section).add(p);
        }
        return rails;
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
