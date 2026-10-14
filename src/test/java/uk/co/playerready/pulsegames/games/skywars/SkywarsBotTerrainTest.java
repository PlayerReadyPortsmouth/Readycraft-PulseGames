package uk.co.playerready.pulsegames.games.skywars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkywarsBotTerrainTest {
    static final class Fixture {
        final World world=mock(World.class); final WorldBorder border=mock(WorldBorder.class);
        Fixture() {
            when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
            when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{
                int x=i.getArgument(0),y=i.getArgument(1),z=i.getArgument(2);
                return block(y==63?Material.STONE:Material.AIR,x,y,z);
            });
        }
        Block block(Material material,int x,int y,int z) {
            Block b=mock(Block.class); when(b.getType()).thenReturn(material); when(b.isPassable()).thenReturn(SkywarsBotTerrain.air(material));
            when(b.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1)); return b;
        }
        Location at(double x,double z) { return new Location(world,x,64,z); }
    }
    @Test void nextStepIsCardinalOneCellAndCentresBeforeLeavingCurrentCell() {
        var f=new Fixture(); var here=f.at(.5,.5);
        var step=SkywarsBotTerrain.step(here,f.at(20,20));
        assertEquals(1.5,step.getX()); assertEquals(.5,step.getZ()); assertEquals(1,here.distanceSquared(step));
        assertEquals(f.at(.5,.5),SkywarsBotTerrain.step(f.at(.1,.2),f.at(20,20)));
        assertNull(SkywarsBotTerrain.step(here,new Location(mock(World.class),20,64,20)));
    }
    @Test void corridorCannotSkipGapOrMakeVerticalJump() {
        var f=new Fixture(); assertTrue(SkywarsBotTerrain.corridor(f.at(.5,.5),f.at(1.5,.5)));
        when(f.world.getBlockAt(1,63,0)).thenAnswer(i->f.block(Material.AIR,1,63,0));
        assertFalse(SkywarsBotTerrain.corridor(f.at(.5,.5),f.at(1.5,.5)));
        assertTrue(SkywarsBotTerrain.safe(f.at(1.5,.5),false));
        assertFalse(SkywarsBotTerrain.corridor(f.at(.5,.5),f.at(1.5,.5).add(0,1,0)));
        assertFalse(SkywarsBotTerrain.corridor(f.at(.5,.5),f.at(4.5,.5)));
    }
    @Test void unloadedFootprintNeverReadsBlocksAndWallPreventsWalking() {
        var f=new Fixture(); when(f.world.isChunkLoaded(1,0)).thenReturn(false);
        assertFalse(SkywarsBotTerrain.safe(f.at(15.9,.5),true));
        verify(f.world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        when(f.world.isChunkLoaded(1,0)).thenReturn(true);
        when(f.world.getBlockAt(1,65,0)).thenAnswer(i->f.block(Material.STONE,1,65,0));
        assertFalse(SkywarsBotTerrain.corridor(f.at(.5,.5),f.at(1.5,.5)));
    }
    @Test void narrowOrOpenFloorCannotSupportFullPlayerFootprint() {
        var f=new Fixture(); Block thin=f.block(Material.STONE,0,63,0);
        when(thin.getBoundingBox()).thenReturn(new BoundingBox(.4,63,0,.6,64,1));
        when(f.world.getBlockAt(0,63,0)).thenReturn(thin);
        assertFalse(SkywarsBotTerrain.safe(f.at(.5,.5),true));
        when(f.world.getBlockAt(0,63,0)).thenAnswer(i->f.block(Material.OAK_TRAPDOOR,0,63,0));
        assertFalse(SkywarsBotTerrain.safe(f.at(.5,.5),true));
    }
    @Test void hazardousFloorAndNonFinitePositionsAreRejected() {
        var f=new Fixture(); when(f.world.getBlockAt(0,63,0)).thenAnswer(i->f.block(Material.MAGMA_BLOCK,0,63,0));
        assertFalse(SkywarsBotTerrain.safe(f.at(.5,.5),true));
        assertFalse(SkywarsBotTerrain.safe(f.at(Double.NaN,.5),true));
        assertFalse(SkywarsBotTerrain.safe(f.at(Double.POSITIVE_INFINITY,.5),true));
    }
}
