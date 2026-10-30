package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class TabLayoutTest {
    static final TabConfig COLUMNS = NetworkTabTest.with("tab.layout", "columns");
    static final Set<String> ALL = Set.of("lobby", "mini", "surv", "creative", "sky");

    static List<TabRoster.Member> people(String server, int n) {
        return IntStream.range(0, n).mapToObj(i -> NetworkTabTest.member(server + String.format("%02d", i), server)).toList();
    }
    static List<TabRoster.Group> groups(String viewer, Object... spec) {
        var all = new ArrayList<TabRoster.Member>();
        for (int i = 0; i < spec.length; i += 2) all.addAll(people((String) spec[i], (Integer) spec[i + 1]));
        return TabRoster.groups(all, viewer, List.of(), ALL);
    }
    static TabLayout.Plan plan(TabConfig c, String viewer, Object... spec) { return TabLayout.plan(groups(viewer, spec), viewer, c, ALL).orElseThrow(); }
    static long count(TabLayout.Plan p, TabLayout.Kind k) { return p.slots().stream().filter(s -> s.kind() == k).count(); }

    @Test void oneToFourServersGetOneTwentyRowColumnEach() {
        for (int servers = 1; servers <= 4; servers++) {
            var spec = new ArrayList<Object>();
            for (int i = 0; i < servers; i++) { spec.add(List.of("lobby", "mini", "surv", "creative").get(i)); spec.add(3); }
            var p = plan(COLUMNS, "lobby", spec.toArray());
            assertEquals(servers, p.columns()); assertEquals(servers * 20, p.slots().size());
            assertEquals(servers, count(p, TabLayout.Kind.HEADER)); assertEquals(servers * 3, count(p, TabLayout.Kind.MEMBER));
            assertEquals(servers * 16, count(p, TabLayout.Kind.FILLER));
            for (int c = 0; c < servers; c++) assertEquals(TabLayout.Kind.HEADER, p.slots().get(c * 20).kind());
        }
    }
    @Test void ownServerIsTheFirstColumn() {
        var p = plan(COLUMNS, "mini", "lobby", 2, "mini", 2);
        assertEquals("mini", p.slots().get(0).server()); assertEquals("lobby", p.slots().get(20).server());
    }
    @Test void emptyServersAreSkippedUnlessShowEmpty() {
        assertEquals(1, plan(COLUMNS, "lobby", "lobby", 2).columns());
        var shown = TabLayout.plan(groups("lobby", "lobby", 2), "lobby", withEmpty(), Set.of("lobby", "mini")).orElseThrow();
        assertEquals(2, shown.columns());
        assertEquals(19, shown.slots().stream().filter(s -> s.server().equals("mini") && s.kind() == TabLayout.Kind.FILLER).count());
    }
    static TabConfig withEmpty() {
        var p = new Properties(); p.setProperty("tab.layout", "columns"); p.setProperty("tab.columns.show-empty", "true"); return TabConfig.parse(p);
    }
    @Test void nineteenPlayersFillTheColumnExactly() {
        var p = plan(COLUMNS, "lobby", "lobby", 19);
        assertEquals(1, p.columns()); assertEquals(0, count(p, TabLayout.Kind.FILLER)); assertEquals(0, count(p, TabLayout.Kind.MORE));
    }
    @Test void twentyPlayersTakeASecondColumnWhenOneIsFree() {
        var p = plan(COLUMNS, "lobby", "lobby", 20, "mini", 2);
        assertEquals(3, p.columns()); assertEquals(22, count(p, TabLayout.Kind.MEMBER));
        assertEquals(0, count(p, TabLayout.Kind.MORE)); assertEquals(60 - 2 - 22, count(p, TabLayout.Kind.FILLER));
    }
    @Test void twentyOnePlayersOnTheViewersServerFallBackWhenNoColumnIsSpare() {
        assertTrue(TabLayout.plan(groups("lobby", "lobby", 21, "mini", 1, "surv", 1, "creative", 1), "lobby", COLUMNS, ALL).isEmpty());
    }
    @Test void overflowOnAnotherServerBecomesPlusMoreWhenNoColumnIsSpare() {
        var p = plan(COLUMNS, "lobby", "lobby", 2, "mini", 45, "surv", 2, "creative", 2);
        var more = p.slots().stream().filter(s -> s.kind() == TabLayout.Kind.MORE).toList();
        assertEquals(1, more.size()); assertEquals("mini", more.getFirst().server()); assertEquals(45 - 18, more.getFirst().n());
        assertEquals(18, p.slots().stream().filter(s -> s.server().equals("mini") && s.kind() == TabLayout.Kind.MEMBER).count());
        assertEquals(4, p.columns());
    }
    @Test void fortyFivePlayersWithSparesGetThreeColumnsAndAMoreRow() {
        var p = plan(COLUMNS, "mini", "mini", 45, "lobby", 1);
        assertEquals(4, p.columns()); // 45 players need 3 columns, lobby 1; fits without hiding
        assertEquals(0, count(p, TabLayout.Kind.MORE));
        assertEquals(45, p.slots().stream().filter(s -> s.kind() == TabLayout.Kind.MEMBER && s.server().equals("mini")).count());
    }
    @Test void moreThanFourPopulatedServersFallBack() {
        assertTrue(TabLayout.plan(groups("lobby", "lobby", 1, "mini", 1, "surv", 1, "creative", 1, "sky", 1), "lobby", COLUMNS, ALL).isEmpty());
        assertTrue(TabLayout.plan(List.of(), "lobby", COLUMNS, ALL).isEmpty());
    }
    @Test void fillerIdsAreStableUniqueAndSortAfterRealNames() {
        var a = TabRender.rows(plan(COLUMNS, "lobby", "lobby", 2, "mini", 3), "lobby", COLUMNS, 0);
        var b = TabRender.rows(plan(COLUMNS, "lobby", "lobby", 2, "mini", 4), "lobby", COLUMNS, 0);
        var fa = a.stream().filter(r -> r.id().equals(TabLayout.fillerId("lobby", 5))).count();
        assertEquals(1, fa);
        assertEquals(a.size(), a.stream().map(TabRender.Row::id).distinct().count());
        var fillers = a.stream().filter(r -> r.name().startsWith("~p")).toList();
        assertEquals(33, fillers.size());
        assertTrue(fillers.stream().allMatch(r -> r.name().length() <= 16 && r.name().compareTo("zzzzzzzzzzzzzzzz") > 0 && r.header() && !r.local()));
        assertEquals(" ", NetworkTabTest.text(fillers.getFirst().display()));
        // Another player joining mini removes exactly one filler id and leaves every other filler untouched.
        var ids = a.stream().filter(r -> r.name().startsWith("~p")).map(TabRender.Row::id).collect(Collectors.toSet());
        var after = b.stream().filter(r -> r.name().startsWith("~p")).map(TabRender.Row::id).collect(Collectors.toSet());
        assertEquals(1, ids.size() - after.size()); assertTrue(ids.containsAll(after));
    }
    @Test void orderValuesStayInsideTheirColumnAndIgnorePlanSize() {
        var one = TabRender.rows(plan(COLUMNS, "lobby", "lobby", 2), "lobby", COLUMNS, 0);
        var two = TabRender.rows(plan(COLUMNS, "lobby", "lobby", 2, "mini", 1), "lobby", COLUMNS, 0);
        assertEquals(80, one.getFirst().order()); assertEquals(80, two.getFirst().order());
        assertEquals(one.stream().map(TabRender.Row::order).toList(), two.subList(0, 20).stream().map(TabRender.Row::order).toList());
        assertEquals(61, two.get(19).order()); assertEquals(60, two.get(20).order());
        var asc = TabRender.rows(plan(NetworkTabTest.with("tab.list-order-higher-first", "false"), "lobby", "lobby", 2), "lobby", NetworkTabTest.with("tab.list-order-higher-first", "false"), 0);
        assertEquals(0, asc.getFirst().order());
    }
    @Test void localRowsMarkTheViewersServerAndHeaderShowsCount() {
        var rows = TabRender.rows(plan(COLUMNS, "lobby", "lobby", 2, "mini", 1), "lobby", COLUMNS, 0);
        assertTrue(rows.get(1).local()); assertFalse(rows.get(20 + 1).local());
        assertEquals("— Lobby (2) —", NetworkTabTest.text(rows.getFirst().display()));
    }
    @Test void fallbackDecisionNeedsModernJava() {
        assertTrue(TabLayout.eligible(false, true));
        assertFalse(TabLayout.eligible(true, true)); assertFalse(TabLayout.eligible(false, false));
    }
    @Test void configKeysDefaultToListAndRejectUnknownLayouts() {
        assertFalse(NetworkTabTest.CONFIG.columns()); assertFalse(NetworkTabTest.CONFIG.showEmpty()); assertEquals("", NetworkTabTest.CONFIG.filler());
        assertTrue(COLUMNS.columns());
        assertThrows(IllegalArgumentException.class, () -> NetworkTabTest.with("tab.layout", "grid"));
    }
    @Test void customFillerRendersAndEmptyOneIsASpace() {
        var c = NetworkTabTest.with("tab.columns.filler", "<dark_gray>.");
        var p = TabLayout.plan(groups("lobby", "lobby", 1), "lobby", c, ALL).orElseThrow();
        var row = TabRender.rows(p, "lobby", c, 0).get(5);
        assertEquals(".", NetworkTabTest.text(row.display()));
    }
}
