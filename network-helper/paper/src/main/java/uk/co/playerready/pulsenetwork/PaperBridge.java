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
    private LocalChatGames chatGames;
    private final Map<UUID, Boolean> global = new HashMap<>();
    private final Map<UUID, Long> lastChat = new HashMap<>();
    private final Map<String, Long> handled = new HashMap<>();
    private Set<String> advertised = Set.of();
    @Override public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled")) { getLogger().info("PulseNetwork is disabled until configured."); return; }
        try { wire = new Wire(getConfig().getString("bridge-secret", "")); }
        catch (IllegalArgumentException e) { getLogger().severe(e.getMessage()); return; }
        getServer().getMessenger().registerOutgoingPluginChannel(this, Wire.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Wire.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::queues, 20, 100);
        chatGames = new LocalChatGames(this);
        getServer().getScheduler().runTaskTimer(this, chatGames::tick, 20, 20);
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
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        // Reading after dispatch also respects cancellation/restricted viewers by later handlers.
        getServer().getScheduler().runTask(this, () -> {
            Player player = event.getPlayer(); UUID id = player.getUniqueId();
            if (event.isCancelled() || !player.isOnline() || player.getMetadata("vanished").stream().anyMatch(org.bukkit.metadata.MetadataValue::asBoolean)) return;
            // A private/local-radius/staff channel must never accidentally become public.
            if (!event.viewers().contains(player) || !event.viewers().containsAll(Bukkit.getOnlinePlayers())) return;
            String body = Wire.plain(PlainTextComponentSerializer.plainText().serialize(event.message()));
            if (body.isBlank() || body.length() > 512) return;
            if (chatGames != null && chatGames.answer(player, body)) return;
            if (!getConfig().getBoolean("chat.enabled") || !player.hasPermission("pulsenetwork.chat")
                    || !global.getOrDefault(id, getConfig().getBoolean("chat.global-by-default"))) return;
            long now = System.currentTimeMillis();
            if (now - lastChat.getOrDefault(id, 0L) < 1000) return;
            lastChat.put(id, now);
            send(player, "CHAT", body);
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) { global.remove(event.getPlayer().getUniqueId()); lastChat.remove(event.getPlayer().getUniqueId()); }
    private void send(Player carrier, String kind, String... fields) { carrier.sendPluginMessage(this, Wire.CHANNEL, wire.encode(kind, carrier.getUniqueId(), fields)); }

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
    @Override public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!channel.equals(Wire.CHANNEL) || wire == null || !getConfig().getBoolean("queues.enabled")) return;
        wire.decode(bytes).filter(m -> m.kind().equals("JOIN") && m.player().equals(player.getUniqueId()) && m.fields().size() == 2).ifPresent(m -> {
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
                    handled.put(key, now + 30_000);
                } catch (ReflectiveOperationException e) { player.sendMessage(Component.text("This server needs the queue support update.")); return; }
            }
            send(player, "ACK", request);
        });
    }
}
