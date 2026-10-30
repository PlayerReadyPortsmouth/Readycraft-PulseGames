package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxy-owned network tab list. Never logs player names, roster contents or secrets. Backend status is only
 * trusted after VelocityBridge has authenticated the signed STATUS message and checked its sender.
 */
final class TabService {
    static final long STATUS_TTL_MS = 20_000, PERIODIC_MS = 5_000, SETTLE_MS = 4_000;
    private record Seen(ServerStatus status, long at) {}
    private static final class Viewer {
        final Set<UUID> owned = ConcurrentHashMap.newKeySet();
        volatile long lastUpdate, lastPeriodic, settleUntil, lastFrame;
        volatile Component[] lastHeaderFooter;
    }
    private final ProxyServer proxy;
    private final Logger logger;
    private final TabConfig config;
    private final Set<String> participating;
    private final Clock clock;
    private final OptionalPlugins optional = new OptionalPlugins();
    private final LuckPermsMeta lpMeta = new LuckPermsMeta();
    private final boolean floodgate;
    private final Map<String, Seen> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private volatile boolean everyoneDirty = true;
    private long lastWarning;

    TabService(ProxyServer proxy, Logger logger, TabConfig config, Set<String> backends, boolean floodgate, Clock clock) {
        this.proxy = proxy; this.logger = logger; this.config = config; this.clock = clock; this.floodgate = floodgate;
        var servers = new HashSet<>(backends); servers.removeAll(config.excluded());
        this.participating = Set.copyOf(servers);
    }

    /** Called with an already authenticated STATUS message from {@code server}. */
    void status(String server, List<String> fields) {
        if (!participating.contains(server)) return;
        ServerStatus.parse(fields).ifPresent(s -> statuses.put(server, new Seen(s, clock.millis())));
    }
    private ServerStatus status(String server) {
        Seen seen = statuses.get(server);
        return seen == null || clock.millis() - seen.at() > STATUS_TTL_MS ? null : seen.status();
    }

    /** Level from the authenticated backend report for {@code server}, or null when unknown or stale. */
    Integer level(String server, UUID id) {
        var s = status(server);
        return s == null ? null : s.levels().get(id);
    }
    NameStyle.Tags tags() { return config.tags(); }

    @Subscribe public void connected(ServerPostConnectEvent event) {
        var viewer = viewers.computeIfAbsent(event.getPlayer().getUniqueId(), id -> new Viewer());
        viewer.owned.clear(); // Velocity cleared the old server's list; backend entries for this server arrive shortly.
        viewer.lastHeaderFooter = null; viewer.settleUntil = clock.millis() + SETTLE_MS;
        everyoneDirty = true;
    }
    @Subscribe public void quit(DisconnectEvent event) { viewers.remove(event.getPlayer().getUniqueId()); everyoneDirty = true; }

    /** One pass over all viewers; schedule at {@code config.minIntervalMs()}. */
    void tick() {
        long now = clock.millis();
        boolean dirty = everyoneDirty; everyoneDirty = false;
        statuses.values().removeIf(s -> now - s.at() > STATUS_TTL_MS * 3);
        var members = new ArrayList<TabRoster.Member>();
        boolean animated = false;
        var players = List.copyOf(proxy.getAllPlayers());
        for (Player p : players) {
            String server = server(p);
            if (server == null || !participating.contains(server)) continue;
            var flags = status(server) == null ? 0 : status(server).flags().getOrDefault(p.getUniqueId(), 0);
            String[] meta = optional.meta(p.getUniqueId());
            var identity = IdentityResolver.resolve(p.getUniqueId(), p.getUsername(), lpMeta, level(server, p.getUniqueId()), config.tags());
            animated |= NameStyle.isAnimated(identity.styleId());
            members.add(new TabRoster.Member(p.getUniqueId(), p.getUsername(), server, (int) Math.min(9999, Math.max(0, p.getPing())), meta[0], meta[1],
                    bedrock(p), (flags & ServerStatus.HIDDEN) != 0 || p.hasPermission("pulsenetwork.tab.hidden"),
                    (flags & ServerStatus.AFK) != 0, (flags & ServerStatus.MATCH) != 0, identity.role(), identity.styleId(), identity.level()));
        }
        long frame = TabRender.frame(now, config);
        var cache = new HashMap<String, List<TabRender.Row>>();
        var groupCache = new HashMap<String, List<TabRoster.Group>>();
        for (Player viewer : players) {
            var state = viewers.computeIfAbsent(viewer.getUniqueId(), id -> new Viewer());
            String server = server(viewer);
            try {
                if (server == null) continue;
                if (!participating.contains(server)) { release(viewer, state); continue; }
                boolean periodic = now - state.lastPeriodic >= PERIODIC_MS;
                boolean newFrame = animated && frame != state.lastFrame; // static rows are identical across frames, so TabSync sends nothing for them
                if (!(dirty || periodic || newFrame || now < state.settleUntil) || now - state.lastUpdate < config.minIntervalMs()) continue;
                state.lastUpdate = now; state.lastFrame = frame; if (periodic) state.lastPeriodic = now;
                boolean bedrock = bedrock(viewer);
                var groups = groupCache.computeIfAbsent(server, s -> TabRoster.groups(members, s, config.order(), participating));
                var rows = cache.computeIfAbsent(server + (bedrock ? "/b" : "/j/" + (animated ? frame : 0)), k -> TabRender.rows(groups, server, config, bedrock, frame));
                TabSync.apply(viewer.getTabList(), rows, state.owned);
                var counts = new LinkedHashMap<String, Integer>();
                int total = 0;
                for (var g : groups) { counts.put(g.server(), g.members().size()); total += g.members().size(); }
                var hf = TabRender.headerFooter(config, new TabRender.Context(viewer.getUsername(), server, counts, total, status(server),
                        viewer.hasPermission("pulsenetwork.tab.staff"), bedrock, viewer.getProtocolVersion().getMostRecentSupportedVersion(), now / 1000));
                if (state.lastHeaderFooter == null || !Arrays.equals(state.lastHeaderFooter, hf)) {
                    viewer.getTabList().setHeaderAndFooter(hf[0], hf[1]); state.lastHeaderFooter = hf;
                }
            } catch (RuntimeException failure) {
                if (now - lastWarning > 60_000) { lastWarning = now; logger.warn("Network tab update failed for a viewer: {}", failure.getClass().getSimpleName()); }
            }
        }
    }
    /** Viewer moved to an excluded or non-allowlisted server: hand the list back to the backend. */
    private void release(Player viewer, Viewer state) {
        if (state.owned.isEmpty() && state.lastHeaderFooter == null) return;
        TabSync.apply(viewer.getTabList(), List.of(), state.owned);
        if (state.lastHeaderFooter != null) viewer.getTabList().clearHeaderAndFooter();
        state.lastHeaderFooter = null;
    }
    private static String server(Player p) { return p.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse(null); }
    private boolean bedrock(Player p) {
        if (!floodgate) return false;
        try { return BedrockCheck.bedrock(p.getUniqueId()); } catch (RuntimeException | LinkageError e) { return false; }
    }
}
