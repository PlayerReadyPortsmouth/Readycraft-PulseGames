package uk.co.playerready.pumpkinhunt.logic;

import static org.junit.jupiter.api.Assertions.*;

import uk.co.playerready.pumpkinhunt.logic.SeasonWindow.State;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeasonWindowTest {
    private static final ZoneId Z = ZoneId.of("Europe/London");

    private static long at(String iso) { return LocalDateTime.parse(iso).atZone(Z).toInstant().toEpochMilli(); }

    @Test void disabledAndUnboundedWindows() {
        assertEquals(State.DISABLED, new SeasonWindow(false, "", "", Z).state(at("2026-10-30T12:00")));
        assertEquals(State.OPEN, new SeasonWindow(true, "", "", Z).state(at("2026-10-30T12:00")));
        assertEquals(State.OPEN, new SeasonWindow(true, "garbage", null, Z).state(at("2026-10-30T12:00")));
        assertTrue(SeasonWindow.unreadable("garbage", Z));
    }

    @Test void bareEndDateIncludesWholeDay() {
        SeasonWindow w = new SeasonWindow(true, "2026-10-24", "2026-11-01", Z);
        assertEquals(State.NOT_STARTED, w.state(at("2026-10-23T23:59")));
        assertEquals(State.OPEN, w.state(at("2026-10-24T00:00")));
        assertEquals(State.OPEN, w.state(at("2026-11-01T23:59")));
        assertEquals(State.OVER, w.state(at("2026-11-02T00:00")));
    }

    @Test void dateTimeEndIsExclusive() {
        SeasonWindow w = new SeasonWindow(true, "2026-10-24T09:00", "2026-10-31T18:00", Z);
        assertTrue(w.isOpen(at("2026-10-31T17:59")));
        assertFalse(w.isOpen(at("2026-10-31T18:00")));
    }

    @Test void cooldownBlocksSpamPerLantern() {
        ClickCooldown c = new ClickCooldown();
        UUID u = UUID.randomUUID();
        assertTrue(c.tryAcquire(u, "a", 0, 1000));
        assertFalse(c.tryAcquire(u, "a", 999, 1000));
        assertTrue(c.tryAcquire(u, "b", 999, 1000));
        assertTrue(c.tryAcquire(u, "a", 1000, 1000));
    }
}
