package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import java.util.Set;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PlayerReleaseTest {
    private void verifyRelease(boolean cleanup) throws Exception {
        var plugin=mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        var type=mock(GameType.class);var logic=mock(MiniGame.class);
        when(type.createLogic(any())).thenReturn(logic);
        var arena=mock(Arena.class);when(arena.id()).thenReturn("map");
        var game=new GameInstance(plugin,type,GameMode.ffa("solo","Solo",2,8),arena);
        var player=mock(Player.class);var id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(player.isOnline()).thenReturn(true);
        when(plugin.getServer().getPlayer(id)).thenReturn(player);
        var members=GameInstance.class.getDeclaredField("participants");members.setAccessible(true);
        @SuppressWarnings("unchecked") var participants=(Set<UUID>)members.get(game);participants.add(id);
        var state=GameInstance.class.getDeclaredField("state");state.setAccessible(true);state.set(game,GameState.WAITING);
        var combat=plugin.combat();var sidebar=plugin.sidebar();
        var content=plugin.content();
        doThrow(new IllegalStateException("wardrobe cleanup failed")).when(content).clearWardrobeContext(player);
        doThrow(new IllegalStateException("combat cleanup failed")).when(combat).leave(player);
        doThrow(new IllegalStateException("sidebar cleanup failed")).when(sidebar).clear(player);
        if(cleanup)game.cleanup();else game.remove(player,true);
        verify(plugin.instances()).unindex(player);
        verify(plugin.content()).clearWardrobeContext(player);
        verify(plugin.playerState()).restore(player);
        verify(plugin.lobby()).sendToLobby(player);
        if(cleanup)verify(plugin.instances()).remove(game);
    }
    @Test void cleanupContinuesAfterCombatAndSidebarFailures() throws Exception {verifyRelease(true);}
    @Test void leaveContinuesAfterCombatAndSidebarFailures() throws Exception {verifyRelease(false);}
}
