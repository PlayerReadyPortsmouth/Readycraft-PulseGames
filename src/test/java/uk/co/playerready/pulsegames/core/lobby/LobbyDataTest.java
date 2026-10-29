package uk.co.playerready.pulsegames.core.lobby;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LobbyDataTest {
    private static LobbySpot spot(double x, double z) { return new LobbySpot("lobby", x, 64, z, 90f, 0f); }
    private final LocalDate today = LocalDate.of(2026, 10, 29);

    @Test void roundTripsEverythingThroughYaml() {
        var d = new LobbyData();
        d.addNpc("spleef", null, spot(1.5, 2.5));
        d.addNpc("bedwars", "solo", spot(10, 10));
        d.addBoard("weekly", "spleef", "wins", spot(5, 5));
        d.addBoard("alltime", null, "tokens", spot(6, 6));
        d.addBoard("parkour", null, "time", spot(7, 7));
        d.setChest(spot(20, 20));
        d.setParkourStart(spot(30, 30));
        d.addCheckpoint(spot(31, 30));
        d.addCheckpoint(spot(32, 30));
        d.setParkourEnd(spot(33, 30));
        UUID id = UUID.randomUUID();
        d.markClaimed(id, today);

        var loaded = LobbyData.fromYaml(YamlConfiguration.loadConfiguration(new java.io.StringReader(d.toYaml(today).saveToString())));
        assertEquals(d.npcs(), loaded.npcs());
        assertEquals(d.boards(), loaded.boards());
        assertEquals(d.chest(), loaded.chest());
        assertEquals(d.checkpoints(), loaded.checkpoints());
        assertEquals(d.parkourStart(), loaded.parkourStart());
        assertEquals(d.parkourEnd(), loaded.parkourEnd());
        assertEquals(today, loaded.claimedOn(id));
        // ids keep counting after a reload instead of reusing one
        assertNotEquals("n1", loaded.addNpc("quake", null, spot(0, 0)).id());
    }

    @Test void oldClaimsAreDroppedOnSaveAndBrokenEntriesSkipped() {
        var d = new LobbyData();
        UUID id = UUID.randomUUID();
        d.markClaimed(id, today.minusDays(1));
        var y = d.toYaml(today);
        assertNull(LobbyData.fromYaml(y).claimedOn(id));
        y.set("npcs.n9.game", "spleef");
        y.set("npcs.n9.at", "garbage");
        assertTrue(LobbyData.fromYaml(y).npcs().isEmpty());
    }

    @Test void removesTheNearestNpcWithinRangeOnly() {
        var d = new LobbyData();
        var far = d.addNpc("spleef", null, spot(10, 0));
        var near = d.addNpc("quake", null, spot(2, 0));
        assertEquals(near, d.nearestNpc(spot(0, 0), 3).orElseThrow());
        assertTrue(d.removeNpc(near));
        assertTrue(d.nearestNpc(spot(0, 0), 3).isEmpty());
        assertEquals(far, d.nearestNpc(spot(9, 0), 3).orElseThrow());
        assertTrue(d.nearestNpc(new LobbySpot("other", 10, 64, 0, 0, 0), 3).isEmpty());
    }

    @Test void boardsRemoveNearestToo() {
        var d = new LobbyData();
        var b = d.addBoard("weekly", null, "wins", spot(1, 1));
        assertEquals(b, d.nearestBoard(spot(0, 0), 3).orElseThrow());
        assertTrue(d.removeBoard(b));
        assertTrue(d.boards().isEmpty());
    }

    @Test void resettingParkourClearsThePlatesAndItsBoardOnly() {
        var d = new LobbyData();
        d.setParkourStart(spot(1, 1));
        d.addCheckpoint(spot(2, 2));
        d.addBoard("parkour", null, "time", spot(3, 3));
        d.addBoard("weekly", null, "wins", spot(4, 4));
        d.resetParkour();
        assertNull(d.parkourStart());
        assertTrue(d.checkpoints().isEmpty());
        assertEquals(1, d.boards().size());
    }
}
