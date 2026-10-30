package uk.co.playerready.pulsenetwork;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IdentityReportTest {
    final NameStyle.Tags tags = NameStyle.Tags.DEFAULT;
    IdentityReport.Facts facts(String role, String style, int held, Integer level, boolean hidden, boolean bedrock) {
        return new IdentityReport.Facts("Ann", Set.of("staff", "dev"), role, style, held, level, hidden, bedrock, "lobby");
    }

    @Test void reportListsEveryFactAndBothPreviews() {
        var lines = IdentityReport.lines(facts("dev", "red", 3, 12, true, false), tags);
        assertEquals("Identity of Ann on lobby:", lines.get(0));
        assertEquals("Groups: dev, staff", lines.get(1));
        assertEquals("Role (pulse.role): dev; tag: [DEV]", lines.get(2));
        assertEquals("Style (pulse.namestyle): red; unlocked styles held: 3 of " + NameStyle.styleIds().size(), lines.get(3));
        assertEquals("Level: 12; hidden from tab: yes; Bedrock: no", lines.get(4));
        assertEquals("Java viewer sees: {dark_gray:[}{aqua:DEV}{dark_gray:]} {gray:[12]} {red:Ann}", lines.get(5));
        assertEquals("Bedrock viewer sees: {dark_gray:[}{aqua:DEV}{dark_gray:]} {gray:[12]} {red:Ann}", lines.get(6));
    }
    @Test void javaAndBedrockPreviewsDifferForGradientsAndBadMetaIsFlagged() {
        var lines = IdentityReport.lines(new IdentityReport.Facts("Annabelle", Set.of(), "owner", "sunset", 16, null, false, true, "lobby"), tags);
        assertTrue(lines.get(2).endsWith("none (unknown role, ignored)"));
        assertTrue(lines.get(3).endsWith("16 of 16 (a wildcard counts)"));
        assertTrue(lines.get(4).startsWith("Level: unknown") && lines.get(4).endsWith("Bedrock: yes"));
        assertTrue(lines.get(5).startsWith("Java viewer sees: {") && lines.get(5).contains("#"), "gradient uses hex colours");
        assertEquals("Bedrock viewer sees: {gold:Annabelle}", lines.get(6));
        assertTrue(IdentityReport.lines(facts(null, "wobble", 0, null, false, false), tags).get(3).contains("wobble (unknown style, ignored)"));
    }
    @Test void onlyDevAdminAndConsoleMayLookAtOthersOrRefresh() {
        for (var level : RoleLogic.Level.values()) {
            boolean elevated = level == RoleLogic.Level.DEV || level == RoleLogic.Level.ADMIN || level == RoleLogic.Level.CONSOLE;
            assertTrue(IdentityReport.mayView(level, true), "everyone sees themselves");
            assertEquals(elevated, IdentityReport.mayView(level, false));
            assertEquals(elevated, IdentityReport.mayRefresh(level));
        }
    }
    @Test void heldStylesAreCountedFromPermissions() {
        assertEquals(0, IdentityReport.stylesHeld(p -> false));
        assertEquals(2, IdentityReport.stylesHeld(Set.of("pulse.namestyle.red", "pulse.namestyle.pink", "pulse.other")::contains));
        assertEquals(NameStyle.styleIds().size(), IdentityReport.stylesHeld(p -> true));
    }
}
