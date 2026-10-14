package uk.co.playerready.pulsegames.core.setup;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ArenaNavigationMenuTest {
    @Test void routeNamesCannotEscapeTheDedicatedSettingsNamespace() {
        assertEquals("bot-route-upper_deck-2", ArenaSetupMenu.navigationRouteKey(" Upper_Deck-2 "));
        for (String invalid : List.of("", "../spawn", "route.name", "two words", "x".repeat(33)))
            assertThrows(IllegalArgumentException.class, () -> ArenaSetupMenu.navigationRouteKey(invalid));
    }
    @Test void staleRemovalCannotDeleteADifferentWaypoint() {
        Map<String, Object> settings = new HashMap<>();
        String key = "bot-route-garden";
        List<String> original = List.of("1,80,1", "2,80,2");
        settings.put(key, original);
        List<String> snapshot = ArenaSetupMenu.navigationPoints(settings, key);
        settings.put(key, List.of("9,80,9", "1,80,1", "2,80,2"));
        assertFalse(ArenaSetupMenu.removeNavigationPoint(settings, key, snapshot, 1));
        assertEquals(List.of("9,80,9", "1,80,1", "2,80,2"), settings.get(key));
    }
    @Test void removalPreservesRouteOrderAndAcceptsImmutableLoadedLists() {
        Map<String, Object> settings = new HashMap<>();
        String key = "bot-route-garden";
        List<String> original = List.of("1,80,1", "2,80,2", "3,80,3");
        settings.put(key, original);
        assertTrue(ArenaSetupMenu.removeNavigationPoint(settings, key, original, 1));
        assertEquals(List.of("1,80,1", "3,80,3"), settings.get(key));
        assertFalse(ArenaSetupMenu.removeNavigationPoint(settings, key, List.of("1,80,1", "3,80,3"), -1));
        assertFalse(ArenaSetupMenu.removeNavigationPoint(settings, key, List.of("1,80,1", "3,80,3"), 2));
    }
}
