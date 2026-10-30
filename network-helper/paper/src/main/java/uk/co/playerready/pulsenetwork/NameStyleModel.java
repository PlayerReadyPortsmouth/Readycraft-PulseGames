package uk.co.playerready.pulsenetwork;

import org.bukkit.Material;
import java.util.*;
import java.util.function.Predicate;

/** Pure model of the /namestyle picker: what is unlocked, in what order, in which slot, and the Bedrock form text. No Bukkit state. */
final class NameStyleModel {
    static final String PERMISSION_PREFIX = "pulse.namestyle.", CLEAR = "clear";
    static final int MENU_SIZE = 27, CLEAR_SLOT = 22;
    private static final long COOLDOWN_MS = 5000;

    record Entry(String id, boolean unlocked, boolean current) {}
    private NameStyleModel() {}

    /** Unlocked styles first, then locked, each in catalogue order. {@code current} may be null. */
    static List<Entry> entries(Predicate<String> hasPermission, String current) {
        var open = new ArrayList<Entry>(); var locked = new ArrayList<Entry>();
        for (String id : NameStyle.styleIds()) {
            boolean ok = hasPermission.test(PERMISSION_PREFIX + id);
            (ok ? open : locked).add(new Entry(id, ok, ok && id.equals(current)));
        }
        open.addAll(locked);
        return open;
    }

    /** Menu slot to style id; the clear button sits at {@link #CLEAR_SLOT}. */
    static Map<Integer, String> slots(List<Entry> entries) {
        var map = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < entries.size() && i < CLEAR_SLOT; i++) map.put(i, entries.get(i).id());
        map.put(CLEAR_SLOT, CLEAR);
        return map;
    }

    static String title(String id) { return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1); }

    /** One-word colour hint for the Bedrock form (Bedrock shows a single static colour anyway). */
    static String hint(String id) {
        return switch (id) {
            case "sunset" -> "warm"; case "ocean" -> "cool"; case "forest" -> "leafy"; case "candy" -> "sweet";
            case "rainbow" -> "colourful"; case "shimmer" -> "golden"; case "pulse" -> "glowing"; case "sparkle" -> "twinkly";
            case "orange" -> "orange"; default -> id;
        };
    }

    static Material material(String id) {
        return switch (id) {
            case "red" -> Material.RED_WOOL; case "orange" -> Material.ORANGE_WOOL; case "yellow" -> Material.YELLOW_WOOL;
            case "green" -> Material.LIME_WOOL; case "aqua" -> Material.LIGHT_BLUE_WOOL; case "blue" -> Material.BLUE_WOOL;
            case "purple" -> Material.PURPLE_WOOL; case "pink" -> Material.PINK_WOOL;
            case "sunset" -> Material.ORANGE_CONCRETE; case "ocean" -> Material.CYAN_CONCRETE;
            case "forest" -> Material.GREEN_CONCRETE; case "candy" -> Material.PINK_CONCRETE;
            case "rainbow" -> Material.WHITE_WOOL; case "shimmer" -> Material.GOLD_BLOCK;
            case "pulse" -> Material.PRISMARINE; default -> Material.AMETHYST_BLOCK;
        };
    }

    /** Bedrock button for an unlocked style, for example {@code Red (red)} or {@code Sunset (warm) - selected}. */
    static String button(Entry e) { return title(e.id()) + " (" + hint(e.id()) + ")" + (e.current() ? " - selected" : ""); }

    /** Bedrock form body: how to use it, then the locked styles listed below the buttons. */
    static String formContent(List<Entry> entries) {
        var locked = entries.stream().filter(e -> !e.unlocked()).map(e -> title(e.id())).toList();
        String body = "Pick a style for your name. It shows in the tab list and in chat.";
        if (locked.isEmpty()) return body + "\n\nYou have unlocked every style.";
        return body + "\n\nLocked (unlock by levelling up):\n" + String.join(", ", locked);
    }

    /** Unlocked entries in button order; the form adds a final "Clear my style" button after them. */
    static List<Entry> formButtons(List<Entry> entries) { return entries.stream().filter(Entry::unlocked).toList(); }

    /** At most one change per five seconds per player. Returns true and records the change when allowed. */
    static boolean allow(Map<UUID, Long> last, UUID id, long now) {
        Long before = last.get(id);
        if (before != null && now - before < COOLDOWN_MS) return false;
        last.put(id, now);
        return true;
    }

    /** Parses a stored meta value to a known style id, or null. */
    static String known(String value) { return value != null && NameStyle.hasStyle(value.strip().toLowerCase(Locale.ROOT)) ? value.strip().toLowerCase(Locale.ROOT) : null; }
}
