package uk.co.playerready.pumpkinhunt;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses the admin text route: {@code /pumpkins admin <verb> ...} and the hidden {@code /admin-route hunt <verb> ...}
 * that the network admin hub calls. Pure text parsing, so it can be tested without a server.
 */
public final class HuntAdminRoute {
    /** The area name the hub uses for this plugin. */
    public static final String AREA = "hunt";
    /** The hidden, stable route command (plugin.yml). */
    public static final String ROUTE_COMMAND = "admin-route";
    public static final List<String> VERBS = List.of("stats", "list", "reset", "enable", "disable", "add", "remove", "reload");

    /** verb is empty for a bare {@code admin} (summary). {@code args} are the words after the verb. */
    public record Parsed(String verb, String[] args) { }

    private HuntAdminRoute() { }

    /** Words after {@code /pumpkins admin}. Unknown verbs return null. */
    public static Parsed parse(String[] afterAdmin) {
        if (afterAdmin.length == 0) return new Parsed("", new String[0]);
        String verb = afterAdmin[0].toLowerCase(Locale.ROOT);
        if (!VERBS.contains(verb)) return null;
        return new Parsed(verb, Arrays.copyOfRange(afterAdmin, 1, afterAdmin.length));
    }

    /** Words after {@code /admin-route}: the first must be this area. Returns the words after it, or null when it is another area. */
    public static String[] stripArea(String[] routeArgs) {
        if (routeArgs.length == 0 || !routeArgs[0].equalsIgnoreCase(AREA)) return null;
        return Arrays.copyOfRange(routeArgs, 1, routeArgs.length);
    }
}
