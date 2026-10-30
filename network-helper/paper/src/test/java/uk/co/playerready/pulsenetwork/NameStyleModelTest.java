package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NameStyleModelTest {
    @Test void unlocksComeFromPermissionsAndWildcardHoldersHaveEverything() {
        Set<String> held = Set.of("pulse.namestyle.red", "pulse.namestyle.rainbow");
        var some = NameStyleModel.entries(held::contains, null);
        assertEquals(NameStyle.styleIds().size(), some.size());
        assertEquals(List.of("red", "rainbow"), some.stream().filter(NameStyleModel.Entry::unlocked).map(NameStyleModel.Entry::id).toList());
        assertTrue(NameStyleModel.entries(p -> true, null).stream().allMatch(NameStyleModel.Entry::unlocked));
        assertTrue(NameStyleModel.entries(p -> false, null).stream().noneMatch(NameStyleModel.Entry::unlocked));
    }
    @Test void unlockedFirstThenLockedAndSlotsFollowThatOrder() {
        var entries = NameStyleModel.entries(Set.of("pulse.namestyle.pink", "pulse.namestyle.red")::contains, "pink");
        assertEquals("red", entries.get(0).id());
        assertEquals("pink", entries.get(1).id());
        assertFalse(entries.get(2).unlocked());
        var slots = NameStyleModel.slots(entries);
        assertEquals("red", slots.get(0)); assertEquals("pink", slots.get(1));
        assertEquals(NameStyleModel.CLEAR, slots.get(NameStyleModel.CLEAR_SLOT));
        assertEquals(NameStyle.styleIds().size() + 1, slots.size());
    }
    @Test void onlyAnUnlockedCurrentStyleIsHighlighted() {
        var e = NameStyleModel.entries(Set.of("pulse.namestyle.red")::contains, "red");
        assertTrue(e.get(0).current());
        assertTrue(NameStyleModel.entries(p -> false, "red").stream().noneMatch(NameStyleModel.Entry::current));
        assertNull(NameStyleModel.known("nonsense")); assertEquals("red", NameStyleModel.known(" Red "));
    }
    @Test void bedrockFormListsUnlockedAsButtonsAndLockedBelow() {
        var e = NameStyleModel.entries(Set.of("pulse.namestyle.sunset", "pulse.namestyle.red")::contains, "sunset");
        assertEquals(List.of("Red (red)", "Sunset (warm) - selected"), NameStyleModel.formButtons(e).stream().map(NameStyleModel::button).toList());
        String text = NameStyleModel.formContent(e);
        assertTrue(text.contains("Locked (unlock by levelling up):") && text.contains("Rainbow") && !text.contains("Locked (unlock by levelling up):\nRed"));
        assertTrue(NameStyleModel.formContent(NameStyleModel.entries(p -> true, null)).contains("every style"));
    }
    @Test void everyStyleHasAMaterialAndAHint() {
        for (String id : NameStyle.styleIds()) { assertNotNull(NameStyleModel.material(id)); assertFalse(NameStyleModel.hint(id).contains(" ")); }
    }
    @Test void changesAreLimitedToOneEveryFiveSeconds() {
        var last = new HashMap<UUID, Long>(); UUID id = UUID.randomUUID();
        assertTrue(NameStyleModel.allow(last, id, 1000)); assertFalse(NameStyleModel.allow(last, id, 5999)); assertTrue(NameStyleModel.allow(last, id, 6000));
    }
    @Test void previewIsOpenToDevAndAdminForAnyStyleAndLockedForEveryoneElse() {
        java.util.function.Predicate<String> held = Set.of("pulse.namestyle.red")::contains;
        assertEquals(NameStyleModel.Preview.OK, NameStyleModel.preview("rainbow", true, p -> false));
        assertEquals(NameStyleModel.Preview.OK, NameStyleModel.preview("red", false, held));
        assertEquals(NameStyleModel.Preview.LOCKED, NameStyleModel.preview("rainbow", false, held));
        assertEquals(NameStyleModel.Preview.UNKNOWN, NameStyleModel.preview("nope", true, p -> true));
        assertEquals(Set.of("dev", "admin"), NameStyleModel.PREVIEW_GROUPS);
    }
}
