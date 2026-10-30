package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RemoteChatTest {
    static final String KEY = "test-only-not-a-deployment-secret-0123456789";
    final UUID sender = UUID.randomUUID(), viewer = UUID.randomUUID();
    final NameStyle.Tags tags = NameStyle.Tags.DEFAULT;
    final NameStyle.Identity styled = new NameStyle.Identity("Aurora", "dev", "sunset", 12);

    List<String> roundTrip(String... fields) {
        var wire = new Wire(KEY);
        return wire.decode(wire.encode("REMOTECHAT", viewer, fields)).orElseThrow().fields();
    }
    static String text(net.kyori.adventure.text.Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }

    @Test void sevenFieldsSurviveTheWireAndRebuildTheIdentity() {
        var fields = roundTrip(RemoteChat.fields("lobby", sender, styled, "hi"));
        assertEquals(7, fields.size());
        assertTrue(RemoteChat.validCount(fields.size()));
        assertEquals(styled, RemoteChat.identity(fields, tags));
    }
    @Test void fourFieldsFromAnOlderProxyGiveThePlainName() {
        var fields = roundTrip("lobby", sender.toString(), "Aurora", "hi");
        assertTrue(RemoteChat.validCount(fields.size()));
        assertEquals(new NameStyle.Identity("Aurora", null, null, null), RemoteChat.identity(fields, tags));
        assertEquals("[Global \u00b7 lobby] Aurora: hi", text(RemoteChat.line("lobby", RemoteChat.identity(fields, tags), "hi", false, tags)));
        assertFalse(RemoteChat.validCount(5));
        assertFalse(RemoteChat.validCount(6));
    }
    @Test void absentExtrasAndForgedValuesAreDropped() {
        assertEquals(new NameStyle.Identity("Aurora", null, null, null),
                RemoteChat.identity(List.of("lobby", sender.toString(), "Aurora", "hi", "", "", ""), tags));
        assertEquals(new NameStyle.Identity("Aurora", null, null, null),
                RemoteChat.identity(List.of("lobby", sender.toString(), "Aurora", "hi", "owner", "<red>x", "-5"), tags));
        assertNull(RemoteChat.identity(List.of("lobby", sender.toString(), "Aurora", "hi", "dev", "", "99999"), tags).level());
    }
    @Test void backendRenderEqualsProxyRenderForJavaAndBedrock() {
        // The proxy renders its own copy from the live identity; a backend renders from the fields it received.
        var received = RemoteChat.identity(roundTrip(RemoteChat.fields("lobby", sender, styled, "hi")), tags);
        for (boolean bedrock : new boolean[]{false, true}) {
            var proxy = RemoteChat.line("lobby", styled, "hi", bedrock, tags);
            assertEquals(proxy, RemoteChat.line("lobby", received, "hi", bedrock, tags));
            assertEquals("[Global \u00b7 lobby] [DEV] [12] Aurora: hi", text(proxy));
        }
        assertNotEquals(RemoteChat.line("lobby", styled, "hi", false, tags), RemoteChat.line("lobby", styled, "hi", true, tags));
    }
}
