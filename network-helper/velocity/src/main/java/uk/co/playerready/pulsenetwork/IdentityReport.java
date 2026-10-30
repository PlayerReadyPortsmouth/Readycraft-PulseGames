package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import java.util.*;
import java.util.function.Predicate;

/** Pure text assembly and gating for {@code /identity}. No Velocity or LuckPerms types, so it is unit tested with fake data. */
final class IdentityReport {
    static final String STYLE_PERMISSION_PREFIX = "pulse.namestyle.";
    private IdentityReport() {}

    /** Everything the report shows, already read from the providers. {@code level} may be null. */
    record Facts(String name, Set<String> groups, String roleMeta, String styleMeta, int stylesHeld, Integer level,
                 boolean hidden, boolean bedrock, String server) {}

    /** Devs, admins and the console may look at anyone; everyone else only at themselves. */
    static boolean mayView(RoleLogic.Level level, boolean self) {
        return self || level == RoleLogic.Level.DEV || level == RoleLogic.Level.ADMIN || level == RoleLogic.Level.CONSOLE;
    }
    static boolean mayRefresh(RoleLogic.Level level) { return mayView(level, false); }

    static int stylesHeld(Predicate<String> hasPermission) {
        return (int) NameStyle.styleIds().stream().filter(id -> hasPermission.test(STYLE_PERMISSION_PREFIX + id)).count();
    }

    /** Plain report lines followed by the two previews. {@code render} is how the row would look to a Java or Bedrock viewer. */
    static List<String> lines(Facts f, NameStyle.Tags tags) {
        var id = IdentityResolver.resolve(new UUID(1, 1), f.name(), (u, k) -> k.equals(IdentityResolver.ROLE_KEY) ? f.roleMeta() : f.styleMeta(), f.level(), tags);
        var out = new ArrayList<String>();
        out.add("Identity of " + f.name() + " on " + (f.server() == null ? "no server" : f.server()) + ":");
        out.add("Groups: " + (f.groups().isEmpty() ? "none" : String.join(", ", new TreeSet<>(f.groups()))));
        String raw = f.roleMeta() == null || f.roleMeta().isBlank() ? null : f.roleMeta().strip();
        out.add("Role (pulse.role): " + (raw == null ? "none" : raw) + "; tag: " + (id.role() == null ? (raw == null ? "none" : "none (unknown role, ignored)") : tags.label(id.role())));
        String style = f.styleMeta() == null || f.styleMeta().isBlank() ? null : f.styleMeta().strip();
        out.add("Style (pulse.namestyle): " + (style == null ? "none" : id.styleId() != null ? style : style + " (unknown style, ignored)")
                + "; unlocked styles held: " + f.stylesHeld() + " of " + NameStyle.styleIds().size() + (f.stylesHeld() == NameStyle.styleIds().size() ? " (a wildcard counts)" : ""));
        out.add("Level: " + (f.level() == null ? "unknown (no recent status)" : f.level()) + "; hidden from tab: " + (f.hidden() ? "yes" : "no") + "; Bedrock: " + (f.bedrock() ? "yes" : "no"));
        out.add("Java viewer sees: " + describe(NameStyle.render(id, false, 0, tags, NameStyle.MAX_VISIBLE)));
        out.add("Bedrock viewer sees: " + describe(NameStyle.render(id, true, 0, tags, NameStyle.MAX_VISIBLE)));
        out.add("(Previews show the static first frame as {colour:text} runs; animated styles move in the Java tab only.)");
        return out;
    }

    /** Text with its colours: adjacent characters of one colour join, e.g. {@code {dark_gray:[}{aqua:DEV}{dark_gray:]} {default:Ann}}. */
    static String describe(Component c) {
        var runs = new ArrayList<Map.Entry<String, StringBuilder>>();
        walk(c, null, runs);
        var sb = new StringBuilder();
        for (var run : runs) sb.append(run.getKey().isEmpty() ? run.getValue() : "{" + run.getKey() + ":" + run.getValue() + "}");
        return sb.toString();
    }
    private static void walk(Component c, TextColor inherited, List<Map.Entry<String, StringBuilder>> runs) {
        TextColor colour = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            String key = t.content().isBlank() ? "" : colour == null ? "default" : colour instanceof NamedTextColor n ? NamedTextColor.NAMES.key(n) : colour.asHexString();
            var last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            if (last != null && last.getKey().equals(key)) last.getValue().append(t.content());
            else runs.add(Map.entry(key, new StringBuilder(t.content())));
        }
        for (var child : c.children()) walk(child, colour, runs);
    }
}
