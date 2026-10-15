package uk.co.playerready.pulsegames.games.readyplayerz;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadyPlayerZBotTest {
    static uk.co.playerready.pulsegames.core.arena.Arena arena(){return new uk.co.playerready.pulsegames.core.arena.Arena("z","readyplayerz","Z","world",List.of(),1,4,"0,64,0","0,64,0",List.of("0,64,0"),Map.of(),new org.bukkit.configuration.MemoryConfiguration());}

    @Test void purchaseChargesNormalPriceAndRejectsInactiveOrPoorPlayers() {
        var game=mock(GameInstance.class);var player=mock(Player.class);var world=mock(World.class);
        when(game.state()).thenReturn(GameState.RUNNING);when(game.world()).thenReturn(world);when(player.getWorld()).thenReturn(world);
        when(game.isActive(player)).thenReturn(true);when(game.score(player)).thenReturn(74);
        when(game.arena()).thenReturn(arena());
        var logic=new ReadyPlayerZGame(game);
        assertFalse(logic.purchase(player,6));verify(game,never()).addScore(any(),anyInt());
        when(game.score(player)).thenReturn(75);assertTrue(logic.purchase(player,6));
        verify(game).addScore(player,-75);verify(player).setHealth(20);
        when(game.isActive(player)).thenReturn(false);assertFalse(logic.purchase(player,6));
        verify(game,times(1)).addScore(any(),anyInt());
    }
    @Test void botOnlySpendsEarnedPointsAndPrioritisesHealing() {
        var game=mock(GameInstance.class);var bot=mock(Player.class);var world=mock(World.class);
        when(game.state()).thenReturn(GameState.RUNNING);when(game.world()).thenReturn(world);when(bot.getWorld()).thenReturn(world);
        when(game.isBot(bot)).thenReturn(true);when(game.isActive(bot)).thenReturn(true);
        when(bot.getHealth()).thenReturn(8.0);when(game.score(bot)).thenReturn(75);
        when(game.arena()).thenReturn(arena());
        var logic=new ReadyPlayerZGame(game);logic.botTick(bot);
        verify(game).addScore(bot,-75);verify(bot).setHealth(20);
    }
    @Test void downedNpcRevivesWithoutOnlinePlayerLookup() throws Exception {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);var bot=mock(Player.class);
        UUID id=UUID.randomUUID();when(bot.getUniqueId()).thenReturn(id);
        when(game.player(id)).thenReturn(bot);when(game.isParticipant(bot)).thenReturn(true);
        when(bot.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(game.arena()).thenReturn(arena());
        var logic=spy(new ReadyPlayerZGame(game));
        doNothing().when(logic).equipBase(bot);
        var field=ReadyPlayerZGame.class.getDeclaredField("downed");field.setAccessible(true);
        ((Set<UUID>)field.get(logic)).add(id);
        // Test the lookup/restoration seam without a live ItemFactory; the runtime probe verifies the base kit.
        var method=ReadyPlayerZGame.class.getDeclaredMethod("reviveDowned");method.setAccessible(true);
        method.invoke(logic);
        verify(logic).equipBase(bot);
        verify(game).player(id);verify(bot).setGameMode(org.bukkit.GameMode.SURVIVAL);
        assertFalse(((Set<UUID>)field.get(logic)).contains(id));
        verify(bot,never()).isOnline();
    }
}
