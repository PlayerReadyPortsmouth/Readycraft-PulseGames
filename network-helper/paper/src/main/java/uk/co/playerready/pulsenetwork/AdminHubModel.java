package uk.co.playerready.pulsenetwork;

import java.util.*;
import java.util.function.Predicate;

/** Pure model of the {@code /admin} hub (areas, gating): who may do what, which sections this server shows, the whitelist commands and the name-style grid. */
final class AdminHubModel {
    private AdminHubModel() {}

    /** Direct LuckPerms groups that may open the hub, and the narrower set for risky actions. */
    static final Set<String> HUB_GROUPS = RoleMenuModel.GROUPS, ADMIN_GROUPS = Set.of("dev", "admin");

    /**
     * One {@code /admin <area>}. {@code id} is the word typed and the config key ({@code admin.areas.<id>.min-group}); {@code minAdmin} is the default
     * minimum group (false = staff, true = dev/admin). Owner areas (games, survival, hunt, server) default to staff (Aurora, 30 Oct 2026).
     */
    enum Area {
        PLAYERS("players", false), ROLES("roles", false), NICK("nick", false), STYLES("styles", true), IDENTITY("identity", false), TAB("tab", true),
        WHITELIST("whitelist", false), ANNOUNCE("announce", true),
        GAMES("games", false), SURVIVAL("survival", false), HUNT("hunt", false), SERVER("server", false);
        final String id; final boolean minAdmin;
        Area(String id, boolean minAdmin) { this.id = id; this.minAdmin = minAdmin; }
        static Optional<Area> of(String word) { return Arrays.stream(values()).filter(a -> a.id.equals(word == null ? "" : word.toLowerCase(Locale.ROOT))).findFirst(); }
    }

    /** What this server has. Faked in tests. {@code routeAvailable} is true when the plugin registers its hidden route command. */
    interface Env { boolean pluginEnabled(String name); boolean routeAvailable(AdminRoutes.Owner owner); boolean bridgeEnabled(); }
    /** Sends a network announcement through the proxy link ({@link PaperBridge#announce}); false when it cannot be sent. */
    interface Announcer { boolean enabled(); boolean announce(String text, String style); }
    /** Announcement styles offered by the hub: button label and the wire style. */
    record AnnounceStyle(String label, String style) {}
    static final List<AnnounceStyle> ANNOUNCE_STYLES = List.of(
            new AnnounceStyle("Chat", "chat"), new AnnounceStyle("Title", "title"), new AnnounceStyle("Action bar", "bar"));
    static final String WHITELIST_PLUGIN = "UltimateWhitelist";

    static Optional<AdminRoutes.Owner> owner(Area a) {
        return AdminRoutes.OWNERS.stream().filter(o -> o.areaId().equals(a.id)).findFirst();
    }
    private static boolean ownerPresent(Env env, AdminRoutes.Owner o) { return env.pluginEnabled(o.plugin()) && env.routeAvailable(o); }
    /** The owner behind {@code /admin server} on this server: the first of PulseGames, PulseAddon, PumpkinHunt that is present with its route. */
    static Optional<AdminRoutes.Owner> serverOwner(Env env) { return AdminRoutes.OWNERS.stream().filter(o -> ownerPresent(env, o)).findFirst(); }

    /** Whether this server has what the area needs (plugin and route command, the whitelist plugin, the proxy link). */
    static boolean available(Env env, Area a) {
        return switch (a) {
            case WHITELIST -> env.pluginEnabled(WHITELIST_PLUGIN);
            case ANNOUNCE -> env.bridgeEnabled();
            case GAMES, SURVIVAL, HUNT -> ownerPresent(env, owner(a).orElseThrow());
            case SERVER -> serverOwner(env).isPresent();
            default -> true;
        };
    }

    /** Minimum gate for an area: the default, or {@code admin.areas.<id>.min-group} ("staff" or "admin") when set. */
    static Gate gateOf(Area a, Map<String, String> minGroups) {
        String set = minGroups.get(a.id);
        if (set != null) return set.equalsIgnoreCase("admin") ? Gate.ADMIN : Gate.HUB;
        return a.minAdmin ? Gate.ADMIN : Gate.HUB;
    }

    /**
     * Areas this viewer sees here, in order: present on this server and allowed by the area's minimum group. A hidden area is not told apart from
     * one that does not exist, so the list does not leak.
     */
    static List<Area> visible(Env env, Predicate<Gate> may, Map<String, String> minGroups) {
        var out = new ArrayList<Area>();
        for (Area a : Area.values()) if (available(env, a) && may.test(gateOf(a, minGroups))) out.add(a);
        return out;
    }

    /** The area behind a typed word if this viewer sees it; empty for hidden and unknown alike. */
    static Optional<Area> resolve(String word, List<Area> visible) { return Area.of(word).filter(visible::contains); }

    // ---- tab-complete

    static final List<String> WHITELIST_VERBS = List.of("add", "remove", "on", "off", "list", "status");
    /** Verbs per area for tab-complete; the owner areas' verbs belong to the owner plugin. */
    static List<String> complete(List<Area> visible, Predicate<Gate> may, List<String> names, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> pool;
        if (args.length <= 1) pool = visible.stream().map(a -> a.id).toList();
        else {
            var area = resolve(args[0], visible);
            pool = List.of();
            if (area.isPresent()) switch (area.get()) {
                case PLAYERS, ROLES, STYLES, IDENTITY -> { if (args.length == 2) pool = names; }
                case NICK -> pool = args.length == 2 ? names : args.length == 3 ? List.of(NickRules.RESET) : List.of();
                case TAB -> pool = args.length == 2 ? List.of("hide", "show") : args.length == 3 ? names : List.of();
                case WHITELIST -> pool = args.length == 2 ? WHITELIST_VERBS.stream().filter(v -> may.test(gate(WhitelistOp.valueOf(v.toUpperCase(Locale.ROOT))))).toList()
                        : args.length == 3 && (args[1].equals("add") || args[1].equals("remove")) ? names : List.of();
                default -> { }
            }
        }
        return pool.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(cur)).toList();
    }

    // ---- hidden aliases (kept one release)

    static final Set<String> HIDDEN_ALIASES = Set.of("adminmenu", "am", "rolemenu");
    /** The one-line notice for an old command word, or empty when {@code label} is not an old alias. */
    static Optional<String> aliasHint(String label, String[] args) {
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (l.equals("rolemenu")) return Optional.of("/rolemenu is moving: use /" + AdminRoutes.ADMIN + " roles" + (args.length > 0 ? " " + args[0] : "") + " next time.");
        if (HIDDEN_ALIASES.contains(l)) return Optional.of("/" + l + " is moving: use /" + AdminRoutes.ADMIN + " next time.");
        return Optional.empty();
    }

    // ---- gating

    enum Gate { HUB, ADMIN }
    static boolean allowed(Gate gate, Set<String> directGroups) {
        var need = gate == Gate.ADMIN ? ADMIN_GROUPS : HUB_GROUPS;
        return directGroups.stream().anyMatch(need::contains);
    }

    // ---- whitelist (UltimateWhitelist: /uw add|remove <player>, list, on, off, status)

    enum WhitelistOp { ADD, REMOVE, LIST, ON, OFF, STATUS }
    /** Gate per operation: adding and reading are for the whole hub; removing and switching the whitelist are dev/admin. */
    static Gate gate(WhitelistOp op) { return op == WhitelistOp.ADD || op == WhitelistOp.LIST || op == WhitelistOp.STATUS ? Gate.HUB : Gate.ADMIN; }
    /** The console command (no slash), or empty when the name is not valid. */
    static Optional<String> whitelistCommand(WhitelistOp op, String name) {
        String verb = op.name().toLowerCase(Locale.ROOT);
        if (op == WhitelistOp.ADD || op == WhitelistOp.REMOVE) return RoleMenuCommands.validName(name) ? Optional.of(AdminRoutes.whitelist(verb + " " + name)) : Optional.empty();
        return Optional.of(AdminRoutes.whitelist(verb));
    }

    // ---- name-style grid (dev/admin write pulse.namestyle.<id> on the target)

    record Cell(String id, boolean held, int slot) {}
    static final int GRID_SIZE = 27, BACK_SLOT = 22;
    /** One cell per style in catalogue order; {@code has} answers "does the target hold this permission". */
    static List<Cell> grid(Predicate<String> has) {
        var out = new ArrayList<Cell>();
        int slot = 0;
        for (String id : NameStyle.styleIds()) out.add(new Cell(id, has.test(permission(id)), slot++));
        return out;
    }
    static String permission(String id) { return NameStyleModel.PERMISSION_PREFIX + id; }

    // ---- audit

    /** The one console line per admin action. */
    static String audit(String actor, String action, String target) { return "ADMIN " + actor + " " + action + " " + (target == null || target.isEmpty() ? "-" : target); }

    /** Menu rows for a list of buttons: whole rows of nine, at most six. */
    static int menuSize(int buttons) { return Math.max(9, Math.min(54, (buttons + 8) / 9 * 9)); }
}
