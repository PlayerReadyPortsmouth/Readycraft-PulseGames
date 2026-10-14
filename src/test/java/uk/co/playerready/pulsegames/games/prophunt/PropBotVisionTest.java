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
}
