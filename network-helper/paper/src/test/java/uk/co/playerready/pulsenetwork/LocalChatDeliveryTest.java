package uk.co.playerready.pulsenetwork;

import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalChatDeliveryTest {
    static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    final Player sender = mock(Player.class);
    final Audience other = mock(Audience.class), console = mock(Audience.class);
    final ChatRenderer renderer = (source, name, message, viewer) -> Component.text("<").append(name).append(Component.text("> ")).append(message);

    @Test void fallbackSubstitutesLiterallyAndNeverParsesMiniMessage() {
        var line = LocalChatDelivery.fallback("<name>: <message>", Component.text("Ann"), Component.text("<red>hi</red> <name>"));
        assertEquals("Ann: <red>hi</red> <name>", plain(line));
        assertEquals("Ann: x", plain(LocalChatDelivery.fallback(" ", Component.text("Ann"), Component.text("x"))));
    }
    @Test void everyOriginalViewerGetsOneSystemLineAndNoOneElse() {
        var viewers = new LinkedHashSet<Audience>(List.of(sender, other, console));
        var snapshot = LocalChatDelivery.deliver(viewers, renderer, sender, Component.text("Ann"), Component.text("hello"), null);
        assertEquals(3, snapshot.size());
        for (Audience a : snapshot) verify(a, times(1)).sendMessage(argThat((Component c) -> plain(c).equals("<Ann> hello")));
        assertEquals(3, viewers.size(), "deliver itself must not change the live set");
    }
    @Test void trimmedViewerSetIsRespected() {
        var viewers = new HashSet<Audience>(List.of(sender, console));
        LocalChatDelivery.deliver(viewers, renderer, sender, Component.text("Ann"), Component.text("secret"), null);
        verify(other, never()).sendMessage(any(Component.class));
        verify(console, times(1)).sendMessage(any(Component.class));
    }
    @Test void brokenOrMissingRendererFallsBackToFormat() {
        ChatRenderer broken = (s, n, m, v) -> { throw new IllegalStateException(); };
        LocalChatDelivery.deliver(Set.of(other), broken, sender, Component.text("Ann"), Component.text("yo"), "<name> says <message>");
        verify(other).sendMessage(argThat((Component c) -> plain(c).equals("Ann says yo")));
        var second = mock(Audience.class);
        LocalChatDelivery.deliver(Set.of(second), null, sender, Component.text("Ann"), Component.text("yo"), null);
        verify(second).sendMessage(argThat((Component c) -> plain(c).equals("Ann: yo")));
    }
    @Test void publicDetectionNeedsSenderAndEveryoneOnline() {
        assertTrue(LocalChatDelivery.isPublic(sender, Set.of(sender, other), List.of(sender)));
        var p2 = mock(Player.class);
        assertFalse(LocalChatDelivery.isPublic(sender, Set.of(sender), List.of(sender, p2)));
        assertFalse(LocalChatDelivery.isPublic(sender, Set.of(other), List.of()));
    }
    @Test void senderNameIsChosenPerViewerSoBedrockGetsAFlatStyle() {
        var java = mock(Player.class); when(java.getUniqueId()).thenReturn(new UUID(5, 5));
        var bedrock = mock(Player.class); when(bedrock.getUniqueId()).thenReturn(new UUID(0, 5));
        var id = new NameStyle.Identity("Aurora", "dev", "rainbow", 7);
        LocalChatDelivery.deliver(List.of(java, bedrock), renderer, sender,
                v -> NameStyle.render(id, LocalChatDelivery.bedrock(v), 0), Component.text("hi"), null);
        var lines = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(java).sendMessage(lines.capture()); verify(bedrock).sendMessage(lines.capture());
        assertEquals("<[DEV] [7] Aurora> hi", plain(lines.getAllValues().get(0)));
        assertEquals(plain(lines.getAllValues().get(0)), plain(lines.getAllValues().get(1)));
        assertNotEquals(lines.getAllValues().get(0), lines.getAllValues().get(1));
    }
}
