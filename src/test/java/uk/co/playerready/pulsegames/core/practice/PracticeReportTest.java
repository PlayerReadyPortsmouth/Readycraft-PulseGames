package uk.co.playerready.pulsegames.core.practice;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PracticeReportTest {
    private BotDiagnostics.Snapshot snapshot(UUID id,String state) {
        return new BotDiagnostics.Snapshot(id,"PrivateName","match","Capture centre",BotDifficulty.NORMAL,"PrivateTarget","route",state,"No progress",400);
    }
    @Test void repeatedIssuesRequireSameBotAndConsecutiveSamples() {
        var report=new PracticeReport();UUID id=UUID.randomUUID();
        report.sample(List.of(snapshot(id,"STALLED")));report.sample(List.of(snapshot(id,"MOVING")));
        report.sample(List.of(snapshot(id,"STALLED")));report.sample(List.of(snapshot(id,"STALLED")));
        assertTrue(report.findings().stream().noneMatch(line->line.contains("Repeated navigation")));
        report.sample(List.of(snapshot(id,"STALLED")));
        assertTrue(report.findings().stream().anyMatch(line->line.contains("3 consecutive")));
        assertTrue(report.findings().stream().anyMatch(line->line.contains("do not prove completion")));
    }
    @Test void unrelatedOrAbsentBotsDoNotAccumulateAContinuousStall() {
        var report=new PracticeReport();UUID id=UUID.randomUUID();
        report.sample(List.of(snapshot(id,"STALLED")));report.sample(List.of());
        report.sample(List.of(snapshot(id,"STALLED")));report.sample(List.of(snapshot(UUID.randomUUID(),"STALLED")));
        assertTrue(report.findings().stream().noneMatch(line->line.contains("Repeated navigation")));
    }
    @Test void earlyEliminationIsAnInvestigationHintRatherThanAnAutomaticSkillJudgment() {
        var report=new PracticeReport();report.testerEliminated(20);report.testerEliminated(40);
        String text=String.join("\n",report.lines("match","ended"));
        assertTrue(text.contains("eliminated at 20s"));assertTrue(text.contains("before concluding"));
        assertTrue(text.contains("Practice does not train adaptive difficulty"));
        assertFalse(text.contains("eliminated at 40s"));
        var late=new PracticeReport();late.testerEliminated(100);
        assertTrue(late.findings().stream().noneMatch(line->line.contains("Tester eliminated")));
    }
    @Test void noSamplesNeverClaimsDifficultyWasMeasured() {
        var report=new PracticeReport();
        assertTrue(report.findings().stream().anyMatch(line->line.contains("no difficulty or path conclusion")));
    }
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
        assertTrue(lines.size()>=5);assertTrue(lines.get(1).contains("blocked bot samples=100"));
        assertFalse(lines.get(3).contains("\n"));assertTrue(lines.get(3).length()<2700);
    }
}
