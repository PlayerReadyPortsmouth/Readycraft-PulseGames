package uk.co.playerready.pulsegames.core.events;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EventDefaultConfigTest {
    @Test void shippedConfigGivesAnHourlyRotationOfEightGames() {
        var cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml")));
        assertTrue(cfg.getBoolean("events.enabled"));
        assertEquals("Europe/London", cfg.getString("events.timezone"));
        List<EventSchedule.Pick> rotation = new ArrayList<>();
        for (var raw : cfg.getMapList("events.games")) rotation.add(EventSchedule.parsePick(raw, 2.0));
        assertEquals(8, rotation.size());
        assertFalse(rotation.contains(null));
        assertEquals(2.0, rotation.getFirst().multiplier());
        var every = EventSchedule.parseEvery(String.valueOf(cfg.getMapList("events.schedule").getFirst().get("every")));
        var schedule = new EventSchedule(ZoneId.of("Europe/London"),
                List.of(new EventSchedule.Entry(null, every, null)), rotation);
        var eight = schedule.next(Instant.parse("2026-11-10T10:00:00Z"), 8);
        assertEquals(8, eight.stream().map(o -> o.pick().game()).collect(Collectors.toSet()).size(), "one full rotation shows every game once");
    }
}
