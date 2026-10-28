package uk.co.playerready.pulsegames.core.world;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldCloneFailureTest {
    private final Logger log = Logger.getLogger("test");

    @Test void anUncheckedErrorInTheCopyStillFailsTheInstance() {
        AtomicInteger failed = new AtomicInteger();
        WorldService.failSafe("clone", () -> { throw new IllegalStateException("boom"); }, failed::incrementAndGet, log);
        WorldService.failSafe("clone", () -> { throw new OutOfMemoryError("copy"); }, failed::incrementAndGet, log);
        WorldService.failSafe("clone", () -> { throw new java.io.IOException("disk"); }, failed::incrementAndGet, log);
        assertEquals(3, failed.get());
    }

    @Test void aCleanStepDoesNotFail() {
        AtomicInteger failed = new AtomicInteger();
        WorldService.failSafe("clone", () -> { }, failed::incrementAndGet, log);
        assertEquals(0, failed.get());
    }
}
