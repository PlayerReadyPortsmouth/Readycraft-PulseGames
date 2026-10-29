package uk.co.playerready.pulsegames.core.lobby;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelMenuSafetyTest {
    @Test void everyLoadoutHasReachablePageAndOutdatedPageClamps() {
        assertEquals(0, DuelMenu.loadoutPage(0, 0));
        assertEquals(1, DuelMenu.loadoutPage(1, 19));
        assertEquals(2, DuelMenu.loadoutPage(99, 37));
        assertEquals(0, DuelMenu.loadoutPage(2, 4));
    }
}
