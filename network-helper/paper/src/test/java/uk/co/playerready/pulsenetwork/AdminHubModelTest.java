package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsenetwork.AdminHubModel.*;

class AdminHubModelTest {
    private static Env env(Set<String> plugins, boolean bridge) {
        return new Env() {
            public boolean pluginEnabled(String n) { return plugins.contains(n); }
            public boolean routeAvailable(AdminRoutes.Owner o) { return plugins.contains(o.plugin()); }
            public boolean bridgeEnabled() { return bridge; }
        };
    }
    private static final Map<String, String> NONE = Map.of();
    private static List<Area> see(Env e, boolean admin) { return visible(e, g -> g == Gate.HUB || admin, NONE); }
    private static final Env EVERYTHING = env(Set.of("UltimateWhitelist", "PulseGames", "PulseAddon", "PumpkinHunt"), true);

    @Test void everyAreaResolvesFromItsWordForTheDevAndNoOtherWordDoes() {
        var all = see(EVERYTHING, true);
        assertEquals(Area.values().length, all.size());
        assertEquals(List.of("players", "roles", "nick", "styles", "identity", "tab", "whitelist", "announce", "games", "survival", "hunt", "server"),
                Arrays.stream(Area.values()).map(a -> a.id).toList());
        for (Area a : Area.values()) assertEquals(Optional.of(a), resolve(a.id, all), a.id);
        assertEquals(Optional.of(Area.GAMES), resolve("GAMES", all));
        assertTrue(resolve("pulse", all).isEmpty());
    }
    @Test void ownerAreasAreHiddenWhenTheirPluginOrRouteIsAbsent() {
        var lobby = see(env(Set.of("UltimateWhitelist", "PumpkinHunt"), true), true);
        assertTrue(lobby.contains(Area.HUNT) && lobby.contains(Area.SERVER) && lobby.contains(Area.WHITELIST));
        assertFalse(lobby.contains(Area.GAMES) || lobby.contains(Area.SURVIVAL));
        assertEquals(Optional.of(AdminRoutes.HUNT), serverOwner(env(Set.of("PumpkinHunt"), false)));
        assertEquals(Optional.of(AdminRoutes.GAMES), serverOwner(EVERYTHING));
        // plugin enabled but no route command (an older jar): hidden
        var noRoute = new Env() {
            public boolean pluginEnabled(String n) { return true; }
            public boolean routeAvailable(AdminRoutes.Owner o) { return false; }
            public boolean bridgeEnabled() { return false; }
        };
        var seen = see(noRoute, true);
        assertFalse(seen.contains(Area.GAMES) || seen.contains(Area.SURVIVAL) || seen.contains(Area.HUNT) || seen.contains(Area.SERVER));
        assertEquals(List.of(Area.PLAYERS, Area.ROLES, Area.NICK, Area.STYLES, Area.IDENTITY, Area.TAB, Area.WHITELIST), seen);
    }
    @Test void gatingPerGroupStaffSeeOwnerAreasButNotStylesTabOrAnnounce() {
        assertEquals(List.of(Area.PLAYERS, Area.ROLES, Area.NICK, Area.IDENTITY, Area.WHITELIST, Area.GAMES, Area.SURVIVAL, Area.HUNT, Area.SERVER), see(EVERYTHING, false));
        assertTrue(see(EVERYTHING, true).containsAll(List.of(Area.STYLES, Area.TAB, Area.ANNOUNCE)));
        assertEquals(List.of(), visible(EVERYTHING, g -> false, NONE), "not staff: nothing, so /admin <area> is 'unknown'");
        assertFalse(see(env(Set.of(), false), true).contains(Area.ANNOUNCE), "no proxy link, no announce");
        // config can raise or lower an area
        assertFalse(visible(EVERYTHING, g -> g == Gate.HUB, Map.of("games", "admin")).contains(Area.GAMES));
        assertTrue(visible(EVERYTHING, g -> g == Gate.HUB, Map.of("styles", "staff")).contains(Area.STYLES));
        // whitelist remove/on/off stay with dev/admin
        assertEquals(Gate.ADMIN, gate(WhitelistOp.REMOVE));
        assertEquals(Gate.HUB, gate(WhitelistOp.ADD));
    }
    @Test void ownerRoutesAreNamespacedAndBuiltOnlyFromTheConstants() {
        assertEquals("pulsegames:admin-route games maps list", AdminRoutes.GAMES.line("games", "maps list"));
        assertEquals("pulsegames:admin-route server", AdminRoutes.GAMES.line(AdminRoutes.GAMES.serverArea(), ""));
        assertEquals("pulseaddon:admin-route survival", AdminRoutes.SURVIVAL.line("survival", null));
        assertEquals("pumpkinhunt:admin-route hunt stats", AdminRoutes.HUNT.line("hunt", " stats "));
    }
    @Test void noCommandStringIsDispatchedOutsideTheConstantsClass() throws Exception {
        var dir = java.nio.file.Path.of("src/main/java/uk/co/playerready/pulsenetwork");
        var bad = new ArrayList<String>();
        try (var files = java.nio.file.Files.list(dir)) {
            for (var f : files.filter(x -> x.toString().endsWith(".java")).toList())
                for (var line : java.nio.file.Files.readAllLines(f))
                    if (line.matches(".*(performCommand|dispatchCommand|run_command:|runCommand)\\(?\\s*\"[^)]*") && !f.endsWith("AdminRoutes.java")) bad.add(f.getFileName() + ": " + line.trim());
        }
        assertEquals(List.of(), bad);
    }
    @Test void tabCompleteOffersOnlyVisibleAreasAndTheirVerbs() {
        var staff = see(EVERYTHING, false);
        Predicate<Gate> staffMay = g -> g == Gate.HUB;
        var names = List.of("Kit", "Kat");
        assertEquals(List.of("players", "roles", "nick", "identity", "whitelist", "games", "survival", "hunt", "server"), complete(staff, staffMay, names, new String[] {""}));
        assertEquals(List.of("hunt"), complete(staff, staffMay, names, new String[] {"h"}));
        assertEquals(List.of("add", "list", "status"), complete(staff, staffMay, names, new String[] {"whitelist", ""}));
        assertEquals(List.of("add", "remove", "on", "off", "list", "status"), complete(see(EVERYTHING, true), g -> true, names, new String[] {"whitelist", ""}));
        assertEquals(List.of("Kit", "Kat"), complete(staff, staffMay, names, new String[] {"nick", "K"}));
        assertEquals(List.of("reset"), complete(staff, staffMay, names, new String[] {"nick", "Kit", "r"}));
        assertEquals(List.of(), complete(staff, staffMay, names, new String[] {"tab", ""}), "staff do not see tab");
        assertEquals(List.of("hide", "show"), complete(see(EVERYTHING, true), g -> true, names, new String[] {"tab", ""}));
    }
    @Test void oldAdminWordsAreHiddenAliasesWithAHint() {
        assertEquals(Set.of("adminmenu", "am", "rolemenu"), HIDDEN_ALIASES);
        assertEquals(Optional.of("/am is moving: use /admin next time."), aliasHint("am", new String[0]));
        assertEquals(Optional.of("/adminmenu is moving: use /admin next time."), aliasHint("AdminMenu", new String[0]));
        assertEquals(Optional.of("/rolemenu is moving: use /admin roles Kit next time."), aliasHint("rolemenu", new String[] {"Kit"}));
        assertTrue(aliasHint("admin", new String[0]).isEmpty());
    }
    @Test void announceStylesMatchTheWire() {
        assertEquals(List.of("chat", "title", "bar"), ANNOUNCE_STYLES.stream().map(AnnounceStyle::style).toList());
        assertEquals(List.of("Chat", "Title", "Action bar"), ANNOUNCE_STYLES.stream().map(AnnounceStyle::label).toList());
        ANNOUNCE_STYLES.forEach(st -> assertEquals(st.style(), Announce.style(st.style())));
    }
    @Test void whitelistCommandsAreComposedFromValidNamesOnly() {
        assertEquals(Optional.of("uw add .BedrockKid"), whitelistCommand(WhitelistOp.ADD, ".BedrockKid"));
        assertEquals(Optional.of("uw remove Kit_1"), whitelistCommand(WhitelistOp.REMOVE, "Kit_1"));
        assertEquals(Optional.of("uw list"), whitelistCommand(WhitelistOp.LIST, null));
        assertEquals(Optional.of("uw on"), whitelistCommand(WhitelistOp.ON, null));
        assertEquals(Optional.of("uw off"), whitelistCommand(WhitelistOp.OFF, "ignored"));
        assertTrue(whitelistCommand(WhitelistOp.ADD, "Kit; op Kit").isEmpty());
        assertTrue(whitelistCommand(WhitelistOp.REMOVE, "").isEmpty());
    }
    @Test void gatingUsesDirectGroupsAndKeepsRiskyActionsForDevAndAdmin() {
        assertTrue(allowed(Gate.HUB, Set.of("staff")));
        assertFalse(allowed(Gate.ADMIN, Set.of("staff")));
        assertTrue(allowed(Gate.ADMIN, Set.of("admin")));
        assertTrue(allowed(Gate.ADMIN, Set.of("dev", "staff")));
        assertFalse(allowed(Gate.HUB, Set.of()));
        assertFalse(allowed(Gate.HUB, Set.of("default", "venue:ports")));
        assertEquals(Gate.HUB, gate(WhitelistOp.ADD));
        assertEquals(Gate.ADMIN, gate(WhitelistOp.REMOVE));
        assertEquals(Gate.ADMIN, gate(WhitelistOp.ON));
        assertEquals(Gate.ADMIN, gate(WhitelistOp.OFF));
    }
    @Test void styleGridHasEveryStyleOnceInDistinctSlotsAndMarksHeldOnes() {
        var held = Set.of("pulse.namestyle.red", "pulse.namestyle.rainbow");
        var cells = grid(held::contains);
        assertEquals(NameStyle.styleIds().size(), cells.size());
        assertEquals(16, cells.size());
        assertEquals(cells.size(), cells.stream().map(Cell::slot).distinct().count());
        assertTrue(cells.stream().allMatch(c -> c.slot() < BACK_SLOT));
        assertEquals(Set.of("red", "rainbow"), new HashSet<>(cells.stream().filter(Cell::held).map(Cell::id).toList()));
        assertEquals("pulse.namestyle.sunset", permission("sunset"));
    }
    @Test void auditLineHasActorActionTarget() {
        assertEquals("ADMIN Aurora grant style red Kit", audit("Aurora", "grant style red", "Kit"));
        assertEquals("ADMIN Aurora announce -", audit("Aurora", "announce", null));
        assertEquals(9, menuSize(1));
        assertEquals(54, menuSize(80));
    }
}
