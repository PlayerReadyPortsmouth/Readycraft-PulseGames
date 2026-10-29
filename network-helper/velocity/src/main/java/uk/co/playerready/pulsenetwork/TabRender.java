package uk.co.playerready.pulsenetwork;

import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure rendering: rows for the player list and header/footer text. Player-controlled strings are never parsed as MiniMessage. */
final class TabRender {
    /** One tab entry. {@code local} rows live on the viewer's backend and already exist there; others are added by the proxy. */
    record Row(UUID id, String name, Component display, int latency, int order, boolean header, boolean local) {
        GameProfile profile() { return new GameProfile(id, name, List.of()); }
    }
    record Context(String viewer, String viewerServer, Map<String, Integer> counts, int networkOnline, ServerStatus status,
                   boolean staff, boolean bedrock, String version, long epochSeconds) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private TabRender() {}

    /** Round to 25 ms so jitter does not cause a packet per viewer every refresh. */
    static int quantise(long ping) { return (int) Math.max(0, Math.min(9999, Math.round(ping / 25.0) * 25)); }

    static UUID headerId(String server) { return UUID.nameUUIDFromBytes(("pulsenetwork-tab:" + server).getBytes(StandardCharsets.UTF_8)); }

    static Component displayName(TabRoster.Member m, TabConfig config, boolean bedrockViewer) {
        var out = Component.text().append(Component.empty());
        if (m.bedrock() && !config.bedrockMarker().isBlank()) out.append(MM.deserialize(config.bedrockMarker()));
        if (m.prefix() != null && !m.prefix().isBlank()) {
            out.append(LEGACY.deserialize(m.prefix().replace('§', '&')));
            if (!Character.isWhitespace(m.prefix().charAt(m.prefix().length() - 1))) out.append(Component.space());
        }
        out.append(Component.text(m.name()));
        if (m.suffix() != null && !m.suffix().isBlank()) out.append(LEGACY.deserialize(m.suffix().replace('§', '&')));
        if (m.afk()) out.append(Component.text(" (AFK)", NamedTextColor.GRAY));
        if (m.match()) out.append(Component.text(" [In a game]", NamedTextColor.GOLD));
        return bedrockViewer ? plain(out.build()) : out.build();
    }

    /** Rows in display order, including optional per-server header rows. List order is only honoured by 1.21.2+ clients. */
    static List<Row> rows(List<TabRoster.Group> groups, String viewerServer, TabConfig config, boolean bedrockViewer) {
        var slots = new ArrayList<Row>();
        for (var g : groups) {
            if (config.headers()) {
                String name = "~" + g.server();
                Component text = group(config, g);
                slots.add(new Row(headerId(g.server()), name.substring(0, Math.min(16, name.length())), bedrockViewer ? plain(text) : text, 0, 0, true, false));
            }
            for (var m : g.members())
                slots.add(new Row(m.id(), m.name(), displayName(m, config, bedrockViewer), quantise(m.ping()), 0, false, g.server().equals(viewerServer)));
        }
        var result = new ArrayList<Row>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            var r = slots.get(i);
            result.add(new Row(r.id(), r.name(), r.display(), r.latency(), config.higherFirst() ? slots.size() - i : i, r.header(), r.local()));
        }
        return result;
    }
    private static Component group(TabConfig config, TabRoster.Group g) {
        return MM.deserialize(config.headerFormat(), Placeholder.unparsed("server", config.name(g.server())),
                Placeholder.unparsed("count", Integer.toString(g.members().size())));
    }

    /** {header, footer}. Lines that render blank (for example missing status) are dropped. */
    static Component[] headerFooter(TabConfig config, Context ctx) {
        var counts = new ArrayList<Component>();
        var tags = TagResolver.builder();
        ctx.counts().forEach((server, n) -> {
            counts.add(Component.text(config.name(server) + " " + n));
            if (server.matches("[a-z0-9_]+")) tags.resolver(Placeholder.unparsed("online_" + server, Integer.toString(n)));
        });
        var status = ctx.status();
        String tip = config.tips().isEmpty() ? "" : config.tips().get((int) ((ctx.epochSeconds() / config.tipSeconds()) % config.tips().size()));
        tags.resolvers(Placeholder.unparsed("player", ctx.viewer()), Placeholder.unparsed("server", config.name(ctx.viewerServer())),
                Placeholder.unparsed("server_id", ctx.viewerServer()), Placeholder.unparsed("network_online", Integer.toString(ctx.networkOnline())),
                Placeholder.unparsed("server_count", Integer.toString(ctx.counts().getOrDefault(ctx.viewerServer(), 0))),
                Placeholder.unparsed("server_online", status == null ? "" : Integer.toString(status.online())),
                Placeholder.unparsed("tps", status == null ? "" : String.format(Locale.ROOT, "%.1f", Math.min(20.0, status.tps()))),
                Placeholder.unparsed("mspt", status == null ? "" : String.format(Locale.ROOT, "%.1f", status.mspt())),
                Placeholder.component("counts", Component.join(JoinConfiguration.separator(Component.text(" · ")), counts)),
                Placeholder.component("status_line", statusLine(status)),
                Placeholder.parsed("tip", tip),
                Placeholder.unparsed("client", ctx.bedrock() ? "Bedrock" : "Java"), Placeholder.unparsed("version", ctx.version()));
        var resolver = tags.build();
        var footer = config.footer();
        if (ctx.staff() && !config.staffLine().isBlank()) footer = footer + "\n" + config.staffLine();
        Component h = lines(config.header(), resolver), f = lines(footer, resolver);
        return ctx.bedrock() ? new Component[]{plain(h), plain(f)} : new Component[]{h, f};
    }
    private static Component statusLine(ServerStatus s) {
        if (s == null) return Component.empty();
        var colour = s.tps() >= 18 ? NamedTextColor.GREEN : s.tps() >= 15 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text(String.format(Locale.ROOT, "TPS %.1f · MSPT %.1f", Math.min(20.0, s.tps()), s.mspt()), colour);
    }
    private static Component lines(String template, TagResolver resolver) {
        var out = new ArrayList<Component>();
        for (String line : template.split("\n")) {
            Component c = MM.deserialize(line, resolver);
            if (!PlainTextComponentSerializer.plainText().serialize(c).isBlank()) out.add(c);
        }
        return Component.join(JoinConfiguration.newlines(), out);
    }

    /** Bedrock fallback: keep colour and basic styling, drop hover/click/insertion and obfuscation. */
    static Component plain(Component c) {
        return c.children(c.children().stream().map(TabRender::plain).toList()).hoverEvent(null).clickEvent(null).insertion(null)
                .decoration(TextDecoration.OBFUSCATED, false);
    }
}
