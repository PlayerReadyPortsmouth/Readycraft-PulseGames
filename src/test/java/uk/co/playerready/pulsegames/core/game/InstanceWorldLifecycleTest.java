package uk.co.playerready.pulsegames.core.game;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.world.WorldService;
import java.util.*;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class InstanceWorldLifecycleTest {
    @Test void sameMapCanLoadTwiceIntoDifferentWorldsAndLogic() {
        PulseGamesPlugin p=mock(PulseGamesPlugin.class);WorldService worlds=mock(WorldService.class);when(p.worlds()).thenReturn(worlds);
        Arena arena=mock(Arena.class);when(arena.id()).thenReturn("courtyard");when(arena.worldTemplate()).thenReturn("courtyard");
        GameType type=mock(GameType.class);when(type.createLogic(any())).thenAnswer(i->mock(MiniGame.class));
        GameMode mode=GameMode.teams("duos","Doubles",4,4,2);
        var first=new GameInstance(p,type,mode,arena);var second=new GameInstance(p,type,mode,arena);
        assertNotEquals(first.id(),second.id());assertNotSame(first.logic(),second.logic());
        first.init();second.init();
        verify(worlds).cloneAndLoad(eq("courtyard"),eq(first.id()),any(),any());
        verify(worlds).cloneAndLoad(eq("courtyard"),eq(second.id()),any(),any());
    }
    @Test void lateCloneAfterCancellationIsUnloadedInsteadOfResurrectingGame() throws Exception {
        PulseGamesPlugin p=mock(PulseGamesPlugin.class);WorldService worlds=mock(WorldService.class);when(p.worlds()).thenReturn(worlds);
        Arena arena=mock(Arena.class);when(arena.id()).thenReturn("map");when(arena.worldTemplate()).thenReturn("map");
        GameType type=mock(GameType.class);MiniGame logic=mock(MiniGame.class);when(type.createLogic(any())).thenReturn(logic);
        var game=new GameInstance(p,type,GameMode.teams("duos","Doubles",4,4,2),arena);
        List<Consumer<World>> callbacks=new ArrayList<>();doAnswer(i->{callbacks.add(i.getArgument(2));return null;}).when(worlds).cloneAndLoad(any(),any(),any(),any());
        game.init();var field=GameInstance.class.getDeclaredField("state");field.setAccessible(true);field.set(game,GameState.RESETTING);
        World loaded=mock(World.class);callbacks.get(0).accept(loaded);
        verify(worlds).unloadAndDelete(loaded);verify(logic,never()).onWorldReady();assertEquals(GameState.RESETTING,game.state());
    }
}
