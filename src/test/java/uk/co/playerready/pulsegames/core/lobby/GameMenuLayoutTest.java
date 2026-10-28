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
}
