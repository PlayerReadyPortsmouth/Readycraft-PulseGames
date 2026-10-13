package uk.co.playerready.pulsegames.games.prophunt;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropSpaceTest {
    private World room() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int x=call.getArgument(0), y=call.getArgument(1), z=call.getArgument(2);
            Block block=mock(Block.class);
            when(block.isPassable()).thenReturn(y==64 && x==0 && z==0);
            when(block.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1));
            return block;
        });
        return world;
    }
    @Test void fullPropFitsOneHighGapTouchingFloorRoofAndSideWalls() {
        World world=room(); Location feet=new Location(world,.5,64,.5);
        assertTrue(PropSpace.clear(feet,1)); assertTrue(PropSpace.supported(feet));
        assertFalse(PropSpace.clear(new Location(world,.6,64,.5),1));
        assertFalse(PropSpace.clear(new Location(world,.5,64.1,.5),1));
    }
    @Test void smallHeadFitsWhereCubeWouldIntersectShelf() {
        World world=room(); Block shelf=mock(Block.class);
        when(shelf.getBoundingBox()).thenReturn(new BoundingBox(0,64.5,0,1,65,1));
        when(world.getBlockAt(0,64,0)).thenReturn(shelf);
        assertTrue(PropSpace.clear(new Location(world,.5,64,.5),.5));
        assertFalse(PropSpace.clear(new Location(world,.5,64,.5),1));
    }
    @Test void rejectsUnloadedOutsideHeightAndLiquidCellsWithoutLoadingChunks() {
        World world=room(); Location feet=new Location(world,.5,64,.5);
        when(world.isChunkLoaded(0,0)).thenReturn(false);
        assertFalse(PropSpace.clear(feet,1)); verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        when(world.isChunkLoaded(0,0)).thenReturn(true);
        Block water=mock(Block.class); when(water.isLiquid()).thenReturn(true); when(world.getBlockAt(0,64,0)).thenReturn(water);
        assertFalse(PropSpace.clear(feet,.5)); assertFalse(PropSpace.clear(new Location(world,.5,320,.5),1));
    }
}
