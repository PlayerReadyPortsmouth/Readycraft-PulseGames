package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameMenuLayoutTest {
    @Test void footerButtonsNeverShareASlotWithAGame() {
        for (int games = 0; games <= 80; games++) {
            int rows = GameMenu.rowsFor(games);
            int size = rows * 9;
            int shown = GameMenu.capacity(games);
            assertTrue(shown <= size - GameMenu.FOOTER_SLOTS, games + " games would reach the footer");
            if (games <= 49) assertEquals(games, shown, "every game up to six rows' worth is listed");
            else assertEquals(49, shown);
        }
    }

    @Test void todaysCatalogueKeepsItsFourRows() {
        assertEquals(4, GameMenu.rowsFor(24));
        assertEquals(5, GameMenu.rowsFor(32), "the footer needs its own slots once games reach slot 31");
    }

    @Test void modeMenuAlwaysHasRoomForItsModes() {
        assertEquals(3, GameMenu.modeRows(4), "today's games keep the 27-slot menu");
        for (int modes = 1; modes <= 40; modes++) {
            int size = GameMenu.modeRows(modes) * 9;
            int shown = Math.min(modes, GameMenu.MAX_MODES);
            assertTrue(GameMenu.modeSlot(shown - 1) < size, modes + " modes would overflow a " + size + "-slot menu");
        }
        assertEquals(29, GameMenu.modeSlot(12), "the 13th mode used to land on slot 29 of 27");
    }
}
