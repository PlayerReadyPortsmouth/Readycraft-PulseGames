package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerStatusTest {
    private static final String KEY = "test-only-not-a-deployment-secret-0123456789";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    @Test void statusRoundTripsThroughSignedWire() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), carrier = UUID.randomUUID();
        var status = new ServerStatus(19.987, 12.34, 7, Map.of(a, ServerStatus.AFK, b, ServerStatus.HIDDEN | ServerStatus.MATCH));
        var message = new Wire(KEY, NOW).decode(new Wire(KEY, NOW).encode("STATUS", carrier, status.toFields())).orElseThrow();
        assertEquals("STATUS", message.kind());
        var parsed = ServerStatus.parse(message.fields()).orElseThrow();
        assertEquals(19.99, parsed.tps(), 0.001); assertEquals(12.3, parsed.mspt(), 0.001); assertEquals(7, parsed.online());
        assertEquals(status.flags(), parsed.flags());
    }
    @Test void staleStatusIsRejectedByWire() {
        byte[] packet = new Wire(KEY, NOW).encode("STATUS", UUID.randomUUID(), new ServerStatus(20, 5, 1, Map.of()).toFields());
        assertTrue(new Wire(KEY, Clock.offset(NOW, Duration.ofSeconds(31))).decode(packet).isEmpty());
    }
    @Test void oversizedReportsAreTruncatedWithHiddenFirstAndStillFitThePacket() {
        var flags = new HashMap<UUID, Integer>();
        for (int i = 0; i < 400; i++) flags.put(UUID.randomUUID(), ServerStatus.AFK);
        UUID hidden = UUID.randomUUID(); flags.put(hidden, ServerStatus.HIDDEN);
        String[] fields = new ServerStatus(20, 1, 401, flags).toFields();
        assertTrue(fields.length <= 3 + ServerStatus.MAX_CHUNKS);
        byte[] packet = new Wire(KEY, NOW).encode("STATUS", UUID.randomUUID(), fields); // would throw if over 8 KiB
        assertTrue(packet.length <= 8192);
        var parsed = ServerStatus.parse(new Wire(KEY, NOW).decode(packet).orElseThrow().fields()).orElseThrow();
        assertEquals(ServerStatus.MAX_ENTRIES, parsed.flags().size()); assertEquals(ServerStatus.HIDDEN, parsed.flags().get(hidden));
    }
    @Test void malformedReportsAreRejected() {
        String id = UUID.randomUUID().toString().replace("-", "");
        assertTrue(ServerStatus.parse(List.of("20", "5")).isEmpty());
        assertTrue(ServerStatus.parse(List.of("NaN", "5", "1")).isEmpty());
        assertTrue(ServerStatus.parse(List.of("20", "5", "-1")).isEmpty());
        assertTrue(ServerStatus.parse(List.of("99", "5", "1")).isEmpty());
        assertTrue(ServerStatus.parse(List.of("20", "5", "1", id + "9")).isEmpty());
        assertTrue(ServerStatus.parse(List.of("20", "5", "1", id + "1," + id + "2")).isEmpty()); // duplicate player
        assertTrue(ServerStatus.parse(List.of("20", "5", "1", "x".repeat(33))).isEmpty());
        assertTrue(ServerStatus.parse(List.of("20", "5", "1", id + "1")).isPresent());
        assertThrows(IllegalArgumentException.class, () -> new Wire(KEY, NOW).encode("STATUS", UUID.randomUUID(), "x".repeat(1025)));
    }
}
