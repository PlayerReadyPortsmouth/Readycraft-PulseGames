package uk.co.playerready.pulsegames.core.game;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
class MatchDiagnosticsTest {
    @TempDir Path dir;
    @Test void boundsRecordsAndPersistsFailureAndCleanup() throws Exception {
        var d=new MatchDiagnostics(dir,Logger.getAnonymousLogger());
        for(int i=0;i<150;i++)d.record("match_a","duels","solo","court","event","sample="+i);
        d.record("match_a","duels","solo","court","logic-failed","onStart IllegalStateException");
        d.record("match_a","world","cleanup","court","world-deleted","Removed");d.close();
        var lines=Files.readAllLines(dir.resolve("match_a.log"));assertEquals(128,lines.size());assertTrue(lines.getLast().contains("world-deleted"));assertTrue(lines.get(126).contains("logic-failed"));assertEquals(20,d.report("match_a").size());
    }
    @Test void rejectsTraversalAndNewlineInjection()throws Exception {
        var d=new MatchDiagnostics(dir,Logger.getAnonymousLogger());d.record("../escape","g","m","map","event","bad");
        d.record("valid","g","m","map","event","first\nsecond");d.close();
        assertFalse(Files.exists(dir.getParent().resolve("escape.log")));assertEquals(1,Files.readAllLines(dir.resolve("valid.log")).size());
    }
}
