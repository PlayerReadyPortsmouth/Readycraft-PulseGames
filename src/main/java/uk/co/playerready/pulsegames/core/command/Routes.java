package uk.co.playerready.pulsegames.core.command;

import java.util.List;

/**
 * Every command string a PulseGames menu, button or chat click dispatches. Nothing else in the plugin may call
 * {@code performCommand}, {@code dispatchCommand}, {@code ClickEvent.runCommand} or {@code <click:run_command:...>}
 * with a literal; {@code CommandStandardTest} enforces that and checks each string resolves to a registered command.
 */
public final class Routes {
    private Routes() { }

    /** Hidden, stable command the PulseNetwork {@code /admin} hub dispatches: {@code admin-route <area> [args]}. */
    public static final String ADMIN_ROUTE = "admin-route";
    /** Area word for this plugin's admin verbs. */
    public static final String AREA_GAMES = "games";
    /** Area word the hub uses on this server for the plugin's own admin menu. */
    public static final String AREA_SERVER = "server";
    /** The retiring command, kept as a hidden alias for one release. */
    public static final String LEGACY = "pulse";
    /** What staff type; used in usage text. */
    public static final String TYPED_GAMES = "/admin games";
    /** Prefix of every setup checklist command; ArenaSetupMenu parses it off. */
    public static final String SETUP_PREFIX = TYPED_GAMES + " setup ";

    // ---- admin menu buttons (dispatch as the clicking admin) ----
    public static final String BOTS = ADMIN_ROUTE + " " + AREA_GAMES + " bots";
    public static final String RELOAD = ADMIN_ROUTE + " " + AREA_GAMES + " reload";
    public static final String MAPS_BOARD = ADMIN_ROUTE + " " + AREA_GAMES + " maps board";
    public static final String LOBBY = ADMIN_ROUTE + " " + AREA_GAMES + " lobby";

    // ---- player commands dispatched by menus and chat clicks ----
    public static final String RESULTS = "results";
    public static final String EVENT_JOIN = "event join";
    public static final String PARTY_ACCEPT = "party accept";
    public static final String PARTY_DENY = "party deny";
    public static final String NAMESTYLE = "namestyle";

    public static String partySub(String sub) { return "party " + sub; }

    public static String namestyleSet(String id) { return NAMESTYLE + " set " + id; }

    /** A chat click target: the same command with the slash chat needs. */
    public static String slash(String command) { return "/" + command; }

    /** Every fixed string above plus one sample of each builder, for the resolve test. */
    public static List<String> all() {
        return List.of(BOTS, RELOAD, MAPS_BOARD, LOBBY, RESULTS, EVENT_JOIN, PARTY_ACCEPT, PARTY_DENY, NAMESTYLE,
                partySub("invites on"), partySub("invites off"), partySub("disband"), partySub("leave"),
                partySub("invite someone"), namestyleSet("id"));
    }
}
