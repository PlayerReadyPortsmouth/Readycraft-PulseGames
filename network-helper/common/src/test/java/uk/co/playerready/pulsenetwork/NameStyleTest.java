package uk.co.playerready.pulsenetwork;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NameStyleTest {
    static String text(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    static NameStyle.Identity id(String role, String style, Integer level) { return new NameStyle.Identity("Aurora", role, style, level); }
    /** Colours of every visible text leaf, in order. */
    static List<TextColor> colours(Component c) {
        var out = new ArrayList<TextColor>();
        walk(c, null, out);
        return out;
    }
    private static void walk(Component c, TextColor inherited, List<TextColor> out) {
        TextColor own = c.color() != null ? c.color() : inherited;
        if (c instanceof net.kyori.adventure.text.TextComponent t && !t.content().isEmpty()) out.add(own);
        for (var child : c.children()) walk(child, own, out);
    }
    static List<TextColor> nameColours(Component c, int nameLength) { var all = colours(c); return all.subList(all.size() - nameLength, all.size()); }

    @ParameterizedTest @CsvSource({"dev,[DEV] Aurora,AQUA", "admin,[ADMIN] Aurora,RED", "staff,[STAFF] Aurora,GREEN", "venue,[VENUE] Aurora,YELLOW"})
    void everyRoleHasATagAndBedrockGetsOneStaticColour(String role, String expected, String bedrockColour) {
        assertEquals(expected, text(NameStyle.render(id(role, null, null), false, 0)));
        var bedrock = NameStyle.render(id(role, null, null), true, 0);
        assertEquals(expected, text(bedrock));
        assertEquals(NamedTextColor.NAMES.value(bedrockColour.toLowerCase()), colours(bedrock).get(1)); // [ ] are dark grey, tag text second
    }
    @Test void noRoleOrUnknownRoleShowsNoTag() {
        assertEquals("Aurora", text(NameStyle.render(id(null, null, null), false, 0)));
        assertEquals("Aurora", text(NameStyle.render(id("owner", null, null), false, 0)));
    }
    @Test void venueTagIsGenericNeverATown() {
        assertFalse(text(NameStyle.render(id("venue", null, null), false, 0)).toLowerCase().contains("ports"));
        assertEquals(Set.of("dev", "admin", "staff", "venue"), NameStyle.Tags.DEFAULT.roles());
    }
    @Test void everyStyleRendersTheNameUnchangedInText() {
        assertTrue(NameStyle.styleIds().size() >= 16);
        for (String style : NameStyle.styleIds()) assertEquals("Aurora", text(NameStyle.render(id(null, style, null), false, 3)), style);
    }
    @Test void staticStylesDoNotChangeWithTickAndGradientsVaryAcrossTheName() {
        for (String s : List.of("red", "orange", "yellow", "green", "aqua", "blue", "purple", "pink", "sunset", "ocean", "forest", "candy")) {
            assertFalse(NameStyle.isAnimated(s));
            assertEquals(NameStyle.render(id(null, s, null), false, 0), NameStyle.render(id(null, s, null), false, 99), s);
        }
        for (String g : List.of("sunset", "ocean", "forest", "candy")) assertTrue(new HashSet<>(nameColours(NameStyle.render(id(null, g, null), false, 0), 6)).size() > 1, g);
        assertEquals(NamedTextColor.RED, nameColours(NameStyle.render(id(null, "sunset", null), false, 0), 6).getFirst());
    }
    @Test void animatedStylesChangeWithTick() {
        for (String s : List.of("rainbow", "shimmer", "pulse", "sparkle")) {
            assertTrue(NameStyle.isAnimated(s));
            var frames = new HashSet<Component>();
            for (long t = 0; t < 12; t++) frames.add(NameStyle.render(id(null, s, null), false, t));
            assertTrue(frames.size() > 1, s);
            assertEquals(NameStyle.render(id(null, s, null), false, 5), NameStyle.render(id(null, s, null), false, 5), "same tick is stable");
        }
    }
    @Test void bedrockFlattensEveryStyleToOneNamedColourAtAnyTick() {
        for (String s : NameStyle.styleIds()) {
            var a = NameStyle.render(id(null, s, null), true, 0);
            assertEquals(a, NameStyle.render(id(null, s, null), true, 7), s);
            var cols = new HashSet<>(nameColours(a, 1));
            assertEquals(1, cols.size(), s);
            assertInstanceOf(NamedTextColor.class, cols.iterator().next(), s);
        }
    }
    @Test void unknownStyleFallsBackToPlainName() {
        assertEquals(Component.text("Aurora"), ((net.kyori.adventure.text.TextComponent) NameStyle.render(id(null, "<red>evil", null), false, 0)).children().getFirst());
    }
    @Test void levelBadgeIsGreyAndComesBeforeTheName() {
        var c = NameStyle.render(id("dev", "green", 12), false, 0);
        assertEquals("[DEV] [12] Aurora", text(c));
        assertEquals(NamedTextColor.GRAY, colours(c).get(4)); // "[", "DEV", "]", space, then the badge
        assertEquals("[12] Aurora", text(NameStyle.render(id(null, null, 12), false, 0)));
        assertEquals("Aurora", text(NameStyle.render(id(null, null, 0), false, 0)));
    }
    @Test void lengthGuardDropsBadgeFirstThenTagButNeverTruncatesTheName() {
        var longName = new NameStyle.Identity("Sixteen_Chars_Ab", "admin", null, 123);
        // 16 + "[ADMIN] " (8) + "[123] " (6) = 30 fits
        assertEquals("[ADMIN] [123] Sixteen_Chars_Ab", text(NameStyle.render(longName, false, 0)));
        var tags = NameStyle.Tags.DEFAULT;
        assertEquals("[ADMIN] Sixteen_Chars_Ab", text(NameStyle.render(longName, false, 0, tags, 25)));
        assertEquals("Sixteen_Chars_Ab", text(NameStyle.render(longName, false, 0, tags, 20)));
        assertEquals("Sixteen_Chars_Ab", text(NameStyle.render(longName, false, 0, tags, 3)));
        for (int budget = 10; budget < 40; budget++) assertTrue(text(NameStyle.render(longName, false, 0, tags, budget)).length() <= Math.max(budget, 16));
    }
    @Test void configuredTagsOverrideCapAtSixCharactersAndNeverParsePlayerText() {
        var tags = new NameStyle.Tags(Map.of("dev", "<gold><bold>DEVELOPER</bold></gold>", "staff", " "));
        assertEquals("[DEVELO] Aurora", text(NameStyle.render(id("dev", null, null), false, 0, tags, 32)));
        assertEquals("Aurora", text(NameStyle.render(id("staff", null, null), false, 0, tags, 32)), "blank removes a tag");
        var tricky = new NameStyle.Identity("<red>[STAFF]", null, null, null);
        assertEquals("<red>[STAFF]", text(NameStyle.render(tricky, false, 0)));
        assertThrows(IllegalArgumentException.class, () -> new NameStyle.Tags(Map.of("Bad Role", "x")));
    }
}
