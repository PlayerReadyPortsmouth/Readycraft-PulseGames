package uk.co.playerready.pulsegames.core.bot;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GroundNavigationTest {
    World terrain(BiFunction<Integer,Integer,Material> surface) {
        World world=mock(World.class);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            int x=call.getArgument(0),y=call.getArgument(1),z=call.getArgument(2);
            Material type=y==79?surface.apply(x,z):Material.AIR;
            Block block=mock(Block.class);when(block.getType()).thenReturn(type);
            when(block.isPassable()).thenReturn(type == Material.AIR);return block;
        }); return world;
    }
    @Test void returnsAdjacentStepInsteadOfTeleportingToGoal() {
        World w=terrain((x,z)->Material.STONE);
        assertEquals(new Location(w,1.5,80,.5),GroundNavigation.nextStep(new Location(w,.5,80,.5),p->true,p->p.getX(),p-> { Material m=p.getWorld().getBlockAt(p.getBlockX(),79,p.getBlockZ()).getType(); return m==Material.STONE; }));
    }
    @Test void cannotCrossGapToAttractiveIsland() {
        World w=terrain((x,z)->z==0 && (x==0 || x>=2)?Material.STONE:Material.AIR);
        assertNull(GroundNavigation.nextStep(new Location(w,.5,80,.5),p->true,p->p.getX(),p-> { Material m=p.getWorld().getBlockAt(p.getBlockX(),79,p.getBlockZ()).getType(); return m==Material.STONE; }));
    }
    @Test void respectsArenaBoundaryAndAvoidsHazardousSupport() {
        World w=terrain((x,z)->x>0?Material.MAGMA_BLOCK:Material.STONE);
        assertNull(GroundNavigation.nextStep(new Location(w,.5,80,.5),p->p.getX()>=0,p->p.getX(),p-> { Material m=p.getWorld().getBlockAt(p.getBlockX(),79,p.getBlockZ()).getType(); return m==Material.STONE; }));
    }
    @Test void canRouteAroundMissingFloor() {
        World w=terrain((x,z)->x==1 && z==0?Material.AIR:Material.STONE);
        Location next=GroundNavigation.nextStep(new Location(w,.5,80,.5),p->true,p->p.getX(),p-> { Material m=p.getWorld().getBlockAt(p.getBlockX(),79,p.getBlockZ()).getType(); return m==Material.STONE; });
        assertNotNull(next);assertEquals(.5,next.getX());assertEquals(1,Math.abs(next.getZ()-.5));
    }
}
