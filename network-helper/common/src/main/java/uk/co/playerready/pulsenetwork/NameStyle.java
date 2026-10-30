package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.util.HSVLike;
import java.util.*;

/**
 * Pure renderer for {@code [TAG] [12] name}. Styles live in code (child-safe by construction); only role tag text and
 * colour are configurable. Player text is always {@code Component.text}, never parsed. Nothing here does I/O.
 */
public final class NameStyle {
    public static final int MAX_VISIBLE = 32, MAX_TAG = 6;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** {@code role} and {@code styleId} may be null; {@code level} is shown only when positive. */
    public record Identity(String name, String role, String styleId, Integer level) {}

    private interface Painter { Component paint(String name, long tick); }
    private record Def(Painter painter, NamedTextColor bedrock, boolean animated) {}
    private static final Map<String, Def> STYLES = new LinkedHashMap<>();
    private static void solid(String id, NamedTextColor c) { STYLES.put(id, new Def((n, t) -> Component.text(n, c), c, false)); }
    private static void gradient(String id, NamedTextColor bedrock, TextColor... stops) {
        STYLES.put(id, new Def((n, t) -> perChar(n, i -> along(stops, n.length() < 2 ? 0 : i / (double) (n.length() - 1))), bedrock, false));
    }
    private static void animated(String id, NamedTextColor bedrock, Painter p) { STYLES.put(id, new Def(p, bedrock, true)); }
    static {
        solid("red", NamedTextColor.RED); solid("orange", NamedTextColor.GOLD); solid("yellow", NamedTextColor.YELLOW);
        solid("green", NamedTextColor.GREEN); solid("aqua", NamedTextColor.AQUA); solid("blue", NamedTextColor.BLUE);
        solid("purple", NamedTextColor.DARK_PURPLE); solid("pink", NamedTextColor.LIGHT_PURPLE);
        gradient("sunset", NamedTextColor.GOLD, NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW);
        gradient("ocean", NamedTextColor.AQUA, NamedTextColor.AQUA, NamedTextColor.BLUE);
        gradient("forest", NamedTextColor.GREEN, NamedTextColor.DARK_GREEN, NamedTextColor.GREEN, NamedTextColor.YELLOW);
        gradient("candy", NamedTextColor.LIGHT_PURPLE, NamedTextColor.LIGHT_PURPLE, NamedTextColor.AQUA);
        animated("rainbow", NamedTextColor.RED, (n, t) ->
                perChar(n, i -> TextColor.color(HSVLike.hsvLike((float) (((i / (double) Math.max(1, n.length()) + t / 12.0) % 1 + 1) % 1), 0.85f, 1f))));
        animated("shimmer", NamedTextColor.GOLD, (n, t) -> perChar(n, i -> {
            long d = Math.abs(i - Math.floorMod(t, n.length() + 4L) + 2);
            return d == 0 ? NamedTextColor.WHITE : d == 1 ? NamedTextColor.YELLOW : NamedTextColor.GOLD;
        }));
        animated("pulse", NamedTextColor.AQUA, (n, t) -> {
            float k = (float) (0.5 + 0.5 * Math.sin(t * 0.6));
            return Component.text(n, TextColor.lerp(k, NamedTextColor.DARK_AQUA, NamedTextColor.WHITE));
        });
        animated("sparkle", NamedTextColor.LIGHT_PURPLE, (n, t) -> perChar(n, i ->
                i == Math.floorMod(t, (long) n.length()) || i == Math.floorMod(t + n.length() / 2, (long) n.length())
                        ? NamedTextColor.WHITE : NamedTextColor.LIGHT_PURPLE));
    }
    private static Component perChar(String name, java.util.function.IntFunction<TextColor> colour) {
        var out = Component.text();
        for (int i = 0; i < name.length(); i++) out.append(Component.text(name.charAt(i), colour.apply(i)));
        return out.build();
    }
    private static TextColor along(TextColor[] stops, double x) {
        if (stops.length == 1) return stops[0];
        double scaled = x * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) scaled);
        return TextColor.lerp((float) (scaled - i), stops[i], stops[i + 1]);
    }

    private NameStyle() {}

    public static Set<String> styleIds() { return Collections.unmodifiableSet(STYLES.keySet()); }
    public static boolean hasStyle(String id) { return id != null && STYLES.containsKey(id); }
    public static boolean isAnimated(String id) { return hasStyle(id) && STYLES.get(id).animated(); }

    /** Role tags: id to MiniMessage template. Text is capped at {@value #MAX_TAG} characters once tags are stripped. */
    public static final class Tags {
        public static final Map<String, String> DEFAULTS = Map.of("dev", "<aqua>DEV</aqua>", "admin", "<gradient:red:gold>ADMIN</gradient>",
                "staff", "<green>STAFF</green>", "venue", "<yellow>VENUE</yellow>");
        public static final Tags DEFAULT = new Tags(Map.of());
        private record Tag(Component full, String text, NamedTextColor bedrock) {}
        private final Map<String, Tag> tags = new LinkedHashMap<>();

        /** {@code overrides} replace defaults per role; a blank value removes that role's tag. */
        public Tags(Map<String, String> overrides) {
            var merged = new LinkedHashMap<>(DEFAULTS);
            overrides.forEach((role, template) -> {
                if (!role.matches("[a-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid tag role");
                if (template.length() > 200) throw new IllegalArgumentException("A tag template is too long");
                merged.put(role, template);
            });
            merged.forEach((role, template) -> {
                if (template.isBlank()) return;
                Component parsed = MM.deserialize(template);
                String text = PlainTextComponentSerializer.plainText().serialize(parsed).strip();
                if (text.isEmpty()) return;
                TextColor first = firstColour(parsed);
                NamedTextColor bedrock = NamedTextColor.nearestTo(first == null ? NamedTextColor.WHITE : first);
                if (text.length() > MAX_TAG) { text = text.substring(0, MAX_TAG); parsed = Component.text(text, first == null ? NamedTextColor.WHITE : first); }
                tags.put(role, new Tag(parsed, text, bedrock));
            });
        }
        public boolean has(String role) { return role != null && tags.containsKey(role.toLowerCase(Locale.ROOT)); }
        public Set<String> roles() { return Collections.unmodifiableSet(tags.keySet()); }
        int length(String role) { return tags.get(role).text().length() + 2; }
        Component component(String role, boolean bedrock) {
            var tag = tags.get(role);
            var body = bedrock ? Component.text(tag.text(), tag.bedrock()) : tag.full();
            return Component.text("[", NamedTextColor.DARK_GRAY).append(body).append(Component.text("]", NamedTextColor.DARK_GRAY));
        }
        private static TextColor firstColour(Component c) {
            if (c.color() != null) return c.color();
            for (var child : c.children()) { var found = firstColour(child); if (found != null) return found; }
            return null;
        }
    }

    public static Component render(Identity id, boolean bedrockViewer, long tick) { return render(id, bedrockViewer, tick, Tags.DEFAULT, MAX_VISIBLE); }

    /**
     * {@code [TAG] [12] name}. The name is never truncated; if the row would exceed {@code maxVisible} the level badge
     * goes first, then the tag. Bedrock viewers get one static legacy-safe colour per style and per tag.
     */
    public static Component render(Identity id, boolean bedrockViewer, long tick, Tags tags, int maxVisible) {
        String name = id.name();
        boolean tag = tags.has(id.role());
        String role = tag ? id.role().toLowerCase(Locale.ROOT) : null;
        String badge = id.level() != null && id.level() > 0 ? "[" + id.level() + "]" : null;
        int need = name.length() + (tag ? tags.length(role) + 1 : 0) + (badge == null ? 0 : badge.length() + 1);
        if (need > maxVisible && badge != null) { need -= badge.length() + 1; badge = null; }
        if (need > maxVisible && tag) tag = false;
        var out = Component.text();
        if (tag) out.append(tags.component(role, bedrockViewer)).append(Component.space());
        if (badge != null) out.append(Component.text(badge, NamedTextColor.GRAY)).append(Component.space());
        Def def = hasStyle(id.styleId()) ? STYLES.get(id.styleId()) : null;
        if (def == null) out.append(Component.text(name));
        else out.append(bedrockViewer ? Component.text(name, def.bedrock()) : def.painter().paint(name, tick));
        return out.build();
    }
}
