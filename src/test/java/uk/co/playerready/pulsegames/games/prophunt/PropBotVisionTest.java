package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropBotVisionTest {
    @Test void fieldOfViewRangeAndWallsConstrainPublicPropObservation() {
        World world = mock(World.class); Player seeker = mock(Player.class);
        when(seeker.getEyeLocation()).thenReturn(new Location(world, 0, 65, 0, 0, 0));
        assertTrue(PropHuntGame.visibleProp(seeker, new Location(world, 0, 65, 5)));
        assertFalse(PropHuntGame.visibleProp(seeker, new Location(world, 0, 65, -5)));
        assertFalse(PropHuntGame.visibleProp(seeker, new Location(world, 0, 65, 17)));
        assertFalse(PropHuntGame.visibleProp(seeker, new Location(mock(World.class), 0, 65, 5)));
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(new RayTraceResult(new Vector(0, 65, 2)));
        assertFalse(PropHuntGame.visibleProp(seeker, new Location(world, 0, 65, 5)));
    }
    @Test void aBlockOnlyOccludesWhenItIsNotTheTargetsOwnCell() {
        World world = mock(World.class); Player seeker = mock(Player.class);
        when(seeker.getEyeLocation()).thenReturn(new Location(world, 0.5, 65.5, 0.5, 0, 0));
        var target = mock(org.bukkit.block.Block.class); when(target.getX()).thenReturn(0); when(target.getY()).thenReturn(65); when(target.getZ()).thenReturn(5);
        var wall = mock(org.bukkit.block.Block.class); when(wall.getX()).thenReturn(0); when(wall.getY()).thenReturn(65); when(wall.getZ()).thenReturn(3);
        var point = new Location(world, 0.5, 65.5, 5.5);
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(new RayTraceResult(new Vector(0.5, 65.5, 5), target, org.bukkit.block.BlockFace.NORTH));
        assertTrue(PropHuntGame.visibleProp(seeker, point)); // The placed prop block must not hide its own target.
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(new RayTraceResult(new Vector(0.5, 65.5, 3), wall, org.bukkit.block.BlockFace.NORTH));
        assertFalse(PropHuntGame.visibleProp(seeker, point));
    }
}
