package uk.co.playerready.pulsegames.games.prophunt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PropPaletteScanTest {
    World world(){World w=mock(World.class);when(w.getMinHeight()).thenReturn(-64);when(w.getMaxHeight()).thenReturn(320);return w;}
    List<PropPalette.Option> options(){return List.of(new PropPalette.Option("Stone",Material.STONE,"ready:stone"),new PropPalette.Option("Head",Material.PLAYER_HEAD,"ready:head"));}
    @Test void scansNegativeCoordinatesAndOnlyOffersSupportedObservedMaterials(){
        World w=world();when(w.isChunkLoaded(-1,-1)).thenReturn(true);
        Block block=mock(Block.class);when(block.getType()).thenReturn(Material.STONE);when(w.getBlockAt(-1,64,-1)).thenReturn(block);
        var scan=new PropPaletteScan(w,new Cuboid(-1,64,-1,-1,64,-1),options());assertTrue(scan.step());assertEquals(List.of("STONE"),scan.result());
    }
    @Test void boundedWorkDoesNotExposePartialResult(){
        World w=world();when(w.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        Block block=mock(Block.class);when(block.getType()).thenReturn(Material.STONE);when(w.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
        var scan=new PropPaletteScan(w,new Cuboid(0,64,0,63,64,127),options());assertFalse(scan.step());
        verify(w,times(4096)).getBlockAt(anyInt(),anyInt(),anyInt());assertThrows(IllegalStateException.class,scan::result);
        assertTrue(scan.step());assertEquals(List.of("STONE"),scan.result());
    }
    @Test void unloadedChunkIsRejectedWithoutLoadingOrGeneratingIt(){
        World w=world();var scan=new PropPaletteScan(w,new Cuboid(0,64,0,0,64,0),options());
        assertThrows(IllegalArgumentException.class,scan::step);verify(w,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        assertThrows(IllegalStateException.class,scan::result);
    }
    @Test void noSupportedBlocksAndOutOfWorldBoundsAreRejected(){
        World w=world();when(w.isChunkLoaded(0,0)).thenReturn(true);Block block=mock(Block.class);when(block.getType()).thenReturn(Material.AIR);when(w.getBlockAt(0,64,0)).thenReturn(block);
        var scan=new PropPaletteScan(w,new Cuboid(0,64,0,0,64,0),options());assertTrue(scan.step());assertThrows(IllegalArgumentException.class,scan::result);
        assertThrows(IllegalArgumentException.class,()->new PropPaletteScan(w,new Cuboid(0,320,0,0,320,0),options()));
    }
}
