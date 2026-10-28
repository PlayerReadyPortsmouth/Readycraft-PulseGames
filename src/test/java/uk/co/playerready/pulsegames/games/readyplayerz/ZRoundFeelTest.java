package uk.co.playerready.pulsegames.games.readyplayerz;
import java.util.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ZRoundFeelTest {
    @Test void chainPitchRisesAndResetsAfterTwoSeconds() {
        var game=mock(GameInstance.class); when(game.arena()).thenReturn(ReadyPlayerZBotTest.arena());
        var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        var z=new ReadyPlayerZGame(game);long[] now={10000};z.clock=()->now[0];
        z.awardPoints(p,10,true);now[0]+=1500;z.awardPoints(p,10,true);
        verify(p).playSound(nullable(org.bukkit.Location.class),anyString(),eq(.6f),eq(1.12f));
        now[0]+=2001;z.awardPoints(p,10,true);
        verify(p,times(2)).playSound(nullable(org.bukkit.Location.class),anyString(),eq(.6f),eq(1f));
        verify(game,times(3)).addScore(p,10);
    }
    @Test void lastZombieGlowsOnlyAfterTwentySecondsAndNoPendingSpawns() throws Exception {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS); when(game.arena()).thenReturn(ReadyPlayerZBotTest.arena());
        when(game.state()).thenReturn(GameState.RUNNING);when(game.mode().id()).thenReturn("classic");
        var z=new ReadyPlayerZGame(game);var enemy=mock(Zombie.class);UUID id=UUID.randomUUID();
        when(game.world().getEntity(id)).thenReturn(enemy);
        var f=ReadyPlayerZGame.class.getDeclaredField("zombies");f.setAccessible(true);((Set<UUID>)f.get(z)).add(id);
        f=ReadyPlayerZGame.class.getDeclaredField("roundActive");f.setAccessible(true);f.set(z,true);
        f=ReadyPlayerZGame.class.getDeclaredField("pendingSpawns");f.setAccessible(true);f.set(z,1);
        for(int i=0;i<25;i++)z.onSecond(i);verify(enemy,never()).setGlowing(true);
        f.set(z,0);for(int i=0;i<19;i++)z.onSecond(i);verify(enemy,never()).setGlowing(true);
        z.onSecond(20);verify(enemy).setGlowing(true);
    }
    @Test void twoFriendlyDropsHaveDistinctWeights() {
        var random=mock(java.util.random.RandomGenerator.class);when(random.nextDouble()).thenReturn(0d);
        when(random.nextInt(100)).thenReturn(84);assertEquals("team-shield",ZCombatBalance.drop(1,-100,0,0,.03,random));
        when(random.nextInt(100)).thenReturn(92);assertEquals("second-wind",ZCombatBalance.drop(1,-100,0,0,.03,random));
    }
}
