package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetupCoordinatesTest {
    World world() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }
    @Test void absolutePointsAndOrientationAreIndependentOfPlayerPosition() {
        World world = world();
        Location point = SetupCoordinates.point(world, new Location(world, 900, 70, 900),
                new String[]{"setloc", "finish", "-3.5", "80", "12.5", "90", "-30"}, 2, false);
        assertEquals(-3.5, point.getX()); assertEquals(90, point.getYaw()); assertEquals(-30, point.getPitch());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
    @Test void rejectsNonfiniteRelativeOutOfBoundsAndPartialCoordinates() {
        World world = world();
        for (String[] args : new String[][]{{"NaN","80","0"},{"Infinity","80","0"},{"~","80","0"},
                {"0","320","0"},{"30000000","80","0"},{"1","2"},{"1","2","3","4"},
                {"1","80","3","0","91"}})
            assertThrows(IllegalArgumentException.class, () -> SetupCoordinates.point(world, null, args, 0, false));
        assertThrows(IllegalArgumentException.class, () -> SetupCoordinates.point(world, null, new String[]{"1.5","80","0"}, 0, true));
    }
    @Test void omittedCoordinatesKeepExistingHereBehaviour() {
        World world = world(); Location here = new Location(world, 4.5, 80, 9.5, 32, 10);
        Location result = SetupCoordinates.point(world, here, new String[]{"lobby"}, 1, false);
        assertEquals(here, result); assertNotSame(here, result);
    }
    @Test void commandsSetRegionsWithoutItemsAndRejectBadInputWithoutMutation() {
        var plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getName()).thenReturn("PulseGames");
        var manager = spy(new ArenaSetupManager(plugin));
        World world = world(); Player player = mock(Player.class);
        when(player.hasPermission("pulsegames.admin")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 80, 0));
        var session = new ArenaSetupManager.Session(null, "test", world);
        doReturn(session).when(manager).session(player);
        manager.handle(player, new String[]{"region","landing1","1","70","2","5","74","6"});
        assertEquals(1, session.regions.get("landing1").minX());
        manager.handle(player, new String[]{"region","landing1","0","0","0","1000","100","1000"});
        assertEquals(1, session.regions.get("landing1").minX());
        manager.handle(player, new String[]{"pos1","-2","70","-3"});
        manager.handle(player, new String[]{"pos2","2","72","3"});
        manager.handle(player, new String[]{"region","floor"});
        assertEquals(-2, session.regions.get("floor").minX());
        manager.handle(player, new String[]{"addspawn","1.5","80","2.5","90","0"});
        assertEquals(1, session.spawns.size());
        manager.handle(player, new String[]{"addspawn","NaN","80","2"});
        assertEquals(1, session.spawns.size());
        verify(player, never()).getInventory();
        when(player.getWorld()).thenReturn(mock(World.class));
        manager.handle(player, new String[]{"region","wrong","1","70","2","5","74","6"});
        assertFalse(session.regions.containsKey("wrong"));
    }
}
