package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsenetwork.RoleLogic.Level;
import static org.junit.jupiter.api.Assertions.*;

class NickLogicTest {
    // actor, then target NONE, STAFF, ADMIN, DEV for someone else
    @Test void gatingMatrix() {
        record Row(Level actor, boolean none, boolean staff, boolean admin, boolean dev) {}
        for (var r : new Row[]{new Row(Level.CONSOLE, true, true, true, true), new Row(Level.DEV, true, true, true, true),
                new Row(Level.ADMIN, true, true, false, true), new Row(Level.STAFF, true, true, false, false), new Row(Level.NONE, false, false, false, false)}) {
            assertEquals(r.none(), NickLogic.mayChange(r.actor(), false, Level.NONE), r.actor() + " -> none");
            assertEquals(r.staff(), NickLogic.mayChange(r.actor(), false, Level.STAFF), r.actor() + " -> staff");
            assertEquals(r.admin(), NickLogic.mayChange(r.actor(), false, Level.ADMIN), r.actor() + " -> admin");
            assertEquals(r.dev(), NickLogic.mayChange(r.actor(), false, Level.DEV), r.actor() + " -> dev");
        }
    }
    @Test void everyStaffLevelMayChangeTheirOwnButPlayersMayNot() {
        for (Level l : new Level[]{Level.STAFF, Level.ADMIN, Level.DEV}) assertTrue(NickLogic.mayChange(l, true, l), l.name());
        assertFalse(NickLogic.mayChange(Level.NONE, true, Level.NONE));
    }
}
