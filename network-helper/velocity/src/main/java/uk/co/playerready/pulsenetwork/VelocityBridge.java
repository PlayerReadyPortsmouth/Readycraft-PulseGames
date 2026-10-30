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

@Plugin(id="pulsenetwork", name="PulseNetwork", version="0.1.6", description="Public chat relay and minigame queue joins", authors={"AurorasChaos (on behalf of Player Ready)"},
        dependencies = {@com.velocitypowered.api.plugin.Dependency(id="floodgate", optional=true),
                @com.velocitypowered.api.plugin.Dependency(id="luckperms", optional=true)})
public final class VelocityBridge {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(Wire.CHANNEL);
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    private Wire wire;
    private LocalChatIngress ingress;
    private TabService tab;
    private IdentityResolver.Meta lpMeta = new LuckPermsMeta();
    private NameStyle.Tags tags = NameStyle.Tags.DEFAULT;
    private Set<String> backends = Set.of(), queueServers = Set.of();
    private Set<String> chatBackends;
    private Set<String> paperBackends = Set.of();
    private record Visit(String origin, String destination, String request) {}
    private final Map<UUID, Visit> visits = new ConcurrentHashMap<>();
    private record Resume(Visit visit, long expires) {}
    private final Map<UUID, Resume> resuming = new ConcurrentHashMap<>();
    private final Map<UUID, Long> reconnectUntil = new ConcurrentHashMap<>();
    private final Set<UUID> returning = ConcurrentHashMap.newKeySet();
    private final QueueDirectory queues = new QueueDirectory();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chatTimes = new ConcurrentHashMap<>(), joinTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> announcements = new ConcurrentHashMap<>();
    private record Pending(String request, QueueDirectory.Queue queue, long expires, UUID leader, List<UUID> members) {}
    private final NetworkParties parties = new NetworkParties();
    private long lastPartySync;
    private final Map<UUID, Long> partyTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> cancelledGroups = new ConcurrentHashMap<>();
    @Inject public VelocityBridge(ProxyServer proxy, Logger logger, @DataDirectory Path directory) {
        this.proxy = proxy; this.logger = logger; this.directory = directory;
    }
    @Subscribe public void start(ProxyInitializeEvent event) {
        TabConfig tabConfig = null;
        String hiddenPlayers = "", roleGroups = "";
        try {
            Files.createDirectories(directory);
            Path path = directory.resolve("network.properties");
            if (!Files.exists(path)) Files.writeString(path, "enabled=false\nbridge-secret=\nbackends=lobby,survival,creative,skyblock,minigames,minigamestest,survivaltest,sessions,aoc\nqueue-servers=minigamestest\nlocal-ingress-port=0\nlocal-ingress-backends=aoc\ntab.enabled=false\n");
            Properties config = new Properties(); try (var reader = Files.newBufferedReader(path)) { config.load(reader); }
            if (!Boolean.parseBoolean(config.getProperty("enabled"))) { logger.info("PulseNetwork is disabled until configured."); return; }
            wire = new Wire(config.getProperty("bridge-secret"));
            backends = csv(config.getProperty("backends", "")); queueServers = csv(config.getProperty("queue-servers", ""));
            paperBackends = csv(config.getProperty("paper-backends", ""));
            chatBackends = csv(config.getProperty("chat-backends", config.getProperty("backends", "")));
            if (!backends.containsAll(chatBackends)) throw new IllegalArgumentException("Chat backends must be allowed backends");
            hiddenPlayers = config.getProperty("tab.hidden-players", ""); roleGroups = config.getProperty("roles.groups", "");
            try { tags = TabConfig.tags(config); }
            catch (IllegalArgumentException e) { logger.error("Role tag configuration is invalid; built-in tags are used: {}", e.getMessage()); }
            try { tabConfig = TabConfig.parse(config); }
            catch (IllegalArgumentException e) { logger.error("Network tab configuration is invalid; tab stays off: {}", e.getMessage()); }
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
        if (tabConfig != null && tabConfig.enabled()) {
            tab = new TabService(proxy, logger, tabConfig, backends, proxy.getPluginManager().isLoaded("floodgate"), java.time.Clock.systemUTC());
            tab.hiddenNames(RoleLogic.names(hiddenPlayers));
            proxy.getEventManager().register(this, tab);
            proxy.getScheduler().buildTask(this, tab::tick).repeat(Duration.ofMillis(tabConfig.minIntervalMs())).schedule();
        }
        var commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("queues").plugin(this).build(), (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player p) showQueues(p, invocation.arguments().length > 0 && invocation.arguments()[0].equalsIgnoreCase("chat"));
        });
        commands.register(commands.metaBuilder("joinqueue").plugin(this).build(), (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player p) {
                if (invocation.arguments().length == 1) join(p, invocation.arguments()[0]); else showQueues(p, false);
            }
        });
        commands.register(commands.metaBuilder("networkparty").aliases("party").plugin(this).build(), (SimpleCommand) invocation -> {
            if (invocation.source() instanceof Player player) partyCommand(player, invocation.arguments());
        });
        registerRoles(commands, roleGroups, hiddenPlayers);
        proxy.getScheduler().buildTask(this, this::retryJoins).repeat(Duration.ofSeconds(1)).schedule();
    }
    private void registerRoles(com.velocitypowered.api.command.CommandManager commands, String roleGroups, String hiddenPlayers) {
        if (!proxy.getPluginManager().isLoaded("luckperms")) { logger.info("LuckPerms is not loaded; /role is unavailable."); return; }
        try {
            var command = new RoleCommand(proxy, logger, new LuckPermsStore(), RoleLogic.parseGroups(roleGroups), RoleLogic.names(hiddenPlayers));
            commands.register(commands.metaBuilder("role").aliases("networkrole").plugin(this).build(), command);
        } catch (IllegalArgumentException | LinkageError e) { logger.error("/role is unavailable: {}", e.getMessage()); }
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
                    case "STATUS" -> { if (tab != null) tab.status(server, m.fields()); }
                    case "PARTYCMD" -> partyCommand(carrier, m.fields().toArray(String[]::new));
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
                    case "RESUMEACK" -> {
                        Resume resume = resuming.get(carrier.getUniqueId());
                        if (resume != null && resume.visit().destination().equals(server)
                                && m.fields().equals(List.of(resume.visit().request()))) resuming.remove(carrier.getUniqueId(), resume);
                    }
                    case "ACK" -> {
                        Pending request = pending.get(carrier.getUniqueId());
                        if (request != null && request.queue().server().equals(server) && m.fields().equals(List.of(request.request()))) {
                            if (request.members().size() == 1) pending.remove(carrier.getUniqueId(), request);
                            else if (request.leader().equals(carrier.getUniqueId()))
                                request.members().forEach(id -> pending.remove(id, request));
                        }
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
        Component[] lines = {relayLine(player, server, body, false), null};
        for (Player receiver : proxy.getAllPlayers()) if (receiver.getCurrentServer()
                .map(s -> chatAllowed.contains(s.getServerInfo().getName()) && !s.getServerInfo().getName().equals(server)).orElse(false)) {
            var connection = receiver.getCurrentServer().orElseThrow();
            if (paperBackends.contains(connection.getServerInfo().getName()))
                connection.sendPluginMessage(CHANNEL, wire.encode("REMOTECHAT", receiver.getUniqueId(), server, player.getUniqueId().toString(), player.getUsername(), body));
            else {
                boolean bedrock = receiver.getUniqueId().getMostSignificantBits() == 0; // Floodgate UUIDs
                if (bedrock && lines[1] == null) lines[1] = relayLine(player, server, body, true);
                receiver.sendMessage(lines[bedrock ? 1 : 0]);
            }
        }
    }
    /** {@code [Global · server] [TAG] name: body}, static frame; the level badge needs the tab status feed. */
    Component relayLine(Player player, String server, String body, boolean bedrockViewer) {
        var identity = IdentityResolver.resolve(player.getUniqueId(), player.getUsername(), lpMeta,
                tab == null ? null : tab.level(server, player.getUniqueId()), tags);
        return Component.text("[Global · " + server + "] ", NamedTextColor.GRAY)
                .append(Component.text().color(NamedTextColor.AQUA).append(NameStyle.render(identity, bedrockViewer, 0, tags, NameStyle.MAX_VISIBLE))
                        .append(Component.text(": ")).build()).append(Component.text(body, NamedTextColor.WHITE));
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
    private void partyCommand(Player player, String[] args) {
        if (!allowed(player)) return;
        long now = System.currentTimeMillis();
        Long previous = partyTimes.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 1000) return;
        try {
            List<UUID> before = parties.members(player.getUniqueId());
            if (before.stream().anyMatch(pending::containsKey)) throw new IllegalArgumentException("Wait for your party queue transfer to finish first.");
            String command = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
            switch (command) {
                case "invite" -> {
                    if (args.length != 2) throw new IllegalArgumentException("Use /party invite <player>.");
                    Player target = proxy.getPlayer(args[1]).orElseThrow(() -> new IllegalArgumentException("Player is not online."));
                    if (!allowed(target) || pending.containsKey(target.getUniqueId())) throw new IllegalArgumentException("That player is unavailable.");
                    parties.invite(player.getUniqueId(), target.getUniqueId());
                    target.sendMessage(Component.text(player.getUsername() + " invited you to a network party. Type /party accept or /party deny.")
                            .clickEvent(ClickEvent.runCommand("/party accept")));
                    player.sendMessage(Component.text("Party invitation sent."));
                }
                case "accept" -> {
                    UUID inviter = parties.inviter(player.getUniqueId());
                    if (inviter != null && parties.members(inviter).stream().anyMatch(pending::containsKey))
                        throw new IllegalArgumentException("Wait until that party's queue transfer finishes.");
                    parties.accept(player.getUniqueId()); player.sendMessage(Component.text("Joined the network party. Your leader can choose /queues.")); }
                case "deny" -> { parties.deny(player.getUniqueId()); player.sendMessage(Component.text("Invitation declined.")); }
                case "leave" -> { parties.leave(player.getUniqueId()); player.sendMessage(Component.text("Left the network party.")); }
                case "disband" -> { parties.disband(player.getUniqueId()); player.sendMessage(Component.text("Network party disbanded.")); }
                case "chat" -> {
                    if (args.length < 2) throw new IllegalArgumentException("Use /party chat <message>.");
                    String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    if (text.length() > 512) throw new IllegalArgumentException("Party messages are limited to 512 characters.");
                    for (UUID id : parties.members(player.getUniqueId())) proxy.getPlayer(id).ifPresent(member ->
                            member.sendMessage(Component.text("[Party] " + player.getUsername() + ": " + text)));
                }
                case "list" -> player.sendMessage(Component.text("Party: " + String.join(", ", parties.members(player.getUniqueId()).stream()
                        .map(id -> proxy.getPlayer(id).map(Player::getUsername).orElse("Offline member")).toList())));
                default -> player.sendMessage(Component.text("/party invite <player>, accept, deny, list, leave, disband, chat. Your leader uses /queues to join together across servers."));
            }
        } catch (IllegalArgumentException rejected) { player.sendMessage(Component.text(rejected.getMessage())); }
        syncParties();
    }
    private void syncParties() {
        for (Player member : proxy.getAllPlayers()) {
            UUID leader = parties.leader(member.getUniqueId());
            String roster = leader == null ? "" : String.join(",", parties.members(member.getUniqueId()).stream().map(UUID::toString).toList());
            member.getCurrentServer().filter(c -> backends.contains(c.getServerInfo().getName())).ifPresent(c ->
                    c.sendPluginMessage(CHANNEL, wire.encode("PARTYSTATE", member.getUniqueId(), leader == null ? "" : leader.toString(), roster)));
        }
    }
    private void join(Player player, String token) {
        if (!allowed(player)) return;
        long now = System.currentTimeMillis();
        Long previous = joinTimes.put(player.getUniqueId(), now); if (previous != null && now - previous < 2000) return;
        var queue = queues.find(token).orElse(null);
        if (queue == null || !queueServers.contains(queue.server())) { player.sendMessage(Component.text("That queue has closed. Use /queues again.")); return; }
        var target = proxy.getServer(queue.server()).orElse(null);
        if (target == null) { player.sendMessage(Component.text("The minigame server is unavailable.")); return; }
        UUID leader = parties.leader(player.getUniqueId());
        if (leader != null && !leader.equals(player.getUniqueId())) { player.sendMessage(Component.text("Only your party leader can queue the group.")); return; }
        List<UUID> members = parties.members(player.getUniqueId());
        List<Player> group = members.stream().map(id -> proxy.getPlayer(id).orElse(null)).toList();
        if (group.stream().anyMatch(member -> member == null || !allowed(member) || pending.containsKey(member.getUniqueId()))
                || members.size() > queue.maximum()) {
            player.sendMessage(Component.text("Your whole party must be online, available, and fit this queue.")); return;
        }
        Pending request = new Pending(UUID.randomUUID().toString(), queue, now + 15_000, player.getUniqueId(), members);
        // Publish the complete transfer before any asynchronous connection can finish.
        for (Player member : group) pending.put(member.getUniqueId(), request);
        for (Player member : group) transfer(member, request);
    }
    private void transfer(Player player, Pending request) {
        var queue = request.queue();
        var target = proxy.getServer(queue.server()).orElse(null);
        if (target == null || pending.get(player.getUniqueId()) != request) { cancelGroup(request); return; }
        String current = player.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse("");
        if (!current.equals(queue.server())) visits.compute(player.getUniqueId(), (id, old) ->
                new Visit(old != null && old.destination().equals(current) ? old.origin() : current, queue.server(), request.request()));
        else visits.computeIfPresent(player.getUniqueId(), (id, old) -> new Visit(old.origin(), queue.server(), request.request()));
        pending.put(player.getUniqueId(), request);
        if (player.getCurrentServer().map(s -> s.getServerInfo().getName().equals(queue.server())).orElse(false)) { deliver(player, request); return; }
        player.sendMessage(Component.text("Connecting to " + queue.title() + "…"));
        player.createConnectionRequest(target).connect().whenComplete((result, failure) -> {
            if (failure != null || result == null || !result.isSuccessful()) {
                if (!cancelledGroups.containsKey(request.request()) && request.members().stream().noneMatch(id -> pending.get(id) == request)) return;
                cancelGroup(request);
                visits.computeIfPresent(player.getUniqueId(), (id, visit) -> visit.request().equals(request.request()) ? null : visit);
            } else if (cancelledGroups.containsKey(request.request())) {
                Visit visit = visits.get(player.getUniqueId());
                if (visit != null && visit.request().equals(request.request())) returnHome(player, visit);
            }
        });
    }
    @Subscribe public void connected(ServerPostConnectEvent event) {
        Player player = event.getPlayer(); Pending request = pending.get(player.getUniqueId());
        Visit visit = visits.get(player.getUniqueId());
        if (visit != null && cancelledGroups.containsKey(visit.request()) && player.getCurrentServer()
                .filter(c -> c.getServerInfo().getName().equals(visit.destination())).isPresent()) { returnHome(player, visit); return; }
        Long reconnect = reconnectUntil.remove(player.getUniqueId());
        if (reconnect != null && visit != null) {
            if (reconnect < System.currentTimeMillis()) { visits.remove(player.getUniqueId(), visit); return; }
            String current = player.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse("");
            if (current.equals(visit.destination())) {
                Resume resume = new Resume(visit, System.currentTimeMillis() + 15_000);
                resuming.put(player.getUniqueId(), resume);
                resumeVisit(player, resume);
                return;
            }
            if (current.equals(visit.origin())) player.getCurrentServer().ifPresent(c -> c.sendPluginMessage(CHANNEL,wire.encode("RESTORE",player.getUniqueId(),visit.request())));
        }
        if (visit != null && player.getCurrentServer().map(c -> !c.getServerInfo().getName().equals(visit.destination())).orElse(false)) {
            visits.remove(player.getUniqueId(), visit); returning.remove(player.getUniqueId()); pending.remove(player.getUniqueId()); return;
        }
        if (request != null) deliver(player, request);
    }
    private void returnHome(Player player, Visit visit) {
        var target = proxy.getServer(visit.origin()).orElse(null);
        if (target == null || !backends.contains(visit.origin()) || !returning.add(player.getUniqueId())) return;
        pending.remove(player.getUniqueId());
        resuming.remove(player.getUniqueId());
        player.createConnectionRequest(target).connect().whenComplete((result, failure) -> {
            // A disconnect may complete this request after a new login has begun.
            // Keep the saved origin for that login and never act on a newer queue visit.
            if (reconnectUntil.containsKey(player.getUniqueId())) return;
            Visit currentVisit = visits.get(player.getUniqueId());
            if (currentVisit != null && !currentVisit.equals(visit)) return;
            returning.remove(player.getUniqueId());
            if (failure == null && result != null && result.isSuccessful()) {
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
        if (request.members().size() > 1) {
            Player leader = proxy.getPlayer(request.leader()).orElse(null);
            boolean allArrived = request.members().stream().allMatch(id -> pending.get(id) == request && proxy.getPlayer(id)
                    .flatMap(Player::getCurrentServer).filter(c -> c.getServerInfo().getName().equals(request.queue().server())).isPresent());
            if (leader != null && allArrived) leader.getCurrentServer().ifPresent(connection -> connection.sendPluginMessage(CHANNEL,
                    wire.encode("GROUPJOIN", leader.getUniqueId(), request.request(), request.queue().instance(),
                            String.join(",", request.members().stream().map(UUID::toString).toList()))));
            return;
        }
        player.getCurrentServer().filter(s -> s.getServerInfo().getName().equals(request.queue().server()))
                .ifPresent(s -> s.sendPluginMessage(CHANNEL, wire.encode("JOIN", player.getUniqueId(), request.request(), request.queue().instance())));
    }
    private void resumeVisit(Player player, Resume resume) {
        player.getCurrentServer().filter(c -> c.getServerInfo().getName().equals(resume.visit().destination()))
                .ifPresent(c -> c.sendPluginMessage(CHANNEL, wire.encode("RESUMEVISIT", player.getUniqueId(), resume.visit().request())));
    }
    private void retryJoins() {
        long now = System.currentTimeMillis();
        if (now - lastPartySync >= 5000) { lastPartySync = now; syncParties(); }
        cancelledGroups.values().removeIf(expiry -> expiry < now);
        reconnectUntil.forEach((id,expiry)-> { if(expiry<now && reconnectUntil.remove(id,expiry)) visits.remove(id); });
        resuming.forEach((id, resume) -> {
            Player player = proxy.getPlayer(id).orElse(null);
            if (player == null) resuming.remove(id, resume);
            else if (resume.expires() < now) {
                if (resuming.remove(id, resume)) returnHome(player, resume.visit());
            } else resumeVisit(player, resume);
        });
        pending.forEach((id, request) -> {
            Player player = proxy.getPlayer(id).orElse(null);
            if (player == null || request.expires() < now) {
                cancelGroup(request);
            } else deliver(player, request);
        });
    }
    private void cancelGroup(Pending request) {
        if (!cancelledGroups.containsKey(request.request()) && request.members().stream().noneMatch(id -> pending.get(id) == request)) return;
        cancelledGroups.put(request.request(), System.currentTimeMillis() + 60_000);
        for (UUID id : request.members()) {
            if (!pending.remove(id, request)) continue;
            Player member = proxy.getPlayer(id).orElse(null);
            Visit visit = visits.get(id);
            if (member != null) {
                member.sendMessage(Component.text("Party queue transfer could not complete. Returning each player to their own previous server where possible."));
                if (visit != null && visit.request().equals(request.request())) {
                    if (member.getCurrentServer().filter(c -> c.getServerInfo().getName().equals(visit.destination())).isPresent()) returnHome(member, visit);
                    // A connection already in flight may still succeed; keep its return origin.

                }
            }
        }
    }
    @Subscribe public void quit(DisconnectEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pending group = pending.get(id); if (group != null) cancelGroup(group);
        parties.leave(id); partyTimes.remove(id); resuming.remove(id);
        syncParties();
        if (visits.containsKey(id)) reconnectUntil.put(id,System.currentTimeMillis()+300_000); returning.remove(id); pending.remove(id); chatTimes.remove(id); joinTimes.remove(id);
    }
    @Subscribe public void stop(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
        if (ingress != null) ingress.close();
        pending.clear();visits.clear();reconnectUntil.clear();resuming.clear();cancelledGroups.clear();
    }
}
