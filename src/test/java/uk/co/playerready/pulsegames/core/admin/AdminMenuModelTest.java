package uk.co.playerready.pulsegames.core.admin;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.playerready.pulsegames.core.ui.forms.FormLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminMenuModelTest {
    @Test void auditLineFormat() {
        assertEquals("PGADMIN Aurora arena-disable spleef/ice_pit", AdminAudit.line("Aurora", "arena-disable", "spleef/ice_pit"));
        assertEquals("PGADMIN CONSOLE reload -", AdminAudit.line("CONSOLE", "reload", ""));
        assertEquals("PGADMIN A level-set Bob_Smith:10", AdminAudit.line("A", "level-set", "Bob Smith:10"));
        List<String> seen = new ArrayList<>();
        new AdminAudit(seen::add).log("A", "x", "y");
        assertEquals(List.of("PGADMIN A x y"), seen);
    }

    @Test void sectionVisibility() {
        assertTrue(AdminSections.visible(false, true, true).isEmpty());
        assertEquals(6, AdminSections.visible(true, true, true).size());
        assertFalse(AdminSections.visible(true, true, false).contains(AdminSections.EVENTS));
        assertFalse(AdminSections.visible(true, false, true).contains(AdminSections.PLAYERS));
        assertEquals(AdminSections.GAMES, AdminSections.visible(true, true, true).getFirst());
    }

    @Test void levelAndXpParsing() {
        assertNull(AdminInput.levelError("1"));
        assertNull(AdminInput.levelError("100"));
        assertNotNull(AdminInput.levelError("0"));
        assertNotNull(AdminInput.levelError("101"));
        assertNotNull(AdminInput.levelError("ten"));
        assertNull(AdminInput.xpError("0"));
        assertNull(AdminInput.xpError("1000000"));
        assertNotNull(AdminInput.xpError("-5"));
        assertNotNull(AdminInput.xpError("1000001"));
        assertTrue(AdminInput.difficulty("HARD"));
        assertFalse(AdminInput.difficulty("brutal"));
        assertNull(AdminInput.nameError("Steve_1"));
        assertNull(AdminInput.nameError(".BedrockKid"));
        assertNotNull(AdminInput.nameError("a"));
    }

    @Test void arenaTogglePathMovesFileInAndOutOfDisabled(@TempDir Path dir) throws IOException {
        ArenaFiles files = new ArenaFiles(dir);
        Path live = dir.resolve("spleef-ice.yml");
        Files.writeString(live, "id: ice\ngame: spleef\ndisplay-name: Ice Pit\nsettings:\n  bots-enabled: true\n");
        Files.writeString(dir.resolve("other.yml"), "id: o\ngame: bedwars\n");
        assertEquals(dir.resolve("disabled").resolve("spleef-ice.yml"), files.disabledPath(live));

        var entry = files.list("spleef").getFirst();
        assertTrue(entry.enabled());
        assertTrue(entry.botsEnabled());
        assertEquals(1, files.list("spleef").size());

        Path moved = files.toggle(entry);
        assertFalse(Files.exists(live));
        assertTrue(Files.exists(moved));
        var off = files.list("spleef").getFirst();
        assertFalse(off.enabled());
        assertEquals(live, files.enabledPath(off.file()));

        files.toggle(off);
        assertTrue(Files.exists(live));
        assertTrue(files.list("spleef").getFirst().enabled());
    }

    @Test void botsSettingIsWrittenToTheFile(@TempDir Path dir) throws IOException {
        ArenaFiles files = new ArenaFiles(dir);
        Files.writeString(dir.resolve("a.yml"), "id: a\ngame: spleef\n");
        files.setBots(files.list("spleef").getFirst(), true);
        assertTrue(files.list("spleef").getFirst().botsEnabled());
    }

    @Test void screenButtonsFollowEntriesThenBack() {
        var entries = List.of(AdminScreen.Entry.info(Material.PAPER, "<yellow><b>Players", "three here"),
                AdminScreen.Entry.button(Material.PAPER, "<green>Go", p -> { }));
        var buttons = AdminScreen.buttons(entries, true);
        assertEquals(3, buttons.size());
        assertEquals("Players\nthree here", buttons.get(0).label());
        assertEquals("Go", buttons.get(1).label());
        assertEquals(FormLayout.BACK, buttons.get(2).target());
        assertEquals(1, AdminScreen.indexOf(buttons.get(1).target()));
        assertEquals(-1, AdminScreen.indexOf(FormLayout.BACK));
        assertEquals(2, AdminScreen.buttons(entries, false).size());
    }

    @Test void inputLabelPutsTheErrorFirst() {
        assertEquals("Level", FormLayout.inputLabel("Level", ""));
        assertEquals("Numbers only.\nLevel", FormLayout.inputLabel("Level", "Numbers only."));
    }
}
