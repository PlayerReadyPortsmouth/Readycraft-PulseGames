package uk.co.playerready.pulsegames.core.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SidebarDiffTest {
    @Test void duplicateLinesGetDistinctEntries() {
        List<String> out = SidebarDiff.uniqueEntries(List.of("", "a", "", ""));
        assertEquals(4, out.stream().distinct().count());
        assertEquals("a", out.get(1));
    }

    @Test void identicalRenderChangesNothing() {
        var lines = List.of("a", "b", "c");
        assertEquals(0, SidebarDiff.diff(lines, lines).total());
    }

    @Test void onlyTheChangedLineIsRewritten() {
        var d = SidebarDiff.diff(List.of("time 5", "kills 0", "tip"), List.of("time 4", "kills 0", "tip"));
        assertEquals(List.of("time 5"), d.remove());
        assertEquals(Map.of("time 4", 3), d.set());
    }

    @Test void scoresCountDownFromTheBottomSoAddingAtTheTopTouchesOneEntry() {
        var d = SidebarDiff.diff(List.of("a", "b"), List.of("new", "a", "b"));
        assertTrue(d.remove().isEmpty());
        assertEquals(Map.of("new", 3), d.set());
        assertEquals(Map.of("a", 3, "b", 2), SidebarDiff.diff(List.of("a", "b"), List.of("a", "b", "new")).set().entrySet().stream()
                .filter(e -> !e.getKey().equals("new")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Test void firstDrawWritesEverything() {
        assertEquals(2, SidebarDiff.diff(null, List.of("a", "b")).set().size());
    }
}
