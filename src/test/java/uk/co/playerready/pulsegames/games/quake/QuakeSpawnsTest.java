package uk.co.playerready.pulsegames.games.quake;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuakeSpawnsTest {
    World world() {
        World world=mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        Block floor=mock(Block.class,RETURNS_DEEP_STUBS),air=mock(Block.class,RETURNS_DEEP_STUBS);
        when(floor.getType()).thenReturn(Material.STONE);when(floor.getBlockData().isOccluding()).thenReturn(true);when(air.getType()).thenReturn(Material.AIR);when(air.isEmpty()).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call -> (int)call.getArgument(1)==63?floor:air);
        return world;
    }
    @Test void choosesInteriorFloorOutsideAnchorListAndAwayFromOpponents() {
        World w=world();Cuboid area=new Cuboid(-12,64,-12,12,65,12);
        Location enemy=new Location(w,0,64,0);
        Location spawn=QuakeSpawns.choose(w,area,List.of(enemy),List.of(enemy),new Random(7));
        assertNotNull(spawn);assertTrue(area.contains(spawn));assertEquals(64,spawn.getY());
        assertTrue(spawn.distanceSquared(enemy)>=64);assertEquals(.5,spawn.getX()-Math.floor(spawn.getX()));
    }
    @Test void rejectsOccupiedCellsHazardsAndInsufficientHeadroom() {
        World w=world();Cuboid area=new Cuboid(0,64,0,0,65,0);
        assertNull(QuakeSpawns.choose(w,area,List.of(new Location(w,.5,64,.5)),List.of(),new Random(1)));
        Block magma=mock(Block.class,RETURNS_DEEP_STUBS);when(magma.getType()).thenReturn(Material.MAGMA_BLOCK);when(magma.getBlockData().isOccluding()).thenReturn(true);when(w.getBlockAt(0,63,0)).thenReturn(magma);
        assertFalse(QuakeSpawns.safe(w,0,64,0));
        Block stone=mock(Block.class,RETURNS_DEEP_STUBS);when(stone.getType()).thenReturn(Material.STONE);when(stone.getBlockData().isOccluding()).thenReturn(true);when(w.getBlockAt(0,63,0)).thenReturn(stone);when(w.getBlockAt(0,65,0)).thenReturn(stone);
        assertFalse(QuakeSpawns.safe(w,0,64,0));
    }
    @Test void neverLoadsChunksAndBoundsFailedSearchWork() {
        World w=world();when(w.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);clearInvocations(w);
        assertNull(QuakeSpawns.choose(w,new Cuboid(0,64,0,20,65,20),List.of(),List.of(),new Random(2)));
        verify(w,never()).getBlockAt(anyInt(),anyInt(),anyInt());verify(w,times(1024)).isChunkLoaded(anyInt(),anyInt());
    }
    @Test void canUseLowerAndUpperFloorsButNeverOutsideVerticalBounds() {
        World w=world();Block stone=mock(Block.class,RETURNS_DEEP_STUBS),air=mock(Block.class,RETURNS_DEEP_STUBS);when(stone.getType()).thenReturn(Material.STONE);when(stone.getBlockData().isOccluding()).thenReturn(true);when(air.getType()).thenReturn(Material.AIR);when(air.isEmpty()).thenReturn(true);
        when(w.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call -> Set.of(63,68).contains((int)call.getArgument(1))?stone:air);
        Set<Integer> levels=new HashSet<>();
        for(int seed=0;seed<20;seed++) {
            Location p=QuakeSpawns.choose(w,new Cuboid(0,64,0,5,70,5),List.of(),List.of(),new Random(seed));
            assertNotNull(p);levels.add(p.getBlockY());
        }
        assertEquals(Set.of(64,69),levels);
    }
    @Test void allUnsafeFloorIsRejectedRatherThanSpawningInAir() {
        World w=world();Block air=mock(Block.class,RETURNS_DEEP_STUBS);when(air.getType()).thenReturn(Material.AIR);when(air.isEmpty()).thenReturn(true);when(w.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);clearInvocations(w);
        assertNull(QuakeSpawns.choose(w,new Cuboid(0,64,0,20,65,20),List.of(),List.of(),new Random(1)));
        verify(w,times(1024)).getBlockAt(anyInt(),anyInt(),anyInt());
    }
}
