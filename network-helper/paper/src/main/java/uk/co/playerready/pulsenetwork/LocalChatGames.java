package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import java.util.*;

/** Optional local-only fallback, suppressed when another chat-game provider is installed. */
final class LocalChatGames {
    private final PaperBridge plugin;
    private String answer;
    private long deadline, next;
    LocalChatGames(PaperBridge plugin) { this.plugin = plugin; next = System.currentTimeMillis() + 60_000; }
    private boolean externalProvider() {
        var configured = plugin.getConfig().getStringList("chat-games.external-provider-plugins");
        return Arrays.stream(plugin.getServer().getPluginManager().getPlugins()).anyMatch(p -> p.isEnabled()
                && (configured.stream().anyMatch(name -> name.equalsIgnoreCase(p.getName())) || p.getName().toLowerCase(Locale.ROOT).startsWith("chatgames")));
    }
    void tick() {
        if (!plugin.getConfig().getBoolean("chat-games.enabled") || externalProvider()) { answer = null; return; }
        long now = System.currentTimeMillis();
        if (answer != null) {
            if (now >= deadline) { broadcast("Time's up! The answer was " + answer + "."); answer = null; }
            return;
        }
        if (now < next || plugin.getServer().getOnlinePlayers().size() < Math.max(1, plugin.getConfig().getInt("chat-games.minimum-players", 2))) return;
        ChatChallenge challenge = ChatChallenge.random();
        answer = challenge.answer();String question = challenge.question();
        deadline = now + 30_000;
        next = now + Math.max(60, plugin.getConfig().getInt("chat-games.interval-seconds", 300)) * 1000L;
        broadcast(question + " Type your answer in local chat. You have 30 seconds.");
    }
    boolean answer(Player player, String message) {
        if (answer == null || System.currentTimeMillis() >= deadline || externalProvider() || !answer.equalsIgnoreCase(message.trim())) return false;
        broadcast(player.getName() + " got it! The answer was " + answer + "."); answer = null; return true;
    }
    private void broadcast(String text) {
        Component component = Component.text("[Chat game] ", NamedTextColor.AQUA).append(Component.text(text, NamedTextColor.YELLOW));
        plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(component));
    }
}
