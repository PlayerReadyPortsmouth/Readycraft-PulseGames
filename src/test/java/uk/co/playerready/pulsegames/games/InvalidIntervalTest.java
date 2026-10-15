package uk.co.playerready.pulsegames.games;

import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.games.parkour.ParkourGame;
import uk.co.playerready.pulsegames.games.luckypillars.LuckyPillarsGame;
import uk.co.playerready.pulsegames.games.volcano.VolcanoGame;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvalidIntervalTest {
    private GameInstance game(String mode,String setting,int value) {
        var settings=new MemoryConfiguration();settings.set(setting,value);
        var game=mock(GameInstance.class);when(game.mode()).thenReturn(GameMode.ffa(mode,mode,2,16));
        when(game.arena()).thenReturn(new Arena("test","test","Test","world",List.of(),2,16,"0,64,0","0,64,0",List.of("0,64,0"),Map.of("arena",new Cuboid(0,64,0,0,64,0)),settings));
        when(game.alivePlayers()).thenReturn(List.of());return game;
    }
    @Test void parkourAndLuckyPillarsCannotDivideByZero() {
        for(int value:new int[]{0,-10}) {
            assertDoesNotThrow(()->new ParkourGame(game("elimination","elim-interval",value)).onSecond(45));
            assertDoesNotThrow(()->new LuckyPillarsGame(game("classic","drop-interval",value)).onSecond(10));
        }
    }
    @Test void volcanoStillProcessesItsFloodTickWithMalformedInterval() {
        for(int value:new int[]{0,-10}) {
            var game=game("classic","lava-interval",value);
            World world=mock(World.class,RETURNS_DEEP_STUBS);when(game.world()).thenReturn(world);
            var solid=mock(org.bukkit.Material.class);when(solid.isAir()).thenReturn(false);
            when(world.getBlockAt(0,64,0).getType()).thenReturn(solid);
            clearInvocations(world);
            var logic=new VolcanoGame(game);logic.onStart();
            assertDoesNotThrow(()->logic.onSecond(16));
            verify(world).getBlockAt(0,64,0);
        }
    }
}
