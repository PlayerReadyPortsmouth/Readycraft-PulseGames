package uk.co.playerready.pulsenetwork;

import java.util.*;

/**
 * Composes and validates the {@code /role} command lines the Paper role menu asks the proxy to run as the player. Shared so the
 * backend only sends what the proxy will accept. The proxy's own gating, audit line and confirm step still decide everything.
 */
public final class RoleMenuCommands {
    /** Same role keys as the proxy's {@code /role}. */
    public static final List<String> ROLES = List.of("dev", "admin", "staff", "venue:ports", "venue:plym", "venue:tru", "venue:tau", "venue:exet");
    private RoleMenuCommands() {}

    public static boolean validName(String name) { return name != null && name.matches("[.A-Za-z0-9_]{1,17}"); }

    /** Wire fields (arguments after {@code /role}), or empty when the request is not one the menu may make. */
    public static Optional<List<String>> fields(String action, String name, String role) {
        List<String> f = switch (action) {
            case "set", "remove" -> role != null && ROLES.contains(role) ? List.of(action, name == null ? "" : name, role) : null;
            case "who" -> List.of("who", name == null ? "" : name);
            case "confirm" -> List.of("confirm");
            default -> null;
        };
        if (f == null || (!action.equals("confirm") && !validName(name))) return Optional.empty();
        return Optional.of(f);
    }

    /** The full command text, for example {@code /role set X venue:ports}. */
    public static Optional<String> line(String action, String name, String role) {
        return fields(action, name, role).map(f -> "/role " + String.join(" ", f));
    }

    /** Proxy side: validates received fields and returns the command to run without the slash. */
    public static Optional<String> fromFields(List<String> f) {
        if (f.isEmpty()) return Optional.empty();
        return fields(f.get(0), f.size() > 1 ? f.get(1) : null, f.size() > 2 ? f.get(2) : null)
                .filter(c -> c.equals(f)).map(c -> "role " + String.join(" ", c));
    }
}
