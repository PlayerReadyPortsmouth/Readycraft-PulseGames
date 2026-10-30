package uk.co.playerready.pulsenetwork;

import java.util.*;
import java.util.function.Predicate;

/** Pure model of the {@code /admin} hub: who may do what, which sections this server shows, the whitelist commands and the name-style grid. */
final class AdminHubModel {
    private AdminHubModel() {}

    /** Direct LuckPerms groups that may open the hub, and the narrower set for risky actions. */
    static final Set<String> HUB_GROUPS = RoleMenuModel.GROUPS, ADMIN_GROUPS = Set.of("dev", "admin");

    enum Section { PLAYERS, WHITELIST, NETWORK, GAMES, ADDON, PUMPKINS }
    /** A plugin whose own admin menu we open by running {@code command} as the player. */
    record PluginSection(Section section, String plugin, String label, String command) {}
    static final List<PluginSection> PLUGINS = List.of(
            new PluginSection(Section.GAMES, "PulseGames", "PulseGames", "pulse adminmenu"),
            new PluginSection(Section.ADDON, "PulseAddon", "PulseAddon", "pulseadmin menu"),
            new PluginSection(Section.PUMPKINS, "PumpkinHunt", "Pumpkin Hunt", "pumpkins admin"));

    /** What this server has. Faked in tests. {@code bridgeEnabled} is true once the signed proxy link is configured. */
    interface Env { boolean pluginEnabled(String name); boolean bridgeEnabled(); }
    /** Sends a network announcement through the proxy link ({@link PaperBridge#announce}); false when it cannot be sent. */
    interface Announcer { boolean enabled(); boolean announce(String text, String style); }
    /** Announcement styles offered by the hub: button label and the wire style. */
    record AnnounceStyle(String label, String style) {}
    static final List<AnnounceStyle> ANNOUNCE_STYLES = List.of(
            new AnnounceStyle("Chat", "chat"), new AnnounceStyle("Title", "title"), new AnnounceStyle("Action bar", "bar"));
    static final String WHITELIST_PLUGIN = "UltimateWhitelist";

    /**
     * Sections to show here, in order. Network (Announce) shows to Dev and Admin whenever the proxy link is enabled: /announce lives on the
     * proxy, and the hub sends the signed ANNOUNCE message, so the proxy's rate limit and log still apply. The whitelist exists only where
     * UltimateWhitelist is enabled; the plugin buttons only where the plugin is.
     */
    static List<Section> visible(Env env, boolean admin) {
        var out = new ArrayList<Section>();
        out.add(Section.PLAYERS);
        if (env.pluginEnabled(WHITELIST_PLUGIN)) out.add(Section.WHITELIST);
        if (admin && env.bridgeEnabled()) out.add(Section.NETWORK);
        for (var p : PLUGINS) if (env.pluginEnabled(p.plugin())) out.add(p.section());
        return out;
    }

    static Optional<PluginSection> plugin(Section s) { return PLUGINS.stream().filter(p -> p.section() == s).findFirst(); }

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
        if (op == WhitelistOp.ADD || op == WhitelistOp.REMOVE) return RoleMenuCommands.validName(name) ? Optional.of("uw " + verb + " " + name) : Optional.empty();
        return Optional.of("uw " + verb);
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
