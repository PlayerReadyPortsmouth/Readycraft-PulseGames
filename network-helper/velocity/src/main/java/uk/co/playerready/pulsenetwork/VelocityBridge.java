package uk.co.playerready.pulsenetwork;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import java.nio.file.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id="pulsenetwork", name="PulseNetwork", version="0.1.1", description="Public chat relay and minigame queue joins",
        dependencies = {@com.velocitypowered.api.plugin.Dependency(id="floodgate", optional=true)})
public final class VelocityBridge {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(Wire.CHANNEL);
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    private Wire wire;
    private LocalChatIngress ingress;
    private Set<String> backends = Set.of(), queueServers = Set.of();
    private Set<String> chatBackends;
    private record Visit(String origin, String destination, String request) {}
    private final Map<UUID, Visit> visits = new ConcurrentHashMap<>();
    private final Set<UUID> returning = ConcurrentHashMap.newKeySet();
    private final QueueDirectory queues = new QueueDirectory();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chatTimes = new ConcurrentHashMap<>(), joinTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> announcements = new ConcurrentHashMap<>();
    private record Pending(String request, QueueDirectory.Queue queue, long expires) {}
    @Inject public VelocityBridge(ProxyServer proxy, Logger logger, @DataDirectory Path directory) {
        this.proxy = proxy; this.logger = logger; this.directory = directory;
    }
    @Subscribe public void start(ProxyInitializeEvent event) {
        try {
            Files.createDirectories(directory);
            Path path = directory.resolve("network.properties");
            if (!Files.exists(path)) Files.writeString(path, "enabled=false\nbridge-secret=\nbackends=lobby,survival,creative,skyblock,minigames,minigamestest,survivaltest,sessions,aoc\nqueue-servers=minigamestest\nlocal-ingress-port=0\nlocal-ingress-backends=aoc\n");
            Properties config = new Properties(); try (var reader = Files.newBufferedReader(path)) { config.load(reader); }
            if (!Boolean.parseBoolean(config.getProperty("enabled"))) { logger.info("PulseNetwork is disabled until configured."); return; }
            wire = new Wire(config.getProperty("bridge-secret"));
            backends = csv(config.getProperty("backends", "")); queueServers = csv(config.getProperty("queue-servers", ""));
            chatBackends = csv(config.getProperty("chat-backends", config.getProperty("backends", "")));
            if (!backends.containsAll(chatBackends)) throw new IllegalArgumentException("Chat backends must be allowed backends");
            if (backends.isEmpty() || !backends.containsAll(queueServers)) throw new IllegalArgumentException("Queue servers must be in the backend allowlist");
            int ingressPort = Integer.parseInt(config.getProperty("local-ingress-port", "0"));
            if (ingressPort != 0) {
                if (ingressPort < 1024 || ingressPort > 65535) throw new IllegalArgumentException("Invalid loopback ingress port");
                Set<String> modded = csv(config.getProperty("local-ingress-backends", ""));
                if (!backends.containsAll(modded)) throw new IllegalArgumentException("Modded servers must be in the backend allowlist");
                ingress = new LocalChatIngress(ingressPort, wire, message -> {
                    String backend = message.fields().get(0);
                    Player player = proxy.getPlayer(message.player()).orElse(null);
                    if (player == null || !modded.contains(backend) || player.getCurrentServer()
                            .filter(c -> c.getServerInfo().getName().equals(backend)).isEmpty()) return false;
                    chat(player, backend, message.fields().get(1));return true;
                });
            }
        } catch (IOException | IllegalArgumentException e) { wire = null; logger.error("PulseNetwork configuration is invalid: {}", e.getMessage()); return; }
        proxy.getChannelRegistrar().register(CHANNEL);
        var commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("queues").plugin(this).build(), (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player p) showQueues(p, invocation.arguments().length > 0 && invocation.arguments()[0].equalsIgnoreCase("chat"));
        });
        commands.register(commands.metaBuilder("joinqueue").plugin(this).build(), (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player p) {
                if (invocation.arguments().length == 1) join(p, invocation.arguments()[0]); else showQueues(p, false);
            }
        });
        proxy.getScheduler().buildTask(this, this::retryJoins).repeat(Duration.ofSeconds(1)).schedule();
    }
    private static Set<String> csv(String value) {
        var set = new HashSet<String>();
        for (String s : value.split(",")) if (!s.isBlank()) {
            if (!Wire.id(s.trim())) throw new IllegalArgumentException("Invalid backend name"); set.add(s.trim());
        }
        return Set.copyOf(set);
    }
    @Subscribe public void message(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;
        // Always consume our channel before checking source, including spoofed client packets.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (wire == null || !(event.getSource() instanceof ServerConnection connection)) return;
        String server = connection.getServerInfo().getName();
        if (!backends.contains(server) || !(event.getTarget() instanceof Player carrier)
                || !carrier.getUniqueId().equals(connection.getPlayer().getUniqueId())
                || carrier.getCurrentServer().filter(s -> s == connection).isEmpty()) return;
        wire.decode(event.getData()).filter(m -> m.player().equals(carrier.getUniqueId())).ifPresent(m -> {
            try {
                switch (m.kind()) {
                    case "CHAT" -> { if (m.fields().size() == 1) chat(carrier, server, m.fields().getFirst()); }
                    case "QUEUE" -> {
                        if (!queueServers.contains(server) || m.fields().size() != 4) return;
                        var f = m.fields(); String id = f.get(0);
                        boolean known = queues.list().stream().anyMatch(q -> q.server().equals(server) && q.instance().equals(id));
                        var q = queues.update(server, id, f.get(1), Integer.parseInt(f.get(2)), Integer.parseInt(f.get(3)));
                        if (!known) announce(q);
                    }
                    case "WITHDRAW" -> { if (queueServers.contains(server) && m.fields().size() == 1) queues.remove(server, m.fields().getFirst()); }
                    case "RETURN" -> {
                        Visit visit = visits.get(carrier.getUniqueId());
                        if (visit != null && visit.destination().equals(server) && m.fields().equals(List.of(visit.request()))) returnHome(carrier, visit);
                    }
                    case "ACK" -> {
                        Pending request = pending.get(carrier.getUniqueId());
                        if (request != null && request.queue().server().equals(server) && m.fields().equals(List.of(request.request()))) pending.remove(carrier.getUniqueId(), request);
                    }
                    default -> { }
                }
            } catch (IllegalArgumentException ignored) { /* Reject malformed messages without logging user text/secrets. */ }
        });
    }
    private void chat(Player player, String server, String body) {
        Set<String> chatAllowed = chatBackends == null ? backends : chatBackends;
        if (!chatAllowed.contains(server)) return;
        body = Wire.plain(body); if (body.isBlank() || body.length() > 512) return;
        long now = System.currentTimeMillis();
        Long previous = chatTimes.put(player.getUniqueId(), now); if (previous != null && now - previous < 1000) return;
        Component line = Component.text("[Global · " + server + "] ", NamedTextColor.GRAY)
                .append(Component.text(player.getUsername() + ": ", NamedTextColor.AQUA)).append(Component.text(body, NamedTextColor.WHITE));
        for (Player receiver : proxy.getAllPlayers()) if (receiver.getCurrentServer()
                .map(s -> chatAllowed.contains(s.getServerInfo().getName()) && !s.getServerInfo().getName().equals(server)).orElse(false)) receiver.sendMessage(line);
    }
    private Component row(QueueDirectory.Queue queue) {
        return Component.text(queue.title() + " (" + queue.players() + "/" + queue.maximum() + ") ", NamedTextColor.YELLOW)
                .append(Component.text("[Join]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/joinqueue " + queue.token())));
    }
    private void announce(QueueDirectory.Queue queue) {
        long now = System.currentTimeMillis(); String key = queue.server() + "/" + queue.title();
        announcements.values().removeIf(time -> time < now - 60_000);
        Long previous = announcements.putIfAbsent(key, now); if (previous != null) return;
        Component line = Component.text("Minigame queue: ", NamedTextColor.AQUA).append(row(queue))
                .append(Component.text("  Bedrock: /queues", NamedTextColor.GRAY));
        for (Player receiver : proxy.getAllPlayers()) if (allowed(receiver)) receiver.sendMessage(line);
    }
    private boolean allowed(Player player) {
        return player.getCurrentServer().map(s -> backends.contains(s.getServerInfo().getName())).orElse(false);
    }
    private void showQueues(Player player, boolean chatOnly) {
        if (!allowed(player)) { player.sendMessage(Component.text("Network queues are unavailable from this server.")); return; }
        var list = queues.list();
        if (list.isEmpty()) { player.sendMessage(Component.text("No minigame queues are waiting right now.")); return; }
        if (!chatOnly && proxy.getPluginManager().isLoaded("floodgate")) {
            try { if (BedrockMenu.show(player, list, token -> join(player, token))) return; }
            catch (RuntimeException | LinkageError failure) { logger.warn("Bedrock queue menu unavailable; using chat fallback."); }
        }
        player.sendMessage(Component.text("Join a waiting minigame:", NamedTextColor.AQUA));
        for (var q : list) player.sendMessage(row(q));
        player.sendMessage(Component.text("If a button does not work, type /joinqueue followed by its queue ID. Use /queues chat for IDs.", NamedTextColor.GRAY));
        // Copyable command is also usable by clients without clickable chat or forms.
        for (var q : list) player.sendMessage(Component.text(q.title() + ": /joinqueue " + q.token(), NamedTextColor.GRAY));
    }
    private void join(Player player, String token) {
        if (!allowed(player)) return;
        long now = System.currentTimeMillis();
        Long previous = joinTimes.put(player.getUniqueId(), now); if (previous != null && now - previous < 2000) return;
        var queue = queues.find(token).orElse(null);
        if (queue == null || !queueServers.contains(queue.server())) { player.sendMessage(Component.text("That queue has closed. Use /queues again.")); return; }
        var target = proxy.getServer(queue.server()).orElse(null);
        if (target == null) { player.sendMessage(Component.text("The minigame server is unavailable.")); return; }
        Pending request = new Pending(UUID.randomUUID().toString(), queue, now + 15_000);
        String current = player.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse("");
        if (!current.equals(queue.server())) visits.put(player.getUniqueId(), new Visit(current, queue.server(), request.request()));
        else visits.computeIfPresent(player.getUniqueId(), (id, old) -> new Visit(old.origin(), queue.server(), request.request()));
        pending.put(player.getUniqueId(), request);
        if (player.getCurrentServer().map(s -> s.getServerInfo().getName().equals(queue.server())).orElse(false)) { deliver(player, request); return; }
        player.sendMessage(Component.text("Connecting to " + queue.title() + "…"));
        player.createConnectionRequest(target).connect().whenComplete((result, failure) -> {
            if (failure != null || !result.isSuccessful()) {
                if (pending.remove(player.getUniqueId(), request)) {
                    visits.computeIfPresent(player.getUniqueId(), (id, visit) -> visit.request().equals(request.request()) ? null : visit);
                    player.sendMessage(Component.text("Could not connect. Your current server has been kept."));
                }
            }
        });
    }
    @Subscribe public void connected(ServerPostConnectEvent event) {
        Player player = event.getPlayer(); Pending request = pending.get(player.getUniqueId());
        Visit visit = visits.get(player.getUniqueId());
        if (visit != null && player.getCurrentServer().map(c -> !c.getServerInfo().getName().equals(visit.destination())).orElse(false)) {
            visits.remove(player.getUniqueId(), visit); returning.remove(player.getUniqueId()); pending.remove(player.getUniqueId()); return;
        }
        if (request != null) deliver(player, request);
    }
    private void returnHome(Player player, Visit visit) {
        var target = proxy.getServer(visit.origin()).orElse(null);
        if (target == null || !backends.contains(visit.origin()) || !returning.add(player.getUniqueId())) return;
        pending.remove(player.getUniqueId());
        player.createConnectionRequest(target).connect().whenComplete((result, failure) -> {
            returning.remove(player.getUniqueId());
            if (failure == null && result.isSuccessful()) {
                visits.remove(player.getUniqueId(), visit);
                player.getCurrentServer().filter(c -> c.getServerInfo().getName().equals(visit.origin()))
                        .ifPresent(c -> c.sendPluginMessage(CHANNEL, wire.encode("RESTORE", player.getUniqueId(), visit.request())));
            }
            else {
                visits.remove(player.getUniqueId(), visit);
                player.sendMessage(Component.text("Your previous server is unavailable. You are safe in the minigames lobby; use /server " + visit.origin() + " to return later."));
            }
        });
    }
    private void deliver(Player player, Pending request) {
        if (pending.get(player.getUniqueId()) != request) return;
        player.getCurrentServer().filter(s -> s.getServerInfo().getName().equals(request.queue().server()))
                .ifPresent(s -> s.sendPluginMessage(CHANNEL, wire.encode("JOIN", player.getUniqueId(), request.request(), request.queue().instance())));
    }
    private void retryJoins() {
        long now = System.currentTimeMillis();
        pending.forEach((id, request) -> {
            Player player = proxy.getPlayer(id).orElse(null);
            if (player == null || request.expires() < now) {
                if (pending.remove(id, request) && player != null) {
                    player.sendMessage(Component.text("Queue join timed out. Returning to your previous server where possible."));
                    Visit visit = visits.get(id);
                    if (visit != null && player.getCurrentServer().map(c -> c.getServerInfo().getName().equals(visit.destination())).orElse(false)) returnHome(player, visit);
                }
            } else deliver(player, request);
        });
    }
    @Subscribe public void quit(DisconnectEvent event) {
        UUID id = event.getPlayer().getUniqueId(); visits.remove(id); returning.remove(id); pending.remove(id); chatTimes.remove(id); joinTimes.remove(id);
    }
    @Subscribe public void stop(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
        if (ingress != null) ingress.close();
        pending.clear();
    }
}
