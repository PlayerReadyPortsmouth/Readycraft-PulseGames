package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private static final String KEY = "test-only-not-a-deployment-secret-0123456789";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);
    @Test void authenticMessageAcceptedOnce() {
        Wire sender = new Wire(KEY, NOW), receiver = new Wire(KEY, NOW); UUID id = UUID.randomUUID();
        byte[] packet = sender.encode("CHAT", id, "Hello <click:run_command:/op>literal text");
        var message = receiver.decode(packet).orElseThrow();
        assertEquals(id, message.player()); assertEquals("CHAT", message.kind());
        assertTrue(receiver.decode(packet).isEmpty());
    }
    @Test void forgedWrongKeyAndTruncatedPacketsRejected() {
        byte[] packet = new Wire(KEY, NOW).encode("JOIN", UUID.randomUUID(), "request", "instance");
        Wire receiver = new Wire(KEY, NOW);
        assertTrue(new Wire("different-32-character-test-key-0000000", NOW).decode(packet).isEmpty());
        byte[] changed = packet.clone(); changed[12] ^= 1; assertTrue(receiver.decode(changed).isEmpty());
        assertTrue(receiver.decode(Arrays.copyOf(packet, 16)).isEmpty());
        assertTrue(receiver.decode(new byte[8193]).isEmpty());
        assertTrue(receiver.decode(packet).isPresent()); // Invalid packets don't consume replay entries.
    }
    @Test void staleFutureAndUnknownMessagesRejected() {
        Wire sender = new Wire(KEY, NOW); UUID id = UUID.randomUUID();
        byte[] packet = sender.encode("CHAT", id, "hi");
        assertTrue(new Wire(KEY, Clock.offset(NOW, Duration.ofSeconds(31))).decode(packet).isEmpty());
        assertTrue(new Wire(KEY, Clock.offset(NOW, Duration.ofSeconds(-6))).decode(packet).isEmpty());
        assertTrue(new Wire(KEY, NOW).decode(sender.encode("OP", id, "user")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> sender.encode("CHAT", id, "x".repeat(1025)));
        assertThrows(IllegalArgumentException.class, () -> new Wire(""));
    }
    @Test void queueRefreshRetainsOpaqueTokenAndWithdrawalInvalidatesIt() {
        QueueDirectory directory = new QueueDirectory(NOW);
        var first = directory.update("minigamestest", "abc_duels", "Duels", 1, 4);
        var second = directory.update("minigamestest", "abc_duels", "Duels", 3, 4);
        assertEquals(first.token(), second.token()); assertEquals(3, directory.find(first.token()).orElseThrow().players());
        directory.remove("minigamestest", "abc_duels"); assertTrue(directory.find(first.token()).isEmpty());
        assertNotEquals(first.token(), directory.update("minigamestest", "abc_duels", "Duels", 1, 4).token());
    }
    @Test void expiryAndInvalidCapacitiesAreRejected() {
        class MutableClock extends Clock {
            Instant instant = NOW.instant(); public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return this;} public Instant instant(){return instant;}
        }
        MutableClock clock = new MutableClock(); QueueDirectory directory = new QueueDirectory(clock);
        var q = directory.update("games", "one", "Duels", 1, 4); clock.instant = clock.instant.plusSeconds(20);
        assertTrue(directory.find(q.token()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> directory.update("games", "one", "Duels", 0, 4));
        assertThrows(IllegalArgumentException.class, () -> directory.update("games", "one", "Duels", 5, 4));
        assertThrows(IllegalArgumentException.class, () -> directory.update("games", "../../", "Duels", 1, 4));
    }
}
