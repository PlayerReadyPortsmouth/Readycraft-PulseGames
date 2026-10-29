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
    @Test void deepSearchRoutesRoundAWallOfHolesThatTheShortSearchCannot() {
        // A wall of missing floor at x=1 with a single gap at z=3: the goal at x=4 is 7 steps round.
        World w=terrain((x,z)->x==1 && z!=3 && z>=-2?Material.AIR:Material.STONE);
        java.util.function.Predicate<Location> stone=p->w.getBlockAt(p.getBlockX(),79,p.getBlockZ()).getType()==Material.STONE;
        java.util.function.Predicate<Location> inArena=p->p.getBlockZ()>=-2 && p.getBlockZ()<=3 && p.getBlockX()<=4;
        Location from=new Location(w,.5,80,.5);
        assertNull(GroundNavigation.nextStep(from,inArena,p->p.getX(),stone));
        Location next=GroundNavigation.nextStep(from,inArena,p->p.getX(),stone,6,96);
        assertNotNull(next);assertEquals(1,next.getBlockZ()); // heads for the gap, not into the hole
    }
}
