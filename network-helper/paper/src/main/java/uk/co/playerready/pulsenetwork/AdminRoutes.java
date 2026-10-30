package uk.co.playerready.pulsenetwork;

/**
 * Every command string the {@code /admin} hub runs, in one place. The hub never writes a command literal anywhere else (a test enforces it).
 * Owner routes are hidden, stable commands in the owning plugin (command standard, Option 1), always addressed with the plugin's namespace
 * because PulseGames and PulseAddon both register {@code admin-route}.
 */
final class AdminRoutes {
    private AdminRoutes() {}

    static final String ROUTE = "admin-route";
    /** Owner plugin name, namespaced route command and the area word that plugin answers to. */
    record Owner(String areaId, String plugin, String label, String command, String area, String serverArea) {
        /** Command line (no slash) for {@code verbs}; empty verbs opens the owner's own menu. */
        String line(String area, String verbs) { return command + " " + area + (verbs == null || verbs.isBlank() ? "" : " " + verbs.trim()); }
    }
    static final Owner GAMES = new Owner("games", "PulseGames", "PulseGames", "pulsegames:" + ROUTE, "games", "server");
    static final Owner SURVIVAL = new Owner("survival", "PulseAddon", "PulseAddon", "pulseaddon:" + ROUTE, "survival", "survival");
    static final Owner HUNT = new Owner("hunt", "PumpkinHunt", "Pumpkin Hunt", "pumpkinhunt:" + ROUTE, "hunt", "hunt");
    /** In the order {@code /admin server} prefers them. */
    static final java.util.List<Owner> OWNERS = java.util.List.of(GAMES, SURVIVAL, HUNT);

    /** UltimateWhitelist's console command word and the proxy hop for servers without a whitelist. */
    static final String WHITELIST = "uw";
    static final String GO_LOBBY = "server lobby";
    /** The hub's own entry, used by the hidden aliases' hints. */
    static final String ADMIN = "admin";
    static String whitelist(String rest) { return WHITELIST + " " + rest; }
}
