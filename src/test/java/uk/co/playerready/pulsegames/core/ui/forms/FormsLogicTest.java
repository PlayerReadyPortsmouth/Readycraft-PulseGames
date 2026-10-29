package uk.co.playerready.pulsegames.core.ui.forms;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.lobby.QueueCounts.Count;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.core.ui.forms.FormLayout.*;

class FormsLogicTest {
    @Test void quickPlayIsFirstThenGamesInOrderThenHelpers() {
        var b = gameList(List.of(new GameRow("spleef", "Spleef", 3, 5, true), new GameRow("x", "Maze", 0, 0, false)));
        assertEquals(QUICK_PLAY, b.get(0).target());
        assertEquals(List.of(QUICK_PLAY, "game:spleef", "soon:x", PARTY, CHALLENGES, TOP, KIT),
                b.stream().map(Button::target).toList());
        assertEquals("Spleef\n3 waiting, 5 playing", b.get(1).label());
        assertEquals("Maze\nComing soon!", b.get(2).label());
    }

    @Test void activityWording() {
        assertEquals("Quiet right now", activity(0, 0));
        assertEquals("2 playing", activity(0, 2));
    }

    @Test void modesThenBackLastAndTargetsResolve() {
        var b = modeList(List.of(new ModeRow("solo", "Solo", 1, 0), new ModeRow("duo", "Duo", 0, 0)));
        assertEquals(List.of("mode:solo", "mode:duo", BACK), b.stream().map(Button::target).toList());
        assertEquals("mode:duo", targetAt(b, 1));
        assertNull(targetAt(b, 3));
        assertNull(targetAt(b, -1));
    }

    @Test void routingNeedsSwitchFloodgateAndBedrock() {
        assertTrue(useForm(true, true, true));
        assertFalse(useForm(false, true, true));
        assertFalse(useForm(true, false, true));
        assertFalse(useForm(true, true, false));
    }

    @Test void refreshOnlyWhenAShownCountChanges() {
        var before = RefreshDiff.snapshot(Map.of("a", new Count(1, 0), "b/x", new Count(2, 0)), k -> !k.contains("/"));
        assertFalse(RefreshDiff.changed(before, RefreshDiff.snapshot(Map.of("a", new Count(1, 0), "b/x", new Count(9, 0)), k -> !k.contains("/"))));
        assertTrue(RefreshDiff.changed(before, RefreshDiff.snapshot(Map.of("a", new Count(2, 0)), k -> true)));
        // gone quiet counts as changed; an all-zero entry equals a missing one
        assertTrue(RefreshDiff.changed(before, RefreshDiff.snapshot(Map.of(), k -> true)));
        assertFalse(RefreshDiff.changed(Map.of(), RefreshDiff.snapshot(Map.of("a", new Count(0, 0)), k -> true)));
    }
}
