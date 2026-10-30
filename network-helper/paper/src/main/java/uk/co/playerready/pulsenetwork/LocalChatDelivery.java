package uk.co.playerready.pulsenetwork;

import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.*;

/**
 * Pure parts of local system-message delivery: sends each original viewer the server-rendered line as a system
 * message, so signed-chat validation (Velocity, Floodgate/Bedrock) cannot hide it. Never adds viewers, never parses text.
 */
final class LocalChatDelivery {
    static final String DEFAULT_FORMAT = "<name>: <message>";
    private LocalChatDelivery() {}

    /** True when the sender sees the message and every online player is a viewer (an unrestricted public message). */
    static boolean isPublic(Player sender, Collection<? extends Audience> viewers, Collection<? extends Player> online) {
        return viewers.contains(sender) && viewers.containsAll(online);
    }

    /** Literal substitution of {@code <name>} and {@code <message>} only; nothing is parsed as MiniMessage. */
    static Component fallback(String template, Component name, Component message) {
        String t = template == null || template.isBlank() ? DEFAULT_FORMAT : template;
        var out = Component.text();
        int i = 0;
        while (i < t.length()) {
            int n = t.indexOf("<name>", i), m = t.indexOf("<message>", i);
            int next = n < 0 ? m : m < 0 ? n : Math.min(n, m);
            if (next < 0) { out.append(Component.text(t.substring(i))); break; }
            if (next > i) out.append(Component.text(t.substring(i, next)));
            if (next == n) { out.append(name); i = n + 6; } else { out.append(message); i = m + 9; }
        }
        return out.build();
    }

    /**
     * Renders per viewer with the event's own renderer and sends it as a system message to a snapshot of the original
     * viewers. Returns the snapshot; the caller clears the live set afterwards. The console, if it was a viewer,
     * receives its single line here, so no extra log line is written.
     */
    static List<Audience> deliver(Collection<? extends Audience> viewers, ChatRenderer renderer, Player sender,
                                  Component displayName, Component message, String template) {
        return deliver(viewers, renderer, sender, v -> displayName, message, template);
    }

    /** Floodgate (Bedrock) players have a zero most-significant UUID half. */
    static boolean bedrock(Audience viewer) { return viewer instanceof Player p && p.getUniqueId() != null && p.getUniqueId().getMostSignificantBits() == 0; }

    /** As above, with the sender's name chosen per viewer (Bedrock viewers get a flattened style). */
    static List<Audience> deliver(Collection<? extends Audience> viewers, ChatRenderer renderer, Player sender,
                                  java.util.function.Function<Audience, Component> nameFor, Component message, String template) {
        var snapshot = new ArrayList<Audience>(viewers);
        for (Audience viewer : snapshot) {
            Component line = null, displayName = nameFor.apply(viewer);
            try { if (renderer != null) line = renderer.render(sender, displayName, message, viewer); }
            catch (RuntimeException e) { /* fall back below */ }
            if (line == null) line = fallback(template, displayName, message);
            viewer.sendMessage(line);
        }
        return snapshot;
    }
}
