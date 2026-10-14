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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=GameState.class, names={"WAITING", "COUNTDOWN"})
    void lastExplicitExitImmediatelyRetiresQueueAndWorld(GameState state) throws Exception {
        var p=mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var type=mock(GameType.class);var logic=mock(MiniGame.class);
        when(type.createLogic(any())).thenReturn(logic);
        var arena=mock(Arena.class);when(arena.id()).thenReturn("wharf");
        var game=new GameInstance(p,type,GameMode.teams("duos","Doubles",4,4,2),arena);
        var world=mock(World.class);set(game,"world",world);set(game,"state",state);
        var player=mock(org.bukkit.entity.Player.class);var id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        members(game).add(id);
        game.remove(player,true);
        assertEquals(GameState.RESETTING,game.state());assertFalse(game.joinable(1));
        verify(logic).onCleanup();verify(p.worlds()).unloadAndDelete(world);verify(p.instances()).remove(game);
        game.cleanup();verify(p.worlds(),times(1)).unloadAndDelete(world);
    }
    @Test void remainingHumanOrReservedSeatKeepsQueueAlive() throws Exception {
        var p=mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var type=mock(GameType.class);when(type.createLogic(any())).thenReturn(mock(MiniGame.class));
        var arena=mock(Arena.class);when(arena.id()).thenReturn("wharf");
        var game=new GameInstance(p,type,GameMode.teams("duos","Doubles",4,4,2),arena);
        set(game,"state",GameState.WAITING);
        var player=mock(org.bukkit.entity.Player.class);var id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        var reserved=UUID.randomUUID();members(game).addAll(List.of(id,reserved));
        // An offline player's reserved UUID remains in participants during the grace period.
        when(p.getServer().getPlayer(reserved)).thenReturn(null);
        game.remove(player,true);
        assertEquals(GameState.WAITING,game.state());verify(p.instances(),never()).remove(game);
        var expired=mock(org.bukkit.entity.Player.class);when(expired.getUniqueId()).thenReturn(reserved);
        game.remove(expired,false);
        assertEquals(GameState.RESETTING,game.state());verify(p.instances()).remove(game);
    }
    @Test void disconnectSuspensionDoesNotRetireReservedQueue() throws Exception {
        var p=mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var type=mock(GameType.class);when(type.createLogic(any())).thenReturn(mock(MiniGame.class));
        var arena=mock(Arena.class);when(arena.id()).thenReturn("wharf");
        var game=new GameInstance(p,type,GameMode.teams("duos","Doubles",4,4,2),arena);
        set(game,"state",GameState.WAITING);
        var player=mock(org.bukkit.entity.Player.class);var id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        members(game).add(id);game.suspendWaiting(player);
        assertEquals(1,game.humanCount());assertEquals(GameState.WAITING,game.state());
        verify(p.instances(),never()).remove(game);
    }
    private static void set(GameInstance game,String name,Object value) throws Exception {
        var field=GameInstance.class.getDeclaredField(name);field.setAccessible(true);field.set(game,value);
    }
    @SuppressWarnings("unchecked") private static Set<UUID> members(GameInstance game) throws Exception {
        var field=GameInstance.class.getDeclaredField("participants");field.setAccessible(true);return (Set<UUID>)field.get(game);
    }
}
