package uk.co.playerready.pulsegames.core.progression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProgressionOutboxTest {
    @TempDir Path directory;
    ProgressionOutbox.Result result(UUID match){return new ProgressionOutbox.Result(UUID.fromString("11111111-1111-1111-1111-111111111111"),match,"duels",true,2,"autumn");}
    @Test void retrySurvivesRestartAndOnlyAcknowledgementRemovesIt()throws Exception {
        var box=new ProgressionOutbox(directory);var row=result(UUID.randomUUID());box.add(row);box.add(row);
        assertEquals(List.of(row),new ProgressionOutbox(directory).pending());
        box.acknowledge(row);assertTrue(new ProgressionOutbox(directory).pending().isEmpty());box.acknowledge(row);
    }
    @Test void conflictDoesNotOverwriteDurableResult()throws Exception {
        var box=new ProgressionOutbox(directory);var row=result(UUID.randomUUID());box.add(row);
        assertThrows(java.io.IOException.class,()->box.add(new ProgressionOutbox.Result(row.player(),row.match(),row.game(),false,2,"autumn")));
        assertEquals(List.of(row),new ProgressionOutbox(directory).pending());
    }
    @Test void corruptReceiptStopsCollectionWithoutDeletingEvidence()throws Exception {
        Files.writeString(directory.resolve("broken.yml"),"[invalid:");
        assertThrows(java.io.IOException.class,()->new ProgressionOutbox(directory));assertTrue(Files.exists(directory.resolve("broken.yml")));
    }
    @Test void onlyCompetitiveParticipantsCount() {
        assertTrue(ProgressionService.eligible(true,true,false,"last-team"));
        assertFalse(ProgressionService.eligible(false,true,false,"last-team"));
        assertFalse(ProgressionService.eligible(true,false,false,"last-team"));
        assertFalse(ProgressionService.eligible(true,true,true,"last-team"));
        for(String reason:List.of("forced","error","shutdown","aborted"))assertFalse(ProgressionService.eligible(true,true,false,reason));
    }
}
