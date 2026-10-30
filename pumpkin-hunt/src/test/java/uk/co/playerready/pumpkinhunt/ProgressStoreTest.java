package uk.co.playerready.pumpkinhunt;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.playerready.pumpkinhunt.logic.PumpkinProgress;

class ProgressStoreTest {
    @Test void debouncedSaveIsWrittenOnFlushAndReloads(@TempDir Path dir) throws Exception {
        PumpkinProgress p = new PumpkinProgress();
        UUID a = UUID.randomUUID();
        p.record(a, "A", "house_1", java.util.Set.of("house_1", "house_2"), 1);
        ProgressStore s = new ProgressStore(dir.resolve("progress.json"), Logger.getAnonymousLogger(), 60_000L, p::toJson);
        assertEquals("", s.read());
        s.save();
        s.save();
        s.flush();
        PumpkinProgress q = new PumpkinProgress();
        q.loadJson(new ProgressStore(dir.resolve("progress.json"), Logger.getAnonymousLogger(), 1L, q::toJson).read());
        assertEquals(java.util.Set.of("house_1"), q.found(a));
    }
}
