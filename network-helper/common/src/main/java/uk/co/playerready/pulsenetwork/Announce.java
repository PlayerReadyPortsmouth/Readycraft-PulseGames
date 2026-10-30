package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import java.util.*;
import java.util.function.LongSupplier;

/** Pure rules for announcements: argument parsing, text clean-up, the colour-tag allowlist and the rate limit. */
public final class Announce {
    public static final int MAX_TEXT = 256;
    public static final Set<String> STYLES = Set.of("chat", "title", "bar");
    public static final Set<String> SCOPES = Set.of("network", "here");
    private static final MiniMessage SAFE = MiniMessage.builder().tags(TagResolver.builder().resolvers(
            colour("red", NamedTextColor.RED), colour("gold", NamedTextColor.GOLD), colour("green", NamedTextColor.GREEN),
            colour("aqua", NamedTextColor.AQUA), colour("gray", NamedTextColor.GRAY), colour("white", NamedTextColor.WHITE),
            TagResolver.resolver("bold", Tag.styling(TextDecoration.BOLD)), StandardTags.reset()).build()).build();
    private Announce() {}
    private static TagResolver colour(String name, NamedTextColor c) { return TagResolver.resolver(name, Tag.styling(c)); }

    public record Request(String style, String text) {}

    /** {@code [-title|-bar] message...}; throws with a player-safe sentence when unusable. */
    public static Request parse(String[] args) {
        String style = "chat"; int i = 0;
        for (; i < args.length && args[i].startsWith("-"); i++) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            if (!flag.equals("-title") && !flag.equals("-bar")) throw new IllegalArgumentException("Unknown option. Use -title or -bar.");
            if (!style.equals("chat")) throw new IllegalArgumentException("Use only one of -title or -bar.");
            style = flag.substring(1);
        }
        String text = clean(String.join(" ", Arrays.copyOfRange(args, i, args.length)));
        if (text == null) throw new IllegalArgumentException("Use /announce [-title|-bar] <message>.");
        return new Request(style, text);
    }
    /** Control characters removed, spaces collapsed, capped at {@value #MAX_TEXT}; null when nothing is left. */
    public static String clean(String raw) {
        if (raw == null) return null;
        String text = Wire.plain(raw).replaceAll("\\s+", " ").strip();
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT).strip();
        return text.isEmpty() ? null : text;
    }
    public static String style(String value) {
        String s = value == null || value.isBlank() ? "chat" : value.strip().toLowerCase(Locale.ROOT);
        return STYLES.contains(s) ? s : null;
    }
    /** Typed text: only the allowlisted colour tags apply; any other tag is shown as literal text, never run. */
    public static Component body(String text) { return SAFE.deserialize(text); }
    /** {@code [Network] message}. */
    public static Component chatLine(String text) {
        return Component.text("[Network] ", NamedTextColor.GOLD).append(Component.text().color(NamedTextColor.WHITE).append(body(text)).build());
    }
    /** Title and subtitle: the text before and after the first {@code |}. */
    public static Component[] titleParts(String text) {
        int bar = text.indexOf('|');
        String main = bar < 0 ? text : text.substring(0, bar).strip(), sub = bar < 0 ? "" : text.substring(bar + 1).strip();
        return new Component[]{Component.text().color(NamedTextColor.GOLD).append(body(main)).build(),
                Component.text().color(NamedTextColor.WHITE).append(body(sub)).build()};
    }
    public static Component barLine(String text) { return Component.text().color(NamedTextColor.WHITE).append(body(text)).build(); }

    /** One announcement per interval, shared by the command and the backend wire. */
    public static final class Limiter {
        private final long intervalMs; private final LongSupplier clock; private long last = Long.MIN_VALUE / 2;
        public Limiter(long intervalMs, LongSupplier clock) { this.intervalMs = intervalMs; this.clock = clock; }
        public synchronized boolean tryAcquire() {
            long now = clock.getAsLong();
            if (now - last < intervalMs) return false;
            last = now; return true;
        }
    }
}
