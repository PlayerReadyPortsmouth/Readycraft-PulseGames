package uk.co.playerready.pulsenetwork;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Explicit adapters: public AdvancedChat events, native mute/ignore decisions. */
final class ChatCompatibility {
    private final PaperBridge bridge;
    private final Plugin advanced, essentials;
    private Object advancedApi;
    ChatCompatibility(PaperBridge bridge) {
        this.bridge = bridge;
        advanced = enabled("AdvancedChat"); essentials = enabled("Essentials");
    }
    private Plugin enabled(String name) {
        var p = bridge.getServer().getPluginManager().getPlugin(name);
        return p != null && p.isEnabled() ? p : null;
    }
    boolean hasAdvancedChat() { return advanced != null; }
    void start() {
        if (advanced == null) return;
        try {
            var loader = advanced.getClass().getClassLoader();
            advancedApi = Class.forName("net.advancedplugins.chat.api.AdvancedChatAPI", true, loader).getMethod("getApi").invoke(null);
            Class<? extends Event> type = Class.forName("net.advancedplugins.chat.api.AdvancedChatEvent", true, loader).asSubclass(Event.class);
            bridge.getServer().getPluginManager().registerEvent(type, bridge, EventPriority.MONITOR, (listener, event) ->
                bridge.getServer().getScheduler().runTask(bridge, () -> publicAdvanced(event)), bridge, true);
            bridge.getLogger().info("AdvancedChat public-event and recipient-ignore adapter ready.");
        } catch (ReflectiveOperationException | LinkageError e) {
            bridge.getLogger().warning("AdvancedChat adapter unavailable; its outgoing global chat remains blocked.");
        }
    }
    void publicAdvanced(Event event) {
        try {
            if (event instanceof Cancellable c && c.isCancelled()) return;
            if (((Number) call(event, "getRange")).doubleValue() > 0) return;
            var raw = (AsyncPlayerChatEvent) call(event, "getChatEvent");
            Player player = raw.getPlayer();
            // AdvancedChat deliberately cancels its raw event to apply its renderer.
            // Only this post-moderation public event is eligible; channel events never are.
            if (advancedApi == null || Boolean.TRUE.equals(call(advancedApi, "isPlayerInChannel", UUID.class, player.getUniqueId()))) return;
            bridge.relayPublic(player, (String) call(event, "getMessage"));
        } catch (ReflectiveOperationException | RuntimeException e) { /* fail closed */ }
    }
    boolean maySend(Player player) {
        if (essentials == null) return true;
        try {
            Object user = call(essentials, "getUser", UUID.class, player.getUniqueId());
            return user != null && !Boolean.TRUE.equals(call(user, "isMuted")) && !Boolean.TRUE.equals(call(user, "isVanished"));
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    boolean mayReceive(Player receiver, UUID sender, String name) {
        try {
            if (advanced != null) {
                if (advancedApi == null || Boolean.TRUE.equals(call(advancedApi, "hasAllPlayersIgnored", Player.class, receiver))) return false;
                var ignored = (Collection<?>) call(advancedApi, "getIgnoredPlayers", Player.class, receiver);
                if (ignored.stream().anyMatch(v -> name.equalsIgnoreCase(String.valueOf(v)) || sender.toString().equalsIgnoreCase(String.valueOf(v)))) return false;
            }
            if (essentials != null) {
                Object user = call(essentials, "getUser", UUID.class, receiver.getUniqueId());
                if (user == null || ((Collection<?>) call(user, "_getIgnoredPlayers")).contains(sender)
                        || Boolean.TRUE.equals(call(user, "isIgnoredPlayer", String.class, name))) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    private static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
    private static Object call(Object target, String method, Class<?> type, Object arg) throws ReflectiveOperationException {
        return target.getClass().getMethod(method, type).invoke(target, arg);
    }
}
