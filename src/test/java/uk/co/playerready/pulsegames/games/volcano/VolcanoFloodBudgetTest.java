package uk.co.playerready.pulsegames.games.volcano;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VolcanoFloodBudgetTest {
    static class Fixture {
        final GameInstance game=mock(GameInstance.class);final World world=mock(World.class);final List<Runnable> scheduled=new ArrayList<>();final VolcanoGame logic;
        Fixture() {
            var settings=new MemoryConfiguration();settings.set("lava-interval",1);
            when(game.arena()).thenReturn(new Arena("v","volcano","V","world",List.of(),2,8,"0,64,0","0,64,0",List.of("0,64,0"),Map.of("arena",new Cuboid(0,64,0,0,64,2499)),settings));
            when(game.mode()).thenReturn(uk.co.playerready.pulsegames.core.game.GameMode.ffa("classic","Classic",2,8));when(game.world()).thenReturn(world);
            Material air=mock(Material.class);when(air.isAir()).thenReturn(true);Block block=mock(Block.class);when(block.getType()).thenReturn(air);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
            doAnswer(i->{scheduled.add(i.getArgument(1));return null;}).when(game).runLater(eq(1L),any(Runnable.class));logic=new VolcanoGame(game);logic.onStart();
        }
    }
    @Test void longThinPlaneUsesAtMost1024ScansPerTickAndVisitsEveryCellOnce() {
        var f=new Fixture();f.logic.onSecond(11);verify(f.world,times(1024)).getBlockAt(anyInt(),eq(64),anyInt());assertEquals(1,f.scheduled.size());
        f.scheduled.removeFirst().run();verify(f.world,times(2048)).getBlockAt(anyInt(),eq(64),anyInt());
        f.scheduled.removeFirst().run();verify(f.world,times(2500)).getBlockAt(anyInt(),eq(64),anyInt());assertTrue(f.scheduled.isEmpty());
        verify(f.world).getBlockAt(0,64,0);verify(f.world).getBlockAt(0,64,1023);verify(f.world).getBlockAt(0,64,1024);verify(f.world).getBlockAt(0,64,2499);
    }
    @Test void staleQueuedFloodCannotWriteAfterCleanupOrNewGeneration() {
        var f=new Fixture();f.logic.onSecond(11);Runnable stale=f.scheduled.getFirst();f.logic.onCleanup();clearInvocations(f.world);stale.run();verifyNoInteractions(f.world);
        f.logic.onStart();stale.run();verifyNoInteractions(f.world);
    }
}
