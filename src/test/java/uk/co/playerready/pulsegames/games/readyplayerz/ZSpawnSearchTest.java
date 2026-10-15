package uk.co.playerready.pulsegames.games.readyplayerz;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.util.Cuboid;
class ZSpawnSearchTest {
    World world;
    Player player;
    Block floor, air;
    final Cuboid region = new Cuboid(10, 64, 0, 10, 64, 0);
    @BeforeEach
    void setup() {
        world = mock(World.class);
        player = mock(Player.class);
        floor = mock(Block.class);
        air = mock(Block.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.getBlockAt(10, 63, 0)).thenReturn(floor);
        when(air.isPassable()).thenReturn(true);
        when(floor.getBoundingBox()).thenReturn(new BoundingBox(10, 63, 0, 11, 64, 1));
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, .5, 64, .5));
    }
    @Test
    void selectionUsesCurrentPlayerPosition() {
        assertTrue(ZSpawnSearch.choose(world, List.of(region), List.of(player), 8, 48, 2.2, p -> true, new Random(1))
                .isPresent());
        when(player.getLocation()).thenReturn(new Location(world, 10.5, 64, .5));
        assertTrue(ZSpawnSearch.choose(world, List.of(region), List.of(player), 8, 48, 2.2, p -> true, new Random(1))
                .isEmpty());
    }
    @Test
    void unloadedRegionsDoNotReadBlocksOrGenerateChunks() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        clearInvocations(world);
        assertFalse(ZSpawnSearch.safe(new Location(world, 10.5, 64, .5), 3));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
    @Test
    void bossesNeedExtraHeadroom() {
        Block ceiling = mock(Block.class);
        when(ceiling.getBoundingBox()).thenReturn(new BoundingBox(10, 66, 0, 11, 67, 1));
        when(world.getBlockAt(10, 66, 0)).thenReturn(ceiling);
        assertFalse(ZSpawnSearch.safe(new Location(world, 10.5, 64, .5), 3));
    }
    @Test
    void lockedOrUnsupportedSpawnsFailWithoutFallback() {
        assertTrue(ZSpawnSearch.choose(world, List.of(region), List.of(player), 8, 48, 2.2, p -> false, new Random(1))
                .isEmpty());
        when(floor.isPassable()).thenReturn(true);
        assertTrue(ZSpawnSearch.choose(world, List.of(region), List.of(player), 8, 48, 2.2, p -> true, new Random(1))
                .isEmpty());
        assertTrue(
            ZSpawnSearch.choose(world, List.of(), List.of(player), 8, 48, 2.2, p -> true, new Random(1)).isEmpty());
    }
}
