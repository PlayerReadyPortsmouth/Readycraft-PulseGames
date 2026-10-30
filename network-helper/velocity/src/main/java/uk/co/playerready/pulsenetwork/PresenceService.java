package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Proxy-owned join, hop and leave lines. Decisions live in {@link PresenceTracker}; this class only gathers inputs and delivers. */
final class PresenceService {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Object plugin;
    private final PresenceConfig config;
    private final NameStyle.Tags tags;
    private final BiFunction<Player, String, NameStyle.Identity> identity;
    private final Predicate<Player> hiddenCheck;
    private final Function<String, String> serverName;
    private final PresenceTracker tracker;
    private final Map<UUID, String> lastServer = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenLast = ConcurrentHashMap.newKeySet();
    private volatile boolean stopping;

    PresenceService(ProxyServer proxy, Logger logger, Object plugin, PresenceConfig config, NameStyle.Tags tags,
                    BiFunction<Player, String, NameStyle.Identity> identity, Predicate<Player> hidden, Function<String, String> serverName, Clock clock) {
        this.proxy = proxy; this.logger = logger; this.plugin = plugin; this.config = config; this.tags = tags;
        this.identity = identity; this.hiddenCheck = hidden; this.serverName = serverName;
        this.tracker = new PresenceTracker(config.rules(), clock);
    }
    void stopping() { stopping = true; }

    private boolean hidden(Player p, NameStyle.Identity id) {
        boolean now;
        try { now = hiddenCheck.test(p) || id.role() != null && config.hiddenRoles().contains(id.role()); }
        catch (RuntimeException e) { now = true; } // If unsure, say nothing.
        if (now) hiddenLast.add(p.getUniqueId()); else hiddenLast.remove(p.getUniqueId());
        return now;
    }

    @Subscribe public void connected(ServerPostConnectEvent event) {
        Player p = event.getPlayer();
        String server = p.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse(null);
        if (server == null) return;
        String from = event.getPreviousServer() == null ? null : event.getPreviousServer().getServerInfo().getName();
        if (server.equals(from)) return;
        try {
            var id = identity.apply(p, server);
            boolean hide = hidden(p, id);
            lastServer.put(p.getUniqueId(), server);
            if (from == null) {
                var line = tracker.join(p.getUniqueId(), hide);
                if (line == PresenceTracker.Line.JOIN) deliver(p, config.join(), id, server, null, Set.of(server));
                if (tracker.firstJoinToday(p.getUniqueId())) welcome(p, id);
            } else if (tracker.hop(p.getUniqueId(), hide) == PresenceTracker.Line.SWITCH)
                deliver(p, config.hop(), id, server, from, Set.of(server, from));
        } catch (RuntimeException e) { logger.warn("Presence line failed: {}", e.getClass().getSimpleName()); }
    }
    @Subscribe public void quit(DisconnectEvent event) {
        Player p = event.getPlayer(); UUID uid = p.getUniqueId();
        String server = lastServer.remove(uid);
        boolean wasHidden = hiddenLast.remove(uid);
        try {
            if (server == null) return;
            var id = identity.apply(p, server);
            boolean hide = wasHidden || hidden(p, id);
            hiddenLast.remove(uid);
            if (tracker.leave(uid, hide) == PresenceTracker.Line.LEAVE && !stopping) deliver(p, config.leave(), id, server, null, Set.of(server));
        } catch (RuntimeException e) { logger.warn("Presence line failed: {}", e.getClass().getSimpleName()); }
    }
    private void welcome(Player p, NameStyle.Identity id) {
        proxy.getScheduler().buildTask(plugin, () -> {
            if (p.isActive()) p.sendMessage(PresenceRenderer.render(config.welcome(), id, bedrock(p), "", null, tags));
        }).delay(Duration.ofSeconds(2)).schedule();
    }
    private static boolean bedrock(Player p) { return p.getUniqueId().getMostSignificantBits() == 0; } // Floodgate UUIDs
    /** {@code scope} is used only when {@code presence.scope=servers}; the subject never sees their own line. */
    private void deliver(Player subject, String template, NameStyle.Identity id, String server, String from, Set<String> scope) {
        Component[] lines = new Component[2];
        String to = serverName.apply(server), origin = from == null ? null : serverName.apply(from);
        for (Player receiver : proxy.getAllPlayers()) {
            if (receiver.getUniqueId().equals(subject.getUniqueId())) continue;
            if (config.serversOnly() && receiver.getCurrentServer().map(c -> !scope.contains(c.getServerInfo().getName())).orElse(true)) continue;
            int slot = bedrock(receiver) ? 1 : 0;
            if (lines[slot] == null) lines[slot] = PresenceRenderer.render(template, id, slot == 1, to, origin, tags);
            receiver.sendMessage(lines[slot]);
        }
    }
}
