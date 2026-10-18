package uk.co.playerready.pulsegames.games.karts;

import java.util.*;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.content.PulseContentBridge;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class KartControllerFailureTest {
    @Test void cleanupFailureCannotStrandClosedControllerInRunningState() throws Exception {
        for (Throwable failure : List.of(new IllegalStateException("cleanup"),new LinkageError("cleanup"))) {
            GameInstance game=mock(GameInstance.class,RETURNS_DEEP_STUBS);
            when(game.world()).thenReturn(mock(World.class));when(game.state()).thenReturn(GameState.RUNNING);
            when(game.alivePlayers()).thenReturn(List.of());when(game.everyone()).thenReturn(List.of());
            List<Runnable> tasks=new ArrayList<>();
            doAnswer(i->{tasks.add(i.getArgument(2));return null;}).when(game).runRepeating(anyLong(),anyLong(),any(Runnable.class));
            var logic=new PulseKartsGame(game);logic.onStart();
            Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(game.isActive(player)).thenReturn(true);
            ArmorStand seat=mock(ArmorStand.class);when(seat.isValid()).thenReturn(false);
            var kart=new PulseKartsGame.Kart(player,seat,mock(AutoCloseable.class),new KartPhysics.State(0,64,0,0,0,0,true));
            var field=PulseKartsGame.class.getDeclaredField("karts");field.setAccessible(true);
            ((Map<UUID,PulseKartsGame.Kart>)field.get(logic)).put(player.getUniqueId(),kart);
            var visual=mock(PulseContentBridge.WorldVisual.class);doThrow(failure).when(visual).close();
            field=PulseKartsGame.class.getDeclaredField("boxVisuals");field.setAccessible(true);
            ((Map<Cuboid,PulseContentBridge.WorldVisual>)field.get(logic)).put(new Cuboid(0,64,0,0,64,0),visual);
            assertEquals(1,tasks.size());assertDoesNotThrow(tasks.getFirst()::run);
            verify(game).end(List.of(),"kart-controller-error");verify(seat).remove();
            tasks.getFirst().run();verify(game,times(1)).end(List.of(),"kart-controller-error");
        }
    }
}
