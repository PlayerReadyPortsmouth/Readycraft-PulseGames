package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkywarsBotRunTest {
    @Test void runsSeveralCellsAheadWithoutTurningBackWhenMidBlock() {
        var f = new SkywarsBotTerrainTest.Fixture();
        Location run = SkywarsBotTerrain.run(f.at(.9, .5), f.at(20, .5), 3);
        assertNotNull(run); assertEquals(3.5, run.getX(), .001); assertEquals(.5, run.getZ(), .001);
    }

    @Test void stopsAtTheEdgeOfTheIsland() {
        var f = new SkywarsBotTerrainTest.Fixture();
        when(f.world.getBlockAt(2, 63, 0)).thenAnswer(i -> f.block(Material.AIR, 2, 63, 0));
        Location run = SkywarsBotTerrain.run(f.at(.5, .5), f.at(20, .5), 3);
        assertNotNull(run); assertEquals(1.5, run.getX(), .001);
        when(f.world.getBlockAt(1, 63, 0)).thenAnswer(i -> f.block(Material.AIR, 1, 63, 0));
        assertNull(SkywarsBotTerrain.run(f.at(.5, .5), f.at(20, .5), 3));
    }

    @Test void doesNotRunPastTheGoalAndCentresAcrossTheLineFirst() {
        var f = new SkywarsBotTerrainTest.Fixture();
        assertEquals(2.5, SkywarsBotTerrain.run(f.at(.5, .5), f.at(2.4, .5), 3).getX(), .001);
        Location across = SkywarsBotTerrain.run(f.at(.5, .9), f.at(20, .5), 3);
        assertEquals(.5, across.getZ(), .001); assertEquals(.5, across.getX(), .001);
    }
}
