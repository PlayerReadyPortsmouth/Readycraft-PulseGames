package uk.co.playerready.pulsegames.core.game;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ComebackTest {
    @Test void bedlessAndOutnumberedActivatesAndEndsWhenEvenOrBedBack() {
        Map<Integer, Boolean> bed = Map.of(0, false, 1, true, 2, true);
        assertEquals(Set.of(0), Comeback.bedwarsTeams(bed, Map.of(0, 1, 1, 3, 2, 2)));
        // the other side loses players: no longer outnumbered
        assertEquals(Set.of(), Comeback.bedwarsTeams(bed, Map.of(0, 1, 1, 1, 2, 1)));
        // out of players altogether
        assertEquals(Set.of(), Comeback.bedwarsTeams(bed, Map.of(0, 0, 1, 3, 2, 2)));
    }

    @Test void aTeamWithTheOnlyBedLeftGetsNothing() {
        Map<Integer, Boolean> bed = Map.of(0, false, 1, true);
        assertEquals(Set.of(0), Comeback.bedwarsTeams(bed, Map.of(0, 1, 1, 4)));
        assertFalse(Comeback.bedwarsTeam(true, 1, 4), "still has a bed");
        assertEquals(Set.of(), Comeback.bedwarsTeams(Map.of(0, true, 1, false), Map.of(0, 1, 1, 4)).stream()
                .filter(t -> t == 0).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void killsBehindThreshold() {
        assertTrue(Comeback.killsBehind(2, 7, 5));
        assertFalse(Comeback.killsBehind(3, 7, 5));
        assertFalse(Comeback.killsBehind(0, 20, 0), "a zero threshold switches it off");
    }

    @Test void boostFollowsTheConfigSwitches() {
        YamlConfiguration cfg = new YamlConfiguration();
        assertTrue(Comeback.boostDue(cfg, "quake", 0, 5));
        assertEquals(60, Comeback.boostTicks(cfg, "quake"));
        cfg.set("comeback.quake.enabled", false);
        assertFalse(Comeback.boostDue(cfg, "quake", 0, 5));
        assertTrue(Comeback.boostDue(cfg, "kitpvp", 0, 5));
        cfg.set("comeback.enabled", false);
        assertFalse(Comeback.boostDue(cfg, "kitpvp", 0, 5));
    }

    @Test void onlyTheSmallerSideGetsTheExtraBall() {
        assertTrue(Comeback.smallerSide(2, 3));
        assertFalse(Comeback.smallerSide(3, 3));
    }
}
