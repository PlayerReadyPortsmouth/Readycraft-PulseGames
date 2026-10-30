package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnounceTest {
    static String plain(Component c) { return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c); }
    static boolean anyEvent(Component c) {
        return c.clickEvent() != null || c.hoverEvent() != null || c.children().stream().anyMatch(AnnounceTest::anyEvent);
    }
    @Test void parsesFlagsAndText() {
        assertEquals(new Announce.Request("chat", "hello there"), Announce.parse(new String[]{"hello", "there"}));
        assertEquals(new Announce.Request("title", "Big | news"), Announce.parse(new String[]{"-TITLE", "Big", "|", "news"}));
        assertEquals("bar", Announce.parse(new String[]{"-bar", "x"}).style());
        assertThrows(IllegalArgumentException.class, () -> Announce.parse(new String[]{"-title", "-bar", "x"}));
        assertThrows(IllegalArgumentException.class, () -> Announce.parse(new String[]{"-nope", "x"}));
        assertThrows(IllegalArgumentException.class, () -> Announce.parse(new String[]{"-bar"}));
    }
    @Test void cleansControlCharactersAndLength() {
        assertEquals("a b", Announce.clean("a\u0000\n  b§"));
        assertEquals(Announce.MAX_TEXT, Announce.clean("x".repeat(1000)).length());
        assertNull(Announce.clean(" \u0007 "));
    }
    @Test void allowlistedColoursWorkAndEverythingElseIsLiteral() {
        Component ok = Announce.body("<red>hot</red> <bold>very</bold> <aqua>cool");
        assertEquals("hot very cool", plain(ok));
        assertTrue(ok.children().stream().anyMatch(c -> NamedTextColor.RED.equals(c.color())));
        for (String evil : List.of("<click:run_command:/op me>x</click>", "<hover:show_text:'hi'>x</hover>", "<rainbow>x</rainbow>",
                "<gradient:red:blue>x</gradient>", "<#ff0000>x", "<insert:y>x", "<font:uniform>x", "<obfuscated>x", "<key:key.jump>"))  {
            Component c = Announce.body(evil);
            assertFalse(anyEvent(c), evil); assertEquals(evil, plain(c), evil);
        }
    }
    @Test void chatLineAndTitleSplit() {
        assertEquals("[Network] hi", plain(Announce.chatLine("hi")));
        var parts = Announce.titleParts("Big news | Details");
        assertEquals("Big news", plain(parts[0])); assertEquals("Details", plain(parts[1]));
        assertEquals("", plain(Announce.titleParts("Only")[1]));
    }
    @Test void styleValidation() {
        assertEquals("chat", Announce.style(null)); assertEquals("bar", Announce.style(" BAR ")); assertNull(Announce.style("shout"));
    }
    @Test void rateLimitOnePerFiveSeconds() {
        long[] now = {1_000};
        var limiter = new Announce.Limiter(5000, () -> now[0]);
        assertTrue(limiter.tryAcquire()); now[0] += 4999; assertFalse(limiter.tryAcquire());
        now[0] += 1; assertTrue(limiter.tryAcquire());
    }
    @Test void announceWireRoundTrip() {
        String key = "test-only-not-a-deployment-secret-0123456789";
        Clock now = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        var m = new Wire(key, now).decode(new Wire(key, now).encode("ANNOUNCE", id, "Bedwars soon <red>!", "title", "network")).orElseThrow();
        assertEquals("ANNOUNCE", m.kind()); assertEquals(id, m.player()); assertEquals(List.of("Bedwars soon <red>!", "title", "network"), m.fields());
    }
}
