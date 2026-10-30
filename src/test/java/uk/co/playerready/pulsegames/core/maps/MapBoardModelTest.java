package uk.co.playerready.pulsegames.core.maps;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.core.maps.MapReviewStore.Check;
import static uk.co.playerready.pulsegames.core.maps.MapReviewStore.Outcome;

class MapBoardModelTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private static MapReviewStore.Entry entry(String map, Outcome outcome, int checks) {
        Map<Check, MapReviewStore.Mark> marks = new EnumMap<>(Check.class);
        int i = 0;
        for (Check c : Check.values()) if (i++ < checks)
            marks.put(c, new MapReviewStore.Mark(outcome, "r1", "fp", "a", "2026-10-29T09:00:00Z", "ok"));
        return new MapReviewStore.Entry("g", map, "r1", marks);
    }

    private static MapBoardModel.Row row(String map, Outcome outcome, int checks) {
        return MapBoardModel.row("g", map, entry(map, outcome, checks), "fp", NOW);
    }

    @Test void ageFormats() {
        assertEquals("just now", MapBoardModel.age("2026-10-29T11:59:30Z", NOW));
        assertEquals("5 min ago", MapBoardModel.age("2026-10-29T11:55:00Z", NOW));
        assertEquals("3 h ago", MapBoardModel.age("2026-10-29T09:00:00Z", NOW));
        assertEquals("12 d ago", MapBoardModel.age("2026-10-17T12:00:00Z", NOW));
        assertEquals("unknown", MapBoardModel.age("garbage", NOW));
        assertEquals("unknown", MapBoardModel.age("2027-01-01T00:00:00Z", NOW));
    }

    @Test void staleWhenFingerprintChanges() {
        assertEquals(MapBoardModel.Status.STALE, MapBoardModel.status(entry("a", Outcome.PASS, 1).marks().get(Check.BUILT), "r1", "other"));
        assertEquals(MapBoardModel.Status.MISSING, MapBoardModel.status(null, "r1", "fp"));
    }

    @Test void summaryCountsFullyCheckedOnly() {
        var rows = List.of(row("a", Outcome.PASS, 5), row("b", Outcome.PASS, 5), row("c", Outcome.PASS, 4),
                row("d", Outcome.FAIL, 5), row("e", Outcome.PASS, 5));
        assertEquals("3 of 5 maps fully checked", MapBoardModel.summary(rows));
        assertEquals("No maps registered", MapBoardModel.summary(List.of()));
        assertEquals("1 of 1 map fully checked", MapBoardModel.summary(List.of(row("a", Outcome.PASS, 5))));
    }

    @Test void missingEvidenceFilterFlag() {
        assertTrue(row("a", Outcome.PASS, 4).missingEvidence());
        assertFalse(row("a", Outcome.PASS, 5).missingEvidence());
        assertFalse(row("a", Outcome.FAIL, 5).missingEvidence());
    }

    @Test void orderingPutsGapsFirstThenId() {
        var ordered = MapBoardModel.order(List.of(row("a", Outcome.PASS, 5), row("c", Outcome.PASS, 4), row("b", Outcome.PASS, 1), row("d", Outcome.PASS, 4)));
        assertEquals(List.of("b", "c", "d", "a"), ordered.stream().map(MapBoardModel.Row::map).toList());
    }

    @Test void markCommandIsExact() {
        assertEquals("/admin games maps mark g m java pass <evidence note>", MapBoardModel.markCommand("g", "m", Check.JAVA));
    }
}
