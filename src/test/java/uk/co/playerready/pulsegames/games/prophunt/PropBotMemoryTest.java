package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PropBotMemoryTest {
    private final org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
    @Test void consecutiveVisibleMovementDoesNotDependOnBukkitVelocity() {
        var memory = new PropBotMemory(); var target = UUID.randomUUID();
        memory.observe(target, new Location(world, 3, 64, 4), true, false, 10);
        var moved = new Location(world, 3.2, 64, 4);
        memory.observe(target, moved, true, false, 11);
        assertEquals(moved, memory.destination(11));
    }
    @Test void stationaryUnknownPropIsNotAnAutomaticTarget() {
        var memory = new PropBotMemory();
        memory.observe(UUID.randomUUID(), new Location(world, 3, 64, 4), true, false, 10);
        assertNull(memory.destination(10));
    }
    @Test void invisibleMovementProvidesNoInformation() {
        var memory = new PropBotMemory();
        memory.observe(UUID.randomUUID(), new Location(world, 3, 64, 4), false, true, 10);
        assertNull(memory.destination(10));
    }
    @Test void lostSightKeepsOnlyOldPositionAndExpires() {
        var memory = new PropBotMemory(); var target = UUID.randomUUID();
        var old = new Location(world, 3, 64, 4);
        memory.observe(target, old, true, true, 10);
        memory.observe(target, new Location(world, 90, 64, 90), false, true, 50);
        assertEquals(old, memory.destination(50));
        assertNull(memory.destination(111));
    }
    @Test void recognisedPropCanBeTrackedOnlyWhileVisible() {
        var memory = new PropBotMemory(); var target = UUID.randomUUID();
        memory.observe(target, new Location(world, 3, 64, 4), true, true, 10);
        var stopped = new Location(world, 5, 64, 4);
        memory.observe(target, stopped, true, false, 70);
        assertEquals(stopped, memory.destination(150));
        assertNull(memory.destination(171));
    }
}
