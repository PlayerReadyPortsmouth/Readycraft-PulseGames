package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.TextReplacementConfig;

/** Renders presence templates. Only the template is MiniMessage; the name is a styled component and server names are literal text. */
public final class PresenceRenderer {
    private PresenceRenderer() {}

    public static Component render(String template, NameStyle.Identity id, boolean bedrockViewer, String server, String from, NameStyle.Tags tags) {
        Component line = MiniMessage.miniMessage().deserialize(template,
                Placeholder.component("name", NameStyle.render(id, bedrockViewer, 0, tags, NameStyle.MAX_VISIBLE)),
                Placeholder.unparsed("server", server == null ? "" : server), Placeholder.unparsed("from", from == null ? "" : from));
        return bedrockViewer ? bedrockSafe(line) : line;
    }
    /** Arrows may be missing from a Bedrock client's font, so they become plain ASCII. */
    static Component bedrockSafe(Component line) {
        return line.replaceText(TextReplacementConfig.builder().matchLiteral("→").replacement(">>").build())
                .replaceText(TextReplacementConfig.builder().matchLiteral("⇄").replacement("<>").build())
                .replaceText(TextReplacementConfig.builder().matchLiteral("←").replacement("<<").build());
    }
}
