package uk.co.playerready.pulsegames.core.maps;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.core.maps.MapReviewStore.*;

class MapReviewStoreTest {
    @TempDir Path directory;
    @Test void evidenceRoundTripsAndBuildOrConfigurationChangesMakeItStale() throws Exception {
        Path file=directory.resolve("reviews.yml");var store=new MapReviewStore(file);
        store.mark("prophunt","laboratory",Check.BEDROCK,Outcome.PASS,"config-a","AurorasChaos","Played\nwith two\tplayers");
        var loaded=new MapReviewStore(file);var mark=loaded.get("prophunt","laboratory").marks().get(Check.BEDROCK);
        assertEquals(Outcome.PASS,mark.outcome());assertEquals("AurorasChaos",mark.actor());
        assertEquals("Played with two players",mark.note());assertFalse(mark.at().isBlank());
        assertTrue(mark.current("initial","config-a"));assertFalse(mark.current("initial","config-b"));
        loaded.revision("prophunt","laboratory","build-two");
        var updated=new MapReviewStore(file).get("prophunt","laboratory");
        assertEquals(mark,updated.marks().get(Check.BEDROCK));assertFalse(mark.current(updated.revision(),"config-a"));
        assertThrows(UnsupportedOperationException.class,()->updated.marks().clear());
    }
    @Test void registrationNeverInfersApprovalAndIsIdempotent() throws Exception {
        var store=new MapReviewStore(directory.resolve("reviews.yml"));store.register("duels","courtyard");
        assertTrue(store.get("duels","courtyard").marks().isEmpty());
        store.mark("duels","courtyard",Check.JAVA,Outcome.FAIL,"config","tester","Spawn obstructed");
        store.register("duels","courtyard");assertEquals(1,store.all().size());
        assertEquals(Outcome.FAIL,store.get("duels","courtyard").marks().get(Check.JAVA).outcome());
    }
    @Test void invalidIdsAndNotesCannotCreateLedgerPaths() throws Exception {
        Path file=directory.resolve("reviews.yml");var store=new MapReviewStore(file);
        for(String id:List.of("../escape","a.b","UPPER","a/b","", "x".repeat(65))) {
            assertFalse(safe(id));assertThrows(IllegalArgumentException.class,()->store.register("duels",id));
            assertThrows(IllegalArgumentException.class,()->store.register(id,"arena"));
        }
        assertFalse(safe(null));assertTrue(safe("arena_1-west"));
        assertThrows(IllegalArgumentException.class,()->store.revision("duels","arena","../bad"));
        for(String note:List.of("","   ","x".repeat(401)))
            assertThrows(IllegalArgumentException.class,()->store.mark("duels","arena",Check.BOTS,Outcome.PASS,"a","tester",note));
        assertFalse(Files.exists(file));assertTrue(store.all().isEmpty());
    }
    @Test void corruptLedgerIsRejectedWithoutOverwritingEvidence() throws Exception {
        Path file=directory.resolve("reviews.yml");
        for(String corrupt:List.of("maps: [unterminated", "maps:\n  duels:\n    arena:\n      checks:\n        JAVA:\n          outcome: UNKNOWN\n")) {
            Files.writeString(file,corrupt);assertThrows(IOException.class,()->new MapReviewStore(file));
            assertEquals(corrupt,Files.readString(file));
        }
    }
    @Test void oversizedLedgerIsRejectedBeforeParsing() throws Exception {
        Path file=directory.resolve("reviews.yml");Files.writeString(file,"x".repeat(4_000_001));
        assertThrows(IOException.class,()->new MapReviewStore(file));
    }
    @Test void failedWriteRollsBackInMemoryChange() throws Exception {
        Path file=directory.resolve("reviews.yml");var store=new MapReviewStore(file);store.register("duels","arena");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"occupied");
        assertThrows(IOException.class,()->store.revision("duels","arena","new-build"));
        assertEquals("initial",store.get("duels","arena").revision());
        assertEquals("occupied",Files.readString(file.resolve("keep")));
    }
}
