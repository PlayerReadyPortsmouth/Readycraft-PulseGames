package uk.co.playerready.pumpkinhunt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ShippedConfigTest {
    @Test void thirteenLanternsWithUniqueIdsAndPositions() {
        var in = getClass().getResourceAsStream("/config.yml");
        YamlConfiguration c = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        assertFalse(c.getBoolean("enabled"));
        Set<String> ids = new HashSet<>(), pos = new HashSet<>();
        for (Map<?, ?> m : c.getMapList("lanterns")) {
            ids.add(String.valueOf(m.get("id")));
            pos.add(m.get("x") + ":" + m.get("y") + ":" + m.get("z"));
            assertEquals("world", m.get("world"));
        }
        assertEquals(13, c.getMapList("lanterns").size());
        assertEquals(13, ids.size());
        assertEquals(13, pos.size());
    }
}
