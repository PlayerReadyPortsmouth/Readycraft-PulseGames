package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NetworkTabTest {
    static final TabConfig CONFIG = TabConfig.parse(new Properties());
    static TabConfig with(String key, String value) { var p = new Properties(); p.setProperty(key, value); return TabConfig.parse(p); }
    static String text(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    static TabRoster.Member member(String name, String server) { return new TabRoster.Member(UUID.nameUUIDFromBytes(name.getBytes()), name, server, 40, "", "", false, false, false, false); }

    @Test void groupsOwnServerFirstThenConfiguredOrderThenAlphabetical() {
        var all = List.of(member("zed", "survival"), member("Amy", "survival"), member("bob", "creative"), member("cat", "lobby"), member("dan", "skyblock"));
        var groups = TabRoster.groups(all, "lobby", List.of("survival", "creative"), Set.of("lobby", "survival", "creative", "skyblock"));
        assertEquals(List.of("lobby", "survival", "creative", "skyblock"), groups.stream().map(TabRoster.Group::server).toList());
        assertEquals(List.of("Amy", "zed"), groups.get(1).members().stream().map(TabRoster.Member::name).toList());
    }
    @Test void hiddenAndNonParticipatingServersAreOmitted() {
        var hidden = new TabRoster.Member(UUID.randomUUID(), "ghost", "lobby", 1, "", "", false, true, false, false);
        var groups = TabRoster.groups(List.of(hidden, member("a", "lobby"), member("b", "creative")), "lobby", List.of(), Set.of("lobby"));
        assertEquals(1, groups.size()); assertEquals(1, groups.getFirst().members().size());
    }
    @Test void displayNameWithAndWithoutPrefixAndBedrockMarker() {
        var plain = member("Alex", "lobby");
        assertEquals("Alex", text(TabRender.displayName(plain, CONFIG, false)));
        var ranked = new TabRoster.Member(plain.id(), "Alex", "lobby", 1, "&c[Admin]", "", true, false, true, true);
        assertEquals("[B] [Admin] Alex (AFK) [In a game]", text(TabRender.displayName(ranked, CONFIG, false)));
        // Player-controlled text is never parsed as MiniMessage.
        var tricky = new TabRoster.Member(plain.id(), "<red>x", "lobby", 1, "", "", false, false, false, false);
        assertEquals("<red>x", text(TabRender.displayName(tricky, CONFIG, false)));
    }
    @Test void rowsCarryListOrderAndOptionalGroupHeaders() {
        var groups = TabRoster.groups(List.of(member("a", "lobby"), member("b", "lobby"), member("c", "survival")), "lobby", List.of(), Set.of("lobby", "survival"));
        var rows = TabRender.rows(groups, "lobby", CONFIG, false);
        assertEquals(List.of(3, 2, 1), rows.stream().map(TabRender.Row::order).toList());
        assertTrue(rows.get(0).local()); assertFalse(rows.get(2).local());
        var headed = TabRender.rows(groups, "lobby", with("tab.group-headers", "true"), false);
        assertEquals(5, headed.size()); assertTrue(headed.get(0).header()); assertEquals("— Lobby (2) —", text(headed.get(0).display()));
        var ascending = TabRender.rows(groups, "lobby", with("tab.list-order-higher-first", "false"), false);
        assertEquals(List.of(0, 1, 2), ascending.stream().map(TabRender.Row::order).toList());
    }
    TabRender.Context context(ServerStatus status, boolean staff, boolean bedrock) {
        return new TabRender.Context("Alex", "survival", new LinkedHashMap<>(Map.of("survival", 12)), 15, status, staff, bedrock, "1.21.4", 100);
    }
    @Test void headerFooterRenderWithMissingStatusAndDropBlankLines() {
        var hf = TabRender.headerFooter(CONFIG, context(null, false, false));
        assertTrue(text(hf[0]).contains("You are on Survival") && text(hf[0]).contains("15 online"));
        assertEquals("Survival 12", text(hf[1])); // no status line, no tip line
        var known = TabRender.headerFooter(with("tab.tips", "Be kind|Have fun"), context(new ServerStatus(19.96, 12.3, 12, Map.of()), false, false));
        assertTrue(text(known[1]).contains("TPS 20.0 · MSPT 12.3"));
        assertTrue(text(known[1]).endsWith("Be kind") || text(known[1]).endsWith("Have fun"));
    }
    @Test void staffLineOnlyForStaffAndBedrockFallbackStripsHoverAndObfuscation() {
        assertFalse(text(TabRender.headerFooter(CONFIG, context(null, false, false))[1]).contains("Client"));
        assertTrue(text(TabRender.headerFooter(CONFIG, context(null, true, true))[1]).contains("Client: Bedrock · 1.21.4"));
        var fancy = with("tab.header", "<obfuscated><click:run_command:/op>hi</click></obfuscated><hover:show_text:'x'>there</hover>");
        var java = TabRender.headerFooter(fancy, context(null, false, false))[0];
        var bedrock = TabRender.headerFooter(fancy, context(null, false, true))[0];
        assertEquals("hithere", text(bedrock)); assertEquals(text(java), text(bedrock));
        assertTrue(java.children().stream().anyMatch(c -> c.clickEvent() != null || c.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE));
        assertTrue(bedrock.children().stream().allMatch(c -> c.clickEvent() == null && c.hoverEvent() == null && c.decoration(TextDecoration.OBFUSCATED) != TextDecoration.State.TRUE));
    }
    @Test void tipsRotateAndInvalidConfigIsRejected() {
        var tips = with("tab.tips", "one|two");
        var a = text(TabRender.headerFooter(tips, context(null, false, false))[1]);
        var ctx = context(null, false, false);
        var later = new TabRender.Context(ctx.viewer(), ctx.viewerServer(), ctx.counts(), ctx.networkOnline(), null, false, false, "1", 130);
        assertNotEquals(a.lines().toList().getLast(), text(TabRender.headerFooter(tips, later)[1]).lines().toList().getLast());
        assertThrows(IllegalArgumentException.class, () -> with("tab.server-order", "../x"));
        assertFalse(CONFIG.enabled());
    }

    // --- diff updates against a fake tab list ---
    static final class FakeEntry implements TabListEntry {
        final TabList list; final GameProfile profile; Component name; int latency, order, mode; int writes;
        FakeEntry(TabList l, GameProfile p, Component n, int latency, int order) { list = l; profile = p; name = n; this.latency = latency; this.order = order; }
        public ChatSession getChatSession() { return null; } public IdentifiedKey getIdentifiedKey() { return null; }
        public TabList getTabList() { return list; } public GameProfile getProfile() { return profile; }
        public Optional<Component> getDisplayNameComponent() { return Optional.ofNullable(name); }
        public TabListEntry setDisplayName(Component c) { name = c; writes++; return this; }
        public int getLatency() { return latency; } public TabListEntry setLatency(int l) { latency = l; writes++; return this; }
        public int getGameMode() { return mode; } public TabListEntry setGameMode(int g) { mode = g; return this; }
        public int getListOrder() { return order; } public TabListEntry setListOrder(int o) { order = o; writes++; return this; }
    }
    static TabList fakeList(Map<UUID, FakeEntry> entries) {
        TabList list = mock(TabList.class);
        when(list.getEntry(any())).thenAnswer(i -> Optional.ofNullable(entries.get(i.<UUID>getArgument(0))));
        when(list.containsEntry(any())).thenAnswer(i -> entries.containsKey(i.<UUID>getArgument(0)));
        when(list.removeEntry(any())).thenAnswer(i -> Optional.ofNullable(entries.remove(i.<UUID>getArgument(0))));
        when(list.buildEntry(any(), any(), anyInt(), anyInt(), any(), anyBoolean(), anyInt(), anyBoolean())).thenAnswer(i ->
                new FakeEntry(list, i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(6)));
        doAnswer(i -> { TabListEntry e = i.getArgument(0); entries.put(e.getProfile().getId(), (FakeEntry) e); return null; }).when(list).addEntry(any());
        return list;
    }
    @Test void diffAddsRemoteMutatesLocalOnlyOnChangeAndRemovesOnlyOwned() {
        var entries = new HashMap<UUID, FakeEntry>(); var tab = fakeList(entries);
        var local = member("local", "lobby"); var far = member("far", "survival");
        var backendOwned = new FakeEntry(tab, new GameProfile(local.id(), "local", List.of()), Component.text("local"), 40, 0);
        entries.put(local.id(), backendOwned);
        var owned = new HashSet<UUID>();
        var rows = TabRender.rows(TabRoster.groups(List.of(local, far), "lobby", List.of(), Set.of("lobby", "survival")), "lobby", CONFIG, false);
        assertEquals(2, TabSync.apply(tab, rows, owned)); // one add (far) + one in-place update of the backend's entry (name, order, ping)
        assertEquals(Set.of(far.id()), owned); assertTrue(entries.containsKey(far.id()));
        int writes = backendOwned.writes;
        assertEquals(0, TabSync.apply(tab, rows, owned)); assertEquals(writes, backendOwned.writes); // identical state sends nothing
        // Local player not yet sent by the backend is skipped, not added.
        var late = member("late", "lobby");
        TabSync.apply(tab, TabRender.rows(TabRoster.groups(List.of(local, far, late), "lobby", List.of(), Set.of("lobby", "survival")), "lobby", CONFIG, false), owned);
        assertFalse(entries.containsKey(late.id()));
        // Remote player leaves: removed. Local entry is never removed even when omitted (backend owns it).
        TabSync.apply(tab, List.of(), owned);
        assertFalse(entries.containsKey(far.id())); assertTrue(entries.containsKey(local.id()));
    }
    @Test void changedPingUpdatesOnlyLatencyAndQuantisationAbsorbsJitter() {
        assertEquals(TabRender.quantise(41), TabRender.quantise(48)); assertEquals(0, TabRender.quantise(-5)); assertEquals(9999, TabRender.quantise(1_000_000));
        var entries = new HashMap<UUID, FakeEntry>(); var tab = fakeList(entries);
        var far = member("far", "survival"); var owned = new HashSet<UUID>();
        TabSync.apply(tab, TabRender.rows(TabRoster.groups(List.of(far), "lobby", List.of(), Set.of("lobby", "survival")), "lobby", CONFIG, false), owned);
        var moved = new TabRoster.Member(far.id(), "far", "survival", 400, "", "", false, false, false, false);
        assertEquals(1, TabSync.apply(tab, TabRender.rows(TabRoster.groups(List.of(moved), "lobby", List.of(), Set.of("lobby", "survival")), "lobby", CONFIG, false), owned));
        assertEquals(400, entries.get(far.id()).latency);
    }

    // --- service: allowlist, stale status, no leakage ---
    @Test void serviceIgnoresStatusFromUnlistedServersAndStaleStatusReadsAsUnknown() throws Exception {
        var clock = new Clock() {
            Instant now = Instant.parse("2026-10-28T12:00:00Z");
            public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId z) { return this; } public Instant instant() { return now; }
        };
        var service = new TabService(mock(ProxyServer.class), mock(Logger.class), with("tab.excluded-servers", "creative"), Set.of("lobby", "creative"), false, clock);
        service.status("creative", List.of("20", "5", "1")); service.status("rogue", List.of("20", "5", "1")); service.status("lobby", List.of("20", "5", "1"));
        var field = TabService.class.getDeclaredField("statuses"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var map = (Map<String, ?>) field.get(service);
        assertEquals(Set.of("lobby"), map.keySet());
        var read = TabService.class.getDeclaredMethod("status", String.class); read.setAccessible(true);
        assertNotNull(read.invoke(service, "lobby")); clock.now = clock.now.plusSeconds(21); assertNull(read.invoke(service, "lobby"));
    }
    @Test void tickBuildsAnEndToEndListForAViewerOnAnotherServer() {
        var proxy = mock(ProxyServer.class);
        var viewer = player("viewer", "lobby", 30); var remote = player("remote", "survival", 90); var excluded = player("hidden-server", "creative", 10);
        when(proxy.getAllPlayers()).thenReturn(List.of(viewer, remote, excluded));
        var entries = new HashMap<UUID, FakeEntry>(); var tab = fakeList(entries); when(viewer.getTabList()).thenReturn(tab);
        var service = new TabService(proxy, mock(Logger.class), with("tab.excluded-servers", "creative"), Set.of("lobby", "survival", "creative"), false, Clock.systemUTC());
        service.tick();
        assertEquals(Set.of(remote.getUniqueId()), entries.keySet());
        verify(tab).setHeaderAndFooter(any(), any());
        service.tick(); verify(tab, times(1)).setHeaderAndFooter(any(), any()); // unchanged: no resend, no rebuild
    }
    Player player(String name, String server, long ping) {
        Player p = mock(Player.class); when(p.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes())); when(p.getUsername()).thenReturn(name); when(p.getPing()).thenReturn(ping);
        ServerConnection c = mock(ServerConnection.class); when(c.getServerInfo()).thenReturn(new ServerInfo(server, new InetSocketAddress("localhost", 25566)));
        when(p.getCurrentServer()).thenReturn(Optional.of(c));
        when(p.getProtocolVersion()).thenReturn(com.velocitypowered.api.network.ProtocolVersion.MINECRAFT_1_21_4);
        return p;
    }
}
