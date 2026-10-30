package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The cross-server chat line and its {@code REMOTECHAT} fields, shared by the proxy relay and Paper backends so both
 * render character for character the same. Fields: server, sender uuid, sender name, body, then optionally role,
 * style id and level (empty when absent). A 4-field message (older proxy) renders the plain name.
 */
public final class RemoteChat {
    public static final int PLAIN_FIELDS = 4, STYLED_FIELDS = 7;
    private RemoteChat() {}

    public static String[] fields(String server, UUID sender, NameStyle.Identity id, String body) {
        return new String[]{server, sender.toString(), id.name(), body, id.role() == null ? "" : id.role(),
                id.styleId() == null ? "" : id.styleId(), id.level() == null ? "" : Integer.toString(id.level())};
    }

    public static boolean validCount(int count) { return count == PLAIN_FIELDS || count == STYLED_FIELDS; }

    /** The sender's identity from received fields; unknown roles, styles and odd levels are dropped, never trusted. */
    public static NameStyle.Identity identity(List<String> fields, NameStyle.Tags tags) {
        String name = Wire.plain(fields.get(2));
        if (fields.size() < STYLED_FIELDS) return new NameStyle.Identity(name, null, null, null);
        String role = fields.get(4).toLowerCase(java.util.Locale.ROOT), style = fields.get(5).toLowerCase(java.util.Locale.ROOT);
        Integer level = null;
        if (fields.get(6).matches("[0-9]{1,4}")) level = Optional.of(Integer.parseInt(fields.get(6))).filter(l -> l > 0).orElse(null);
        return new NameStyle.Identity(name, tags.has(role) ? role : null, NameStyle.hasStyle(style) ? style : null, level);
    }

    /** {@code [Global · server] [TAG] [12] name: body}, static frame. */
    public static Component line(String server, NameStyle.Identity id, String body, boolean bedrockViewer, NameStyle.Tags tags) {
        return Component.text("[Global · " + server + "] ", NamedTextColor.GRAY)
                .append(Component.text().color(NamedTextColor.AQUA).append(NameStyle.render(id, bedrockViewer, 0, tags, NameStyle.MAX_VISIBLE))
                        .append(Component.text(": ")).build()).append(Component.text(body, NamedTextColor.WHITE));
    }
}
