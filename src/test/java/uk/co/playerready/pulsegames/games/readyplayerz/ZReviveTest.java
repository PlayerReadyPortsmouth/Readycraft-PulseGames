package uk.co.playerready.pulsegames.games.readyplayerz;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import uk.co.playerready.pulsegames.core.game.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ZReviveTest {
    GameInstance game; ReadyPlayerZGame logic; Player patient, helper; World world;
    @BeforeEach void setup() {
        game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(game.arena()).thenReturn(ReadyPlayerZBotTest.arena());
        when(game.state()).thenReturn(GameState.RUNNING);
        world = game.world();
        patient = player(); helper = player();
        when(game.alivePlayers()).thenReturn(List.of(patient, helper));
        when(game.everyone()).thenReturn(List.of(patient, helper));
        when(helper.isSneaking()).thenReturn(true);
        when(helper.hasLineOfSight(patient)).thenReturn(true);
        logic = spy(new ReadyPlayerZGame(game));
        doNothing().when(logic).applyLegacyCombat(any());
        doReturn(20d).when(logic).maxHealth(any());
        doNothing().when(logic).clearDarkness(any());
        doNothing().when(logic).applyDownState(any());
        doNothing().when(logic).clearDownState(any());
        logic.onDeath(patient, null);
    }
    Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(game.player(p.getUniqueId())).thenReturn(p);
        when(game.isActive(p)).thenReturn(true);
        return p;
    }
    @Test void downStaysAtSpotAndEightSecondsPaysOneRescuer() {
        verify(patient, never()).teleport(any(Location.class));
        verify(patient, never()).setGameMode(org.bukkit.GameMode.SPECTATOR);
        assertFalse(logic.acceptsDeath(patient));
        for (int i=0;i<7;i++) logic.tickRevives();
        verify(game, never()).addScore(any(), anyInt());
        logic.tickRevives(); logic.tickRevives();
        verify(patient).setHealth(10); verify(game).addScore(helper, 50);
        assertTrue(logic.acceptsDeath(patient));
    }
    @Test void MovingAwayResetsProgress() {
        for (int i=0;i<7;i++) logic.tickRevives();
        when(helper.getLocation()).thenReturn(new Location(world, 3, 64, 0));
        logic.tickRevives();
        when(helper.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        logic.tickRevives(); verify(game, never()).addScore(any(), anyInt());
    }
    @Test void bleedOutAndQuitCannotLeaveAnAllDownSquadRunning() throws Exception {
        when(helper.isSneaking()).thenReturn(false);
        var ticks = ReadyPlayerZGame.class.getDeclaredField("elapsedTicks"); ticks.setAccessible(true); ticks.set(logic,900L);
        logic.tickRevives(); verify(patient).setGameMode(org.bukkit.GameMode.SPECTATOR);
        when(game.alivePlayers()).thenReturn(List.of(patient));
        logic.onQuit(helper); verify(game).end(List.of(), "overrun");
    }
    @Test void soloGetsOnlyOneRecovery() throws Exception {
        var solo = ReadyPlayerZGame.class.getDeclaredField("soloSelfRevive"); solo.setAccessible(true); solo.set(logic,true);
        when(game.alivePlayers()).thenReturn(List.of(patient));
        var ticks = ReadyPlayerZGame.class.getDeclaredField("elapsedTicks"); ticks.setAccessible(true); ticks.set(logic,160L);
        logic.tickRevives(); assertTrue(logic.acceptsDeath(patient));
        logic.onDeath(patient,null); verify(game).end(List.of(),"overrun");
    }
}
