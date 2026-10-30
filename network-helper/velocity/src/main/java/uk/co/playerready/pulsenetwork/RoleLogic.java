package uk.co.playerready.pulsenetwork;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Function;

/** Pure rules for /role: role names, group mapping, who may do what, confirmation tokens and the tab-hidden opt-in. No Velocity or LuckPerms types. */
final class RoleLogic {
    private RoleLogic() {}

    static final List<String> ROLES = List.of("dev", "admin", "staff", "venue:ports", "venue:plym", "venue:tru", "venue:tau", "venue:exet");
    static final String DEFAULT_GROUPS = "dev=dev,admin=admin,staff=staff,venue:ports=venue-ports,venue:plym=venue-plym,venue:tru=venue-tru,venue:tau=venue-tau,venue:exet=venue-exet";
    static final String HIDDEN_META = "pulse.tab.hidden";
    static final long CONFIRM_MS = 30_000;

    enum Action { VIEW, SET, REMOVE, HIDE }
    /** Highest group the sender holds. Group membership only: never permissions, because admins hold {@code *}. */
    enum Level { NONE, STAFF, ADMIN, DEV, CONSOLE }

    /** Network announcements: direct admin or dev group, or the console. */
    static boolean canAnnounce(Level level) { return level.compareTo(Level.ADMIN) >= 0; }

    static boolean isVenue(String role) { return role.startsWith("venue:"); }

    /** A known role key (case-insensitive), or empty. */
    static Optional<String> parseRole(String text) {
        if (text == null) return Optional.empty();
        String t = text.toLowerCase(Locale.ROOT);
        return ROLES.contains(t) ? Optional.of(t) : Optional.empty();
    }

    /** Parses {@code roles.groups}: role=group pairs. Unlisted roles keep their defaults; unknown roles or odd group names are rejected. */
    static Map<String, String> parseGroups(String value) {
        var map = new LinkedHashMap<String, String>();
        for (String pair : DEFAULT_GROUPS.split(",")) map.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
        if (value == null || value.isBlank()) return Map.copyOf(map);
        for (String pair : value.split(",")) {
            if (pair.isBlank()) continue;
            int eq = pair.indexOf('=');
            if (eq < 1 || eq == pair.length() - 1) throw new IllegalArgumentException("Invalid roles.groups entry");
            String role = pair.substring(0, eq).trim().toLowerCase(Locale.ROOT), group = pair.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
            if (!ROLES.contains(role) || !group.matches("[a-z0-9_.-]{1,36}")) throw new IllegalArgumentException("Invalid roles.groups entry");
            map.put(role, group);
        }
        if (new HashSet<>(map.values()).size() != map.size()) throw new IllegalArgumentException("roles.groups maps two roles to one group");
        return Map.copyOf(map);
    }

    /** Level from the sender's direct group names. */
    static Level level(Set<String> groups, Map<String, String> map) {
        if (groups.contains(map.get("dev"))) return Level.DEV;
        if (groups.contains(map.get("admin"))) return Level.ADMIN;
        if (groups.contains(map.get("staff"))) return Level.STAFF;
        return Level.NONE;
    }

    /** {@code role} may be null for actions that name none. */
    static boolean allowed(Level level, Action action, String role) {
        if (level == Level.CONSOLE) return true;
        return switch (action) {
            case VIEW -> level.compareTo(Level.STAFF) >= 0;
            case HIDE -> level.compareTo(Level.ADMIN) >= 0;
            case SET, REMOVE -> role != null && (isVenue(role) ? level.compareTo(Level.STAFF) >= 0 : level == Level.DEV);
        };
    }

    /** Tab-hidden is an explicit opt-in: user meta {@code pulse.tab.hidden=true} or the config list. The {@code *} wildcard no longer hides anyone. */
    static boolean tabHidden(UUID id, String name, Function<UUID, String> meta, Set<String> configured) {
        return "true".equalsIgnoreCase(meta.apply(id)) || configured.contains(name.toLowerCase(Locale.ROOT));
    }
    static Set<String> names(String csv) {
        var set = new HashSet<String>();
        if (csv != null) for (String s : csv.split(",")) if (!s.isBlank()) set.add(s.trim().toLowerCase(Locale.ROOT));
        return Set.copyOf(set);
    }

    /** One pending confirmation per sender, valid for {@link #CONFIRM_MS}. */
    static final class Confirmations<T> {
        private record Entry<T>(T value, long expires) {}
        private final Map<String, Entry<T>> pending = new java.util.concurrent.ConcurrentHashMap<>();
        private final LongSupplier clock;
        Confirmations(LongSupplier clock) { this.clock = clock; }
        void put(String sender, T value) { pending.put(sender, new Entry<>(value, clock.getAsLong() + CONFIRM_MS)); }
        /** Consumes the pending item; empty when none or expired. */
        Optional<T> take(String sender) {
            var e = pending.remove(sender);
            return e == null || clock.getAsLong() > e.expires() ? Optional.empty() : Optional.of(e.value());
        }
    }

    private static final List<String> SUBS = List.of("set", "remove", "who", "list", "venues", "hide", "unhide", "confirm");
    static List<String> complete(String[] args, Collection<String> online) {
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> pool = switch (args.length) {
            case 0, 1 -> SUBS;
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set", "remove", "who", "hide", "unhide" -> new ArrayList<>(online);
                case "list" -> ROLES;
                default -> List.of();
            };
            case 3 -> switch (args[0].toLowerCase(Locale.ROOT)) { case "set", "remove" -> ROLES; default -> List.of(); };
            default -> List.of();
        };
        return pool.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).sorted().toList();
    }
}
