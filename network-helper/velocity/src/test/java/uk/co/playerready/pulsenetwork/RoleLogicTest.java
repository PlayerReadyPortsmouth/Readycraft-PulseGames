package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import uk.co.playerready.pulsenetwork.RoleLogic.Action;
import uk.co.playerready.pulsenetwork.RoleLogic.Level;

class RoleLogicTest {
    static final Map<String, String> MAP = RoleLogic.parseGroups("");

    @Test void parsesRolesCaseInsensitivelyAndRejectsUnknown() {
        assertEquals(Optional.of("venue:ports"), RoleLogic.parseRole("Venue:Ports"));
        assertEquals(Optional.of("dev"), RoleLogic.parseRole("DEV"));
        for (String bad : new String[]{"owner", "venue", "venue:mars", "", "default", "*"}) assertTrue(RoleLogic.parseRole(bad).isEmpty(), bad);
        assertTrue(RoleLogic.parseRole(null).isEmpty());
    }
    @Test void groupMapDefaultsOverridesAndValidation() {
        assertEquals("venue-plym", MAP.get("venue:plym"));
        var m = RoleLogic.parseGroups("staff=crew, venue:tru = venue-truro");
        assertEquals("crew", m.get("staff")); assertEquals("venue-truro", m.get("venue:tru")); assertEquals("dev", m.get("dev"));
        assertThrows(IllegalArgumentException.class, () -> RoleLogic.parseGroups("owner=owner"));
        assertThrows(IllegalArgumentException.class, () -> RoleLogic.parseGroups("staff"));
        assertThrows(IllegalArgumentException.class, () -> RoleLogic.parseGroups("staff=Bad Name"));
        assertThrows(IllegalArgumentException.class, () -> RoleLogic.parseGroups("staff=admin"));
    }
    @Test void levelComesFromGroupMembershipNotPermissions() {
        assertEquals(Level.DEV, RoleLogic.level(Set.of("admin", "dev"), MAP));
        assertEquals(Level.ADMIN, RoleLogic.level(Set.of("admin", "staff"), MAP));
        assertEquals(Level.STAFF, RoleLogic.level(Set.of("staff"), MAP));
        assertEquals(Level.NONE, RoleLogic.level(Set.of("venue-ports", "default"), MAP));
        assertEquals(Level.STAFF, RoleLogic.level(Set.of("crew"), RoleLogic.parseGroups("staff=crew")));
    }
    @Test void gatingMatrix() {
        for (String role : RoleLogic.ROLES) for (Action a : new Action[]{Action.SET, Action.REMOVE}) {
            boolean venue = RoleLogic.isVenue(role);
            assertTrue(RoleLogic.allowed(Level.CONSOLE, a, role));
            assertTrue(RoleLogic.allowed(Level.DEV, a, role));
            assertEquals(venue, RoleLogic.allowed(Level.ADMIN, a, role), "admin " + role);
            assertEquals(venue, RoleLogic.allowed(Level.STAFF, a, role), "staff " + role);
            assertFalse(RoleLogic.allowed(Level.NONE, a, role), "none " + role);
        }
        assertFalse(RoleLogic.allowed(Level.DEV, Action.SET, null));
        assertTrue(RoleLogic.allowed(Level.STAFF, Action.VIEW, null));
        assertFalse(RoleLogic.allowed(Level.NONE, Action.VIEW, null));
        assertTrue(RoleLogic.allowed(Level.ADMIN, Action.HIDE, null));
        assertFalse(RoleLogic.allowed(Level.STAFF, Action.HIDE, null));
        assertFalse(RoleLogic.allowed(Level.NONE, Action.HIDE, null));
    }
    @Test void confirmationExpiresAfterThirtySecondsAndIsSingleUse() {
        var now = new AtomicLong(1000);
        var c = new RoleLogic.Confirmations<String>(now::get);
        c.put("a", "x"); now.addAndGet(RoleLogic.CONFIRM_MS);
        assertEquals(Optional.of("x"), c.take("a"));
        assertTrue(c.take("a").isEmpty());
        c.put("a", "y"); now.addAndGet(RoleLogic.CONFIRM_MS + 1);
        assertTrue(c.take("a").isEmpty());
        assertTrue(c.take("nobody").isEmpty());
    }
    @Test void hiddenNeedsExplicitOptInNotWildcard() {
        var id = UUID.randomUUID();
        assertFalse(RoleLogic.tabHidden(id, "Admin", u -> null, Set.of()));
        assertFalse(RoleLogic.tabHidden(id, "Admin", u -> "false", Set.of()));
        assertTrue(RoleLogic.tabHidden(id, "Admin", u -> "true", Set.of()));
        assertTrue(RoleLogic.tabHidden(id, "Ghost", u -> null, RoleLogic.names(" ghost , other,")));
        assertEquals(Set.of(), RoleLogic.names(null));
    }
    @Test void tabCompletion() {
        var online = List.of("Amy", "bob");
        assertEquals(List.of("set"), RoleLogic.complete(new String[]{"se"}, online));
        assertEquals(List.of("Amy"), RoleLogic.complete(new String[]{"set", "a"}, online));
        assertEquals(List.of("venue:ports", "venue:plym").stream().sorted().toList(), RoleLogic.complete(new String[]{"set", "Amy", "venue:p"}, online));
        assertTrue(RoleLogic.complete(new String[]{"set", "Amy", "dev", "x"}, online).isEmpty());
    }
}
