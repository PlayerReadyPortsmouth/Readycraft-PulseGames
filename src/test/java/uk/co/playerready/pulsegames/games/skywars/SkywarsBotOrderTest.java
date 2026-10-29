package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkywarsBotOrderTest {
    @Test void lootsBeforeBridgingEvenWithBlocksAndARivalInRange() {
        assertEquals(SkywarsBotOrder.Step.LOOT, SkywarsBotOrder.next(true, true, true));
    }
    @Test void bridgesOnceTheChestsAreDoneAndItHasBlocks() {
        assertEquals(SkywarsBotOrder.Step.BRIDGE, SkywarsBotOrder.next(false, true, true));
    }
    @Test void huntsOnItsOwnIslandWithNothingToBridgeWith() {
        assertEquals(SkywarsBotOrder.Step.HUNT_HERE, SkywarsBotOrder.next(false, false, true));
        assertEquals(SkywarsBotOrder.Step.HUNT_HERE, SkywarsBotOrder.next(false, true, false));
    }
    @Test void anySolidStackableBlockWillBridgeButNotSandOrStairs() {
        assertTrue(SkywarsBotInventory.stackableBridgeBlock(Material.DIRT));
        assertTrue(SkywarsBotInventory.stackableBridgeBlock(Material.CALCITE));
        assertFalse(SkywarsBotInventory.stackableBridgeBlock(Material.SAND));
        assertFalse(SkywarsBotInventory.stackableBridgeBlock(Material.OAK_STAIRS));
        assertFalse(SkywarsBotInventory.stackableBridgeBlock(Material.MAGMA_BLOCK));
    }
}
