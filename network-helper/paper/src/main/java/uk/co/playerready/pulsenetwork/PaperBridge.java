package uk.co.playerready.pulsenetwork;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.util.*;

/** Observe final local chat; never cancel, rewrite, or replay chat events. */
public final class PaperBridge extends JavaPlugin implements Listener, PluginMessageListener {
    private Wire wire;
    private ReturnLocations returnLocations;
    private final Map<UUID, String> restores = new HashMap<>();
    private LocalChatGames chatGames;
    private ChatCompatibility compatibility;
    private final Map<UUID, Boolean> global = new HashMap<>();
    private final Map<UUID, Long> lastChat = new HashMap<>();
    private final Map<String, Long> handled = new HashMap<>();
    private record Visit(String request, long joinedAt) {}
    private final Map<UUID, Visit> visits = new HashMap<>();
    private Set<String> advertised = Set.of();
    private StatusReporter status;
    private final StatusReporter levelLookup = new StatusReporter(List.of());
    private final LuckPermsMeta lpMeta = new LuckPermsMeta();
    private NameStyle.Tags tags = NameStyle.Tags.DEFAULT;
    @Override public void onEnable() {
        saveDefaultConfig();
        MenuCommands.register(this, (player, fields) -> { if (wire == null) return false; send(player, "ROLECMD", fields.toArray(String[]::new)); return true; });
        try { returnLocations = ReturnLocations.load(new java.io.File(getDataFolder(), "return-locations.yml").toPath()); }
        catch (java.io.IOException ex) { getLogger().severe("Return locations could not be read; preserving file and disabling location writes."); }
        if (!getConfig().getBoolean("enabled")) { getLogger().info("PulseNetwork is disabled until configured."); return; }
        try { wire = new Wire(getConfig().getString("bridge-secret", "")); }
        catch (IllegalArgumentException e) { getLogger().severe(e.getMessage()); return; }
        getServer().getMessenger().registerOutgoingPluginChannel(this, Wire.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Wire.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::queues, 20, 100);
        getServer().getScheduler().runTaskTimer(this, this::returns, 20, 20);
        if (getConfig().getBoolean("tab-status.enabled")) {
            status = new StatusReporter(getConfig().getStringList("tab-status.afk-metadata-keys"));
            getServer().getScheduler().runTaskTimer(this, this::statusTick, 40, 100);
        }
        tags = loadTags();
        compatibility = new ChatCompatibility(this); compatibility.start();
        chatGames = new LocalChatGames(this);
        getServer().getScheduler().runTaskTimer(this, chatGames::tick, 20, 20);
    }
    private NameStyle.Tags loadTags() {
        var section = getConfig().getConfigurationSection("tags");
        var overrides = new HashMap<String, String>();
        if (section != null) for (String role : section.getKeys(false)) overrides.put(role, section.getString(role, ""));
        try { return new NameStyle.Tags(overrides); }
        catch (IllegalArgumentException e) { getLogger().warning("Invalid tags configuration; built-in role tags are used."); return NameStyle.Tags.DEFAULT; }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (wire == null || !getConfig().getBoolean("chat.enabled")) { player.sendMessage(Component.text("Global chat is not enabled on this server yet.")); return true; }
        if (args.length != 0) { player.sendMessage(Component.text("Use /g to toggle global chat, then type normally. Use /localchat to switch back.")); return true; }
        boolean enabled = !command.getName().equals("localchat") && !global.getOrDefault(player.getUniqueId(), getConfig().getBoolean("chat.global-by-default"));
        global.put(player.getUniqueId(), enabled);
        player.sendMessage(Component.text(enabled ? "Global chat on. Your public messages also reach other servers." : "Local chat on. Your messages stay on this server."));
        return true;
    }
    /** Tag, level and name style for local system delivery (static frame), per viewer kind. Falls back to the plain display name on failure. */
    private java.util.function.Function<net.kyori.adventure.audience.Audience, Component> styledNames(Player player) {
        Component[] cache = new Component[2];
        return viewer -> {
            int slot = LocalChatDelivery.bedrock(viewer) ? 1 : 0;
            if (cache[slot] == null) cache[slot] = styledName(player, slot == 1);
            return cache[slot];
        };
    }
    private Component styledName(Player player, boolean bedrockViewer) {
        try {
            var games = getServer().getPluginManager().getPlugin("PulseGames");
            Integer level = levelLookup.level(games, player);
            var identity = IdentityResolver.resolve(player.getUniqueId(), player.getName(), lpMeta, level, tags);
            return NameStyle.render(identity, bedrockViewer, 0, tags, NameStyle.MAX_VISIBLE);
        } catch (RuntimeException e) { return player.displayName(); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        if (compatibility != null && compatibility.hasAdvancedChat()) return;
        // The viewer set is the privacy proof for the relay, so decide now, before local delivery empties it.
        boolean publicAudience = LocalChatDelivery.isPublic(event.getPlayer(), event.viewers(), Bukkit.getOnlinePlayers());
        // Local system delivery: this plugin is the final consumer at MONITOR, so it may consume the viewer set. Each
        // original viewer (console included, so the log line appears exactly once) gets the rendered line as an unsigned
        // system message and the signed packet is not also sent. Mutes, filters and channels have already trimmed it.
        if (getConfig().getBoolean("chat.local-system-delivery")) {
            var viewers = event.viewers();
            LocalChatDelivery.deliver(viewers, event.renderer(), event.getPlayer(), styledNames(event.getPlayer()), event.message(),
                    getConfig().getString("chat.local-format", LocalChatDelivery.DEFAULT_FORMAT));
            viewers.clear();
        }
        // Reading after dispatch also respects cancellation/restricted viewers by later handlers.
        getServer().getScheduler().runTask(this, () -> {
            Player player = event.getPlayer(); UUID id = player.getUniqueId();
            if (event.isCancelled() || !player.isOnline() || player.getMetadata("vanished").stream().anyMatch(org.bukkit.metadata.MetadataValue::asBoolean)) return;
            // A private/local-radius/staff channel must never accidentally become public.
            if (!publicAudience) return;
            if (event.renderer() != null && event.renderer().getClass().getName().contains("CityChatListener")) return;
            String body = Wire.plain(PlainTextComponentSerializer.plainText().serialize(event.message()));
            if (body.isBlank() || body.length() > 512) return;
            if (chatGames != null && chatGames.answer(player, body)) return;
            relayPublic(player, body);
        });
    }
    void relayPublic(Player player, String message) {
        UUID id = player.getUniqueId(); String body = Wire.plain(message);
        if (!player.isOnline() || body.isBlank() || body.length() > 512 || player.getMetadata("vanished").stream().anyMatch(org.bukkit.metadata.MetadataValue::asBoolean)) return;
        if (!getConfig().getBoolean("chat.enabled") || !player.hasPermission("pulsenetwork.chat")
                || !global.getOrDefault(id, getConfig().getBoolean("chat.global-by-default"))
                || (compatibility != null && !compatibility.maySend(player))) return;
        long now = System.currentTimeMillis();
        if (now - lastChat.getOrDefault(id, 0L) < 1000) return;
        lastChat.put(id, now); send(player, "CHAT", body);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        if (returnLocations != null && !event.getPlayer().hasMetadata("NPC")) {
            returnLocations.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation());
            try { returnLocations.save(new java.io.File(getDataFolder(), "return-locations.yml").toPath()); }
            catch (java.io.IOException ex) { getLogger().warning("Could not save queue return location."); }
        } restores.remove(event.getPlayer().getUniqueId()); visits.remove(event.getPlayer().getUniqueId()); global.remove(event.getPlayer().getUniqueId()); lastChat.remove(event.getPlayer().getUniqueId()); }
    private void send(Player carrier, String kind, String... fields) { carrier.sendPluginMessage(this, Wire.CHANNEL, wire.encode(kind, carrier.getUniqueId(), fields)); }

    private void restore(Player player, String request) {
        if (!player.isOnline() || !request.equals(restores.get(player.getUniqueId()))) return;
        ReturnLocations.Position saved=returnLocations==null?null:returnLocations.get(player.getUniqueId());
        if (saved==null) return;
        org.bukkit.World world=Bukkit.getWorld(saved.world());
        if (world==null) {
            // Only PulseGames may reopen its known persistent build worlds. Never create a missing world.
            var games=getServer().getPluginManager().getPlugin("PulseGames");
            if (games!=null && games.isEnabled()) try {
                world=(org.bukkit.World)games.getClass().getMethod("loadReturnWorld",String.class).invoke(games,saved.world());
            } catch (ReflectiveOperationException ex) { getLogger().warning("Saved return world could not be reopened."); }
        }
        org.bukkit.Location target=saved.in(world);
        if (target==null) {player.sendMessage(Component.text("Your previous location is unavailable. You remain at this server's arrival point."));return;}
        // Chunk loading is asynchronous. Do not teleport a disconnected player or one who has since moved worlds.
        org.bukkit.World arrival=player.getWorld();
        target.getWorld().getChunkAtAsync(target).whenComplete((chunk,failure)->getServer().getScheduler().runTask(this,()->{
            if (!player.isOnline() || !request.equals(restores.get(player.getUniqueId())) || player.getWorld()!=arrival) return;
            if (failure!=null || !safe(target)) {player.sendMessage(Component.text("Your previous spot is obstructed or unsafe. You remain at this server's arrival point."));return;}
            if (player.isInsideVehicle()) player.leaveVehicle();
            if (!player.teleport(target)) player.sendMessage(Component.text("Return teleport was refused; you remain at this server's arrival point."));
        }));
    }
    static boolean safe(org.bukkit.Location at) {
        org.bukkit.World w=at.getWorld();if(w==null)return false;
        var feet=at.getBlock();var head=feet.getRelative(org.bukkit.block.BlockFace.UP);var floor=feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid()
                && floor.getType().isSolid() && !Set.of(org.bukkit.Material.MAGMA_BLOCK,org.bukkit.Material.CAMPFIRE,org.bukkit.Material.SOUL_CAMPFIRE,org.bukkit.Material.CACTUS).contains(floor.getType());
    }

    @SuppressWarnings("unchecked") private void queues() {
        if (!getConfig().getBoolean("queues.enabled")) return;
        var games = getServer().getPluginManager().getPlugin("PulseGames");
        Player carrier = Bukkit.getOnlinePlayers().stream().filter(p -> !p.hasMetadata("NPC")).findFirst().orElse(null);
        if (games == null || !games.isEnabled() || carrier == null) return;
        try {
            var rows = (List<Map<String, String>>) games.getClass().getMethod("networkQueues").invoke(games);
            var current = new HashSet<String>();
            for (var row : rows) {
                String id = row.get("id"); current.add(id);
                send(carrier, "QUEUE", id, row.get("title"), row.get("players"), row.get("maximum"));
            }
            for (String gone : advertised) if (!current.contains(gone)) send(carrier, "WITHDRAW", gone);
            advertised = Set.copyOf(current);
        } catch (ReflectiveOperationException | ClassCastException e) {
            // Older PulseGames servers remain usable; no repeated noisy failure every five seconds.
            getConfig().set("queues.enabled", false);
            getLogger().warning("Queue sharing disabled: this PulseGames build does not provide the network queue API.");
        }
    }
    /** Signed, bounded server status for the proxy tab list; sent through any online player's connection. */
    private void statusTick() {
        Player carrier = Bukkit.getOnlinePlayers().stream().filter(p -> !p.hasMetadata("NPC")).findFirst().orElse(null);
        if (status == null || wire == null || carrier == null) return;
        var report = status.collect(Bukkit.getTPS()[0], Bukkit.getAverageTickTime(), Bukkit.getOnlinePlayers(),
                getServer().getPluginManager().getPlugin("PulseGames"));
        send(carrier, "STATUS", report.toFields());
    }
    private void returns() {
        var games = getServer().getPluginManager().getPlugin("PulseGames");
        if (games == null || !games.isEnabled()) return;
        for (var entry : visits.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || System.currentTimeMillis() - entry.getValue().joinedAt() < 2000) continue;
            try {
                if (Boolean.FALSE.equals(games.getClass().getMethod("networkPlayerBusy", Player.class).invoke(games, player)))
                    send(player, "RETURN", entry.getValue().request());
            } catch (ReflectiveOperationException e) { return; }
        }
    }
    /** Optional backend integration; proxy owns party commands across every server. */
    public boolean networkPartyCommand(Player player, String[] arguments) {
        if (wire == null) return false;
        if (arguments.length > 0 && arguments[0].equalsIgnoreCase("chat"))
            arguments = new String[]{"chat", String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length))};
        if (arguments.length > 12 || Arrays.stream(arguments).anyMatch(value -> value.length() > 512)) {
            player.sendMessage(Component.text("That party command is too long.")); return true;
        }
        send(player, "PARTYCMD", arguments);
        return true;
    }
    @Override public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!channel.equals(Wire.CHANNEL) || wire == null) return;
        var decoded = wire.decode(bytes);
        if (decoded.isPresent() && decoded.get().kind().equals("REMOTECHAT") && decoded.get().player().equals(player.getUniqueId()) && RemoteChat.validCount(decoded.get().fields().size())) {
            var fields = decoded.get().fields();
            try {
                UUID sender = UUID.fromString(fields.get(1));
                if (getConfig().getBoolean("chat.enabled") && (compatibility == null || compatibility.mayReceive(player, sender, fields.get(2))))
                    player.sendMessage(RemoteChat.line(Wire.plain(fields.get(0)), RemoteChat.identity(fields, tags), Wire.plain(fields.get(3)),
                            LocalChatDelivery.bedrock(player), tags));
            } catch (IllegalArgumentException invalid) { /* reject */ }
            return;
        }
        if (decoded.isPresent() && decoded.get().kind().equals("RESTORE") && decoded.get().player().equals(player.getUniqueId()) && decoded.get().fields().size() == 1) {
            String request=decoded.get().fields().get(0);
            try { UUID.fromString(request); } catch (IllegalArgumentException invalid) {return;}
            if (request.equals(restores.put(player.getUniqueId(),request))) return;
            getServer().getScheduler().runTaskLater(this, () -> restore(player,request), 20L);
            return;
        }
        if (decoded.isPresent() && decoded.get().kind().equals("RESUMEVISIT")) {
            var message = decoded.get();
            if (!message.player().equals(player.getUniqueId()) || message.fields().size() != 1
                    || !getConfig().getBoolean("queues.enabled")) return;
            String request = message.fields().getFirst();
            try { UUID.fromString(request); } catch (IllegalArgumentException invalid) { return; }
            var games = getServer().getPluginManager().getPlugin("PulseGames");
            if (games == null || !games.isEnabled()) return;
            visits.compute(player.getUniqueId(), (id, previous) -> previous != null && previous.request().equals(request)
                    ? previous : new Visit(request, System.currentTimeMillis()));
            send(player, "RESUMEACK", request);
            return;
        }
        if (decoded.isPresent() && decoded.get().kind().equals("PARTYSTATE")) {
            var message = decoded.get();
            if (!message.player().equals(player.getUniqueId()) || message.fields().size() != 2) return;
            try {
                UUID leader = message.fields().get(0).isEmpty() ? null : UUID.fromString(message.fields().get(0));
                List<UUID> members = message.fields().get(1).isEmpty() ? List.of()
                        : Arrays.stream(message.fields().get(1).split(",")).map(UUID::fromString).toList();
                if (members.size() > 8 || new HashSet<>(members).size() != members.size()
                        || leader == null && !members.isEmpty()
                        || leader != null && (!members.contains(player.getUniqueId()) || !members.getFirst().equals(leader))) return;
                var games = getServer().getPluginManager().getPlugin("PulseGames");
                if (games != null && games.isEnabled()) games.getClass()
                        .getMethod("networkPartyState", Player.class, UUID.class, List.class).invoke(games, player, leader, members);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) { /* Older backends keep existing local behaviour. */ }
            return;
        }
        if (!getConfig().getBoolean("queues.enabled")) return;
        if (decoded.isPresent() && decoded.get().kind().equals("GROUPJOIN")) {
            var message = decoded.get();
            if (!message.player().equals(player.getUniqueId()) || message.fields().size() != 3) return;
            String request = message.fields().get(0), instance = message.fields().get(1);
            if (!Wire.id(instance)) return;
            List<UUID> members;
            try {
                UUID.fromString(request);
                members = Arrays.stream(message.fields().get(2).split(",")).map(UUID::fromString).toList();
                if (members.size() < 2 || members.size() > 8 || new HashSet<>(members).size() != members.size()
                        || !members.getFirst().equals(player.getUniqueId())) return;
            } catch (IllegalArgumentException invalid) { return; }
            List<Player> group = members.stream().map(Bukkit::getPlayer).toList();
            if (group.stream().anyMatch(member -> member == null || !member.isOnline())) return;
            long now = System.currentTimeMillis();
            handled.values().removeIf(expiry -> expiry < now);
            String key = "party/" + request;
            if (!handled.containsKey(key)) {
                if (handled.size() >= 1000) return;
                var games = getServer().getPluginManager().getPlugin("PulseGames");
                if (games == null || !games.isEnabled()) return;
                try {
                    boolean accepted = Boolean.TRUE.equals(games.getClass()
                            .getMethod("joinNetworkParty", Player.class, String.class, List.class).invoke(games, player, instance, members));
                    if (!accepted) return;
                    for (Player member : group) visits.put(member.getUniqueId(), new Visit(request, now));
                    handled.put(key, now + 30_000);
                } catch (ReflectiveOperationException unavailable) {
                    player.sendMessage(Component.text("This server needs network party support. No party members have been queued.")); return;
                }
            }
            for (Player member : group) send(member, "ACK", request);
            return;
        }
        decoded.filter(m -> m.kind().equals("JOIN") && m.player().equals(player.getUniqueId()) && m.fields().size() == 2).ifPresent(m -> {
            String request = m.fields().get(0), instance = m.fields().get(1);
            if (!Wire.id(instance)) return;
            try { UUID.fromString(request); } catch (IllegalArgumentException e) { return; }
            long now = System.currentTimeMillis(); handled.values().removeIf(expiry -> expiry < now);
            String key = player.getUniqueId() + "/" + request;
            if (!handled.containsKey(key)) {
                if (handled.size() >= 1000) return;
                var games = getServer().getPluginManager().getPlugin("PulseGames");
                if (games == null || !games.isEnabled()) { player.sendMessage(Component.text("Minigame queues are unavailable.")); return; }
                try {
                    games.getClass().getMethod("joinNetworkQueue", Player.class, String.class).invoke(games, player, instance);
                    visits.put(player.getUniqueId(), new Visit(request, now));
                    handled.put(key, now + 30_000);
                } catch (ReflectiveOperationException e) { player.sendMessage(Component.text("This server needs the queue support update.")); return; }
            }
            send(player, "ACK", request);
        });
    }
}
