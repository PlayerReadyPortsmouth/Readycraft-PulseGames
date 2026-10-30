package uk.co.playerready.pumpkinhunt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HuntAdminRouteTest {
    @Test void bareAdminIsASummary() {
        assertEquals("", HuntAdminRoute.parse(new String[0]).verb());
    }

    @Test void verbsAndArgumentsParse() {
        var r = HuntAdminRoute.parse(new String[] {"Reset", "Alex"});
        assertEquals("reset", r.verb());
        assertArrayEquals(new String[] {"Alex"}, r.args());
        for (String v : new String[] {"stats", "list", "enable", "disable"}) assertEquals(v, HuntAdminRoute.parse(new String[] {v}).verb());
    }

    @Test void unknownVerbIsRejected() {
        assertNull(HuntAdminRoute.parse(new String[] {"nuke"}));
    }

    @Test void routeCommandStripsOnlyItsOwnArea() {
        assertArrayEquals(new String[] {"stats"}, HuntAdminRoute.stripArea(new String[] {"hunt", "stats"}));
        assertArrayEquals(new String[0], HuntAdminRoute.stripArea(new String[] {"HUNT"}));
        assertNull(HuntAdminRoute.stripArea(new String[] {"games"}));
        assertNull(HuntAdminRoute.stripArea(new String[0]));
    }

    @Test void routeCommandIsDeclaredAndAdminGated() {
        var in = getClass().getResourceAsStream("/plugin.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        assertEquals("pumpkinhunt.admin", y.getString("commands." + HuntAdminRoute.ROUTE_COMMAND + ".permission"));
    }
}
