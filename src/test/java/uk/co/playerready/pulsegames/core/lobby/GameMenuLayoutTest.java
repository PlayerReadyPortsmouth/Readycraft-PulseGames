package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GameMenuLayoutTest {
    @Test void gamesStayInsideTheContentGridAndNeverTouchNavOrBorder() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < GameMenu.PER_PAGE; i++) {
            int slot = GameMenu.contentSlot(i);
            assertTrue(slot >= 10 && slot <= 43 && slot % 9 >= 1 && slot % 9 <= 7, "slot " + slot);
            assertTrue(seen.add(slot));
        }
        assertEquals(10, GameMenu.contentSlot(0));
        assertEquals(43, GameMenu.contentSlot(27));
    }

    @Test void footerButtonsAvoidTheFixedNavSlots() {
        Set<Integer> nav = Set.of(45 + 3, 49, 50, 53);
        for (int slot : new int[]{GameMenu.PARTY_SLOT, GameMenu.CHALLENGES_SLOT, GameMenu.TOP_SLOT, GameMenu.KIT_SLOT, GameMenu.QUICK_PLAY_SLOT})
            assertFalse(nav.contains(slot), "slot " + slot);
        assertEquals(4, GameMenu.QUICK_PLAY_SLOT, "Quick Play sits top centre");
    }

    @Test void modePickerSizesAndSlotsFit() {
        assertEquals(3, GameMenu.modeRows(4));
        assertEquals(6, GameMenu.modeRows(8));
        for (int modes = 1; modes <= GameMenu.MAX_MODES; modes++) {
            int rows = GameMenu.modeRows(modes);
            for (int i = 0; i < modes; i++) {
                int slot = GameMenu.modeSlot(i, rows);
                assertTrue(slot < rows * 9 - 9, modes + " modes reach the nav row");
            }
        }
    }

    @Test void unreleasedGamesAreHiddenFromEveryoneButTestersAndAdmins() {
        assertTrue(GameMenu.listed(true, false));
        assertFalse(GameMenu.listed(false, false));
        assertTrue(GameMenu.listed(false, true));
    }

    @Test void stackSizeCueIsWaitingCountBetweenOneAndSixtyFour() {
        assertEquals(1, GameMenu.cue(0));
        assertEquals(3, GameMenu.cue(3));
        assertEquals(64, GameMenu.cue(500));
    }

    @Test void quietGamesSayTheyAreQuiet() {
        assertTrue(GameMenu.activity(new QueueCounts.Count(0, 0)).contains("Quiet"));
        assertTrue(GameMenu.activity(new QueueCounts.Count(2, 1)).contains("2 waiting"));
    }
}
