package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.team.GameTeam;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedwarsBotLifecycleTest {
    static final class Fixture {
        final GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS); final World world=mock(World.class);
        final Player bot=mock(Player.class); final Block bed=mock(Block.class); final BedwarsGame logic=new BedwarsGame(game);
        final Cuboid region=new Cuboid(0,64,2,0,64,3); final Map<Integer,Boolean> alive;
        final AtomicReference<Material> type=new AtomicReference<>(Material.RED_BED);
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            when(game.world()).thenReturn(world); when(game.isBot(bot)).thenReturn(true); when(game.teamOf(bot)).thenReturn(new GameTeam(1));
            when(bot.getUniqueId()).thenReturn(UUID.randomUUID()); when(bot.getEyeLocation()).thenReturn(new Location(world,.5,65.62,.5));
            when(bot.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(bed.getType()).thenAnswer(i->type.get()); when(bed.getLocation()).thenReturn(new Location(world,0,64,2));
            when(bed.getBreakSpeed(bot)).thenReturn(.1f);
            when(world.rayTraceBlocks(any(Location.class),any(org.bukkit.util.Vector.class),eq(4.5),eq(FluidCollisionMode.NEVER),eq(true)))
                    .thenReturn(new RayTraceResult(new org.bukkit.util.Vector(.5,64.5,2),bed,org.bukkit.block.BlockFace.NORTH));
            var beds=BedwarsGame.class.getDeclaredField("bedRegions"); beds.setAccessible(true); beds.set(logic,List.of(region));
            var state=BedwarsGame.class.getDeclaredField("bedAlive"); state.setAccessible(true); alive=(Map<Integer,Boolean>)state.get(logic); alive.put(0,true);
        }
        boolean mine(long tick) throws Exception {
            var method=BedwarsGame.class.getDeclaredMethod("mineVisibleDefence",Player.class,Cuboid.class,long.class); method.setAccessible(true);
            return (boolean)method.invoke(logic,bot,region,tick);
        }
    }
    @Test void botPermissionCheckCannotDestroyBedBeforeBreakSucceeds() throws Exception {
        var f=new Fixture(); assertTrue(f.logic.canBreak(f.bot,f.bed)); assertTrue(f.alive.get(0));
        f.mine(0); f.mine(5); verify(f.bot,never()).breakBlock(any());
        f.mine(10); verify(f.bot).breakBlock(f.bed); assertTrue(f.alive.get(0));
    }
    @Test void successfulTimedBreakCommitsBedLossAndOwnBedIsForbidden() throws Exception {
        var f=new Fixture(); when(f.bot.breakBlock(f.bed)).thenAnswer(i->{f.type.set(Material.AIR);return true;});
        f.mine(0); f.mine(5); f.mine(10); assertFalse(f.alive.get(0));
        var own=new Fixture(); when(own.game.teamOf(own.bot)).thenReturn(new GameTeam(0));
        assertFalse(own.logic.canBreak(own.bot,own.bed)); assertTrue(own.alive.get(0));
    }
    @Test void longIdleIntervalDoesNotCountAsContinuousMining() throws Exception {
        var f=new Fixture(); f.mine(0); f.mine(500);
        verify(f.bot,never()).breakBlock(any()); assertTrue(f.alive.get(0));
    }
}
