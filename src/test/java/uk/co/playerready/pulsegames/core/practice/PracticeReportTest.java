package uk.co.playerready.pulsegames.core.practice;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PracticeReportTest {
    @Test void reportsSamplesRatherThanInventedFailureCountAndOmitsPlayerIdentity() {
        var report=new PracticeReport();
        var bot=new BotDiagnostics.Snapshot(UUID.randomUUID(),"PrivateName","match","Defending",BotDifficulty.EASY,"TargetName","route","STALLED","No progress",400);
        report.sample(List.of(bot));report.sample(List.of(bot));
        String text=String.join("\n",report.lines("match","Ended"));
        assertTrue(text.contains("stalled bot samples=2"));assertTrue(text.contains("Defending"));
        assertFalse(text.contains("PrivateName"));assertFalse(text.contains("TargetName"));
    }
    @Test void diagnosticReasonsAreBoundedAndSingleLine() {
        var report=new PracticeReport();
        for(int i=0;i<100;i++) report.sample(List.of(new BotDiagnostics.Snapshot(UUID.randomUUID(),"bot","match","o"+i,BotDifficulty.HARD,"","","WAITING_FOR_SAFE_STEP","reason"+i+"\n"+"x".repeat(200),0)));
        var lines=report.lines("id","Ended");
        assertEquals(5,lines.size());assertTrue(lines.get(1).contains("blocked bot samples=100"));
        assertFalse(lines.get(3).contains("\n"));assertTrue(lines.get(3).length()<2700);
    }
}
