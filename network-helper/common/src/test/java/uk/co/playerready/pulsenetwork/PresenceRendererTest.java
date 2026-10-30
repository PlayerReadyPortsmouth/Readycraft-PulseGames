package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresenceRendererTest {
    static String plain(net.kyori.adventure.text.Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    final NameStyle.Identity sam = new NameStyle.Identity("Sam", "dev", "red", 12);

    @Test void javaLinesUseArrowsTagAndBadge() {
        var t = NameStyle.Tags.DEFAULT;
        assertEquals("→ [DEV] [12] Sam joined the network", plain(PresenceRenderer.render(PresenceConfig.JOIN, sam, false, "Lobby", null, t)));
        assertEquals("⇄ [DEV] [12] Sam hopped to Survival", plain(PresenceRenderer.render(PresenceConfig.SWITCH, sam, false, "Survival", "Lobby", t)));
        assertEquals("← [DEV] [12] Sam left", plain(PresenceRenderer.render(PresenceConfig.LEAVE, sam, false, "Lobby", null, t)));
    }
    @Test void bedrockLinesUsePlainAsciiMarkers() {
        var t = NameStyle.Tags.DEFAULT;
        assertEquals(">> [DEV] [12] Sam joined the network", plain(PresenceRenderer.render(PresenceConfig.JOIN, sam, true, "Lobby", null, t)));
        assertEquals("<> [DEV] [12] Sam hopped to Survival", plain(PresenceRenderer.render(PresenceConfig.SWITCH, sam, true, "Survival", "Lobby", t)));
        assertEquals("<< [DEV] [12] Sam left", plain(PresenceRenderer.render(PresenceConfig.LEAVE, sam, true, "Lobby", null, t)));
    }
    @Test void fromPlaceholderAndLiteralNames() {
        var weird = new NameStyle.Identity("<red>Bob</red>", null, null, null);
        assertEquals("<red>Bob</red> from Lobby to Survival", plain(PresenceRenderer.render("<name> from <from> to <server>", weird, false, "Survival", "Lobby", NameStyle.Tags.DEFAULT)));
    }
    @Test void welcomeBackTemplate() {
        assertEquals("Welcome back, Sam!", plain(PresenceRenderer.render(PresenceConfig.WELCOME, new NameStyle.Identity("Sam", null, null, null), false, "", null, NameStyle.Tags.DEFAULT)));
    }
}
