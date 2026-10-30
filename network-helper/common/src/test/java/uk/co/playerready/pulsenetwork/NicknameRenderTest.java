package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NicknameRenderTest {
    static final String KEY = "test-only-not-a-deployment-secret-0123456789";
    final NameStyle.Tags tags = NameStyle.Tags.DEFAULT;
    final UUID sender = UUID.randomUUID(), viewer = UUID.randomUUID();
    final NameStyle.Identity plain = new NameStyle.Identity("Aurora", "dev", null, 12);
    final NameStyle.Identity nicked = new NameStyle.Identity("Aurora", "dev", null, 12, "Captain_A");

    static String text(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    static boolean hasHover(Component c) { return c.hoverEvent() != null || c.children().stream().anyMatch(NicknameRenderTest::hasHover); }
    List<String> roundTrip(String... fields) { var w = new Wire(KEY); return w.decode(w.encode("REMOTECHAT", viewer, fields)).orElseThrow().fields(); }

    @Test void displayNameIsTheNicknameOrTheUsername() {
        assertEquals("Aurora", plain.displayName()); assertEquals("Captain_A", nicked.displayName());
        assertEquals("Aurora", new NameStyle.Identity("Aurora", null, null, null, "").displayName());
    }
    @Test void javaChatShowsNicknameWithRealNameOnHover() {
        var c = NameStyle.renderChat(nicked, false, tags);
        assertEquals("[DEV] [12] Captain_A", text(c));
        assertTrue(hasHover(c));
        assertEquals("Real name: Aurora", text(((HoverEvent<Component>) c.hoverEvent()).value()));
    }
    @Test void bedrockChatShowsNickThenUsernameAndNoHover() {
        var c = NameStyle.renderChat(nicked, true, tags);
        assertEquals("[DEV] [12] Captain_A (Aurora)", text(c));
        assertFalse(hasHover(c));
    }
    @Test void tabRowIsPlainEvenForJava() {
        var c = NameStyle.render(nicked, false, 0, tags, NameStyle.MAX_VISIBLE, false);
        assertEquals("[DEV] [12] Captain_A", text(c)); assertFalse(hasHover(c));
    }
    @Test void noNicknameRendersExactlyAsBefore() {
        for (boolean bedrock : new boolean[]{false, true}) {
            assertEquals("[DEV] [12] Aurora", text(NameStyle.renderChat(plain, bedrock, tags)));
            assertFalse(hasHover(NameStyle.renderChat(plain, bedrock, tags)));
            assertEquals(NameStyle.render(plain, bedrock, 0, tags, NameStyle.MAX_VISIBLE), NameStyle.renderChat(plain, bedrock, tags));
        }
    }
    @Test void presenceLineUsesTheNickname() {
        assertEquals("Captain_A joined", text(PresenceRenderer.render("<name> joined", new NameStyle.Identity("Aurora", null, null, null, "Captain_A"), false, "", null, tags)));
    }
    @Test void remoteChatFieldsKeepFourSevenAndEightFormsWorking() {
        var four = roundTrip("lobby", sender.toString(), "Aurora", "hi");
        var seven = roundTrip(RemoteChat.fields("lobby", sender, plain, "hi"));
        var eight = roundTrip(RemoteChat.fields("lobby", sender, nicked, "hi"));
        assertEquals(List.of(4, 7, 8), List.of(four.size(), seven.size(), eight.size()));
        assertTrue(List.of(four, seven, eight).stream().allMatch(f -> RemoteChat.validCount(f.size())));
        assertFalse(RemoteChat.validCount(5)); assertFalse(RemoteChat.validCount(9));
        assertEquals(new NameStyle.Identity("Aurora", null, null, null), RemoteChat.identity(four, tags));
        assertEquals(plain, RemoteChat.identity(seven, tags));
        assertEquals(nicked, RemoteChat.identity(eight, tags));
    }
    @Test void aBadNicknameOnTheWireIsDropped() {
        var f = new ArrayList<>(Arrays.asList(RemoteChat.fields("lobby", sender, nicked, "hi")));
        f.set(7, "<red>Evil");
        assertNull(RemoteChat.identity(f, tags).nickname());
    }
    @Test void remoteChatLineMatchesPerViewer() {
        var eight = RemoteChat.identity(Arrays.asList(RemoteChat.fields("lobby", sender, nicked, "hi")), tags);
        assertEquals("[Global · lobby] [DEV] [12] Captain_A: hi", text(RemoteChat.line("lobby", eight, "hi", false, tags)));
        assertEquals("[Global · lobby] [DEV] [12] Captain_A (Aurora): hi", text(RemoteChat.line("lobby", eight, "hi", true, tags)));
    }
    @Test void menuNickCommandIsValidatedAndBare() {
        assertEquals(Optional.of("nick Ann Captain_A"), RoleMenuCommands.fromFields(List.of("nick", "Ann", "Captain_A")));
        assertEquals(Optional.of("nick Ann reset"), RoleMenuCommands.fromFields(List.of("nick", "Ann", "reset")));
        assertTrue(RoleMenuCommands.fromFields(List.of("nick", "Ann", "a b")).isEmpty());
        assertTrue(RoleMenuCommands.fromFields(List.of("nick", "Ann")).isEmpty());
        assertTrue(RoleMenuCommands.fromFields(List.of("nick", "Ann", "x")).isEmpty());
    }
}
