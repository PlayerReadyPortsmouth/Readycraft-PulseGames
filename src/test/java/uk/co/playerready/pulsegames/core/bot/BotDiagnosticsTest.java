package uk.co.playerready.pulsegames.core.bot;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BotDiagnosticsTest {
    private BotDiagnostics.Snapshot bot(UUID id, String name) {
        return new BotDiagnostics.Snapshot(id, name, "duels:a1", "Fight visible opponent", BotDifficulty.HARD,
                "Opponent", "Arena spawn 2", "Navigating", "No issue reported", 40);
    }
    @Test void distinguishesNavigationInvestigationFromCombatSkillAndIntentionalStillness() {
        var stalled=new BotDiagnostics.Snapshot(UUID.randomUUID(),"bot","match","Capture",BotDifficulty.EASY,
                "","","STALLED","No progress",220);
        assertTrue(BotDiagnostics.assessment(stalled).contains("not evidence of low combat skill"));
        var waiting=new BotDiagnostics.Snapshot(UUID.randomUUID(),"bot","match","Bridge",BotDifficulty.HARD,
                "","","WAITING_FOR_SAFE_STEP","No blocks",0);
        assertTrue(BotDiagnostics.assessment(waiting).contains("bridge materials"));
        assertTrue(BotDiagnostics.assessment(bot(UUID.randomUUID(),"bot")).contains("intentional defence"));
    }
    @Test void supportsExactCaseInsensitiveNameAndUuid() {
        UUID id = UUID.randomUUID();
        var snapshots = List.of(bot(id, "Ready7"));
        var lines = BotDiagnostics.report(snapshots, "ready7");
        assertEquals(lines, BotDiagnostics.report(snapshots, id.toString()));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Difficulty: hard")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("2.0 seconds")));
        assertTrue(BotDiagnostics.report(snapshots, "Ready").getFirst().contains("No active bot matches"));
    }
    @Test void boundsSummaryAndReturnsImmutableOutput() {
        var snapshots = new ArrayList<BotDiagnostics.Snapshot>();
        for (int i = 0; i < 100; i++) snapshots.add(bot(UUID.randomUUID(), "Ready" + i));
        var lines = BotDiagnostics.report(snapshots, null);
        assertEquals(10, lines.size());
        assertTrue(lines.getLast().contains("first 8"));
        assertThrows(UnsupportedOperationException.class, () -> lines.add("extra"));
    }
    @Test void sanitizesNullsControlCharactersAndOversizeFields() {
        var snapshot = new BotDiagnostics.Snapshot(null, "Bot\nInjected", "x".repeat(500), null, null,
                null, null, null, null, -99);
        assertFalse(snapshot.name().contains("\n"));
        assertEquals(160, snapshot.gameId().length());
        assertEquals(BotDifficulty.NORMAL, snapshot.difficulty());
        assertEquals(0, snapshot.lastProgressAgeTicks());
        assertEquals("No route", snapshot.route());
        assertEquals(7, BotDiagnostics.report(List.of(snapshot), snapshot.name()).size());
    }
    @Test void handlesEmptyMissingAndAmbiguousSelections() {
        assertEquals(1, BotDiagnostics.report(null, null).size());
        var snapshots = List.of(bot(UUID.randomUUID(), "Duplicate"), bot(UUID.randomUUID(), "Duplicate"));
        assertTrue(BotDiagnostics.report(snapshots, "Duplicate").getFirst().contains("More than one"));
        assertTrue(BotDiagnostics.report(snapshots, "unknown").getFirst().contains("No active bot matches"));
    }
}
