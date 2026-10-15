package uk.co.playerready.pulsegames.games;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.games.prophunt.PropHuntGame;
import uk.co.playerready.pulsegames.games.readyplayerz.ReadyPlayerZGame;
import uk.co.playerready.pulsegames.games.villagerdefense.VillagerDefenseGame;
import uk.co.playerready.pulsegames.games.quake.QuakeGame;
import uk.co.playerready.pulsegames.games.karts.PulseKartsGame;
import uk.co.playerready.pulsegames.games.duels.DuelsGame;
import uk.co.playerready.pulsegames.games.partygames.PartyGamesGame;
import uk.co.playerready.pulsegames.games.blockparty.BlockPartyGame;
import uk.co.playerready.pulsegames.games.buildbattle.BuildBattleGame;

class PhaseCountdownTest {
    private void field(Object target,String name,Object value) throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private void phase(Object target,String name) throws Exception {
        var f=target.getClass().getDeclaredField("phase");f.setAccessible(true);
        for(Object value:f.getType().getEnumConstants())if(value.toString().equals(name)){f.set(target,value);return;}
        fail("Unknown phase");
    }
    @Test void seekersTimerDisappearsAtRelease() throws Exception {
        var logic=new PropHuntGame(mock(GameInstance.class));
        field(logic,"hidePhase",7);assertEquals("Seekers released in: 7s",logic.phaseCountdown());
        field(logic,"hidePhase",0);assertNull(logic.phaseCountdown());
    }
    @Test void waveTimersDoNotShowDuringActiveCombat() throws Exception {
        var game=mock(GameInstance.class);
        when(game.arena()).thenReturn(new Arena("z","readyplayerz","Z","template",List.of(),1,4,
                "0,64,0","0,64,0",List.of("0,64,0"),Map.of(),new MemoryConfiguration()));
        var zombies=new ReadyPlayerZGame(game);assertEquals("Next wave: 8s",zombies.phaseCountdown());
        field(zombies,"roundActive",true);assertNull(zombies.phaseCountdown());
        var defense=new VillagerDefenseGame(game);assertEquals("Next wave: 5s",defense.phaseCountdown());
        field(defense,"waveActive",true);assertNull(defense.phaseCountdown());
    }
    @Test void selectionUsesActualInstanceCountdownAndStopsAtZero() {
        var game=mock(GameInstance.class);var quake=new QuakeGame(game);var karts=new PulseKartsGame(game);
        when(game.startCountdownSeconds()).thenReturn(12);assertEquals("Choose loadout: 12s",quake.phaseCountdown());assertEquals("Choose kart: 12s",karts.phaseCountdown());
        when(game.startCountdownSeconds()).thenReturn(0);assertNull(quake.phaseCountdown());assertNull(karts.phaseCountdown());
    }
    @Test void partyBuildingAndFloorTimersHavePhaseSpecificLabels() throws Exception {
        var game=mock(GameInstance.class);
        assertEquals("Next game: 5s",new PartyGamesGame(game).phaseCountdown());
        var floor=new BlockPartyGame(game);field(floor,"phaseTimer",2);phase(floor,"STAND");assertEquals("Floor drops: 2s",floor.phaseCountdown());
        phase(floor,"CLEARED");assertEquals("Floor returns: 2s",floor.phaseCountdown());
        var build=new BuildBattleGame(game);field(build,"phaseTimer",9);phase(build,"VOTE");assertEquals("Vote ends: 9s",build.phaseCountdown());
        phase(build,"DONE");assertNull(build.phaseCountdown());
    }
    @Test void duelsDisplayTicksPreserveSixtyTickRestartAndCannotReviveAfterCleanup() {
        var game=mock(GameInstance.class,RETURNS_DEEP_STUBS);when(game.state()).thenReturn(GameState.RUNNING);
        when(game.mode()).thenReturn(GameMode.ffa("classic","Classic",2,2));when(game.arena().settings().getInt("rounds-to-win",2)).thenReturn(2);
        var a=mock(Player.class);var b=mock(Player.class);when(a.getUniqueId()).thenReturn(UUID.randomUUID());when(b.getUniqueId()).thenReturn(UUID.randomUUID());when(b.getName()).thenReturn("Winner");
        when(game.alivePlayers()).thenReturn(List.of(a,b));when(game.players()).thenReturn(List.of(a,b));when(game.teamOf(b)).thenReturn(null);
        Map<Long,Runnable> callbacks=new HashMap<>();doAnswer(inv->{callbacks.put(inv.getArgument(0),inv.getArgument(1));return null;}).when(game).runLater(anyLong(),any());
        var logic=new DuelsGame(game);logic.onDeath(a,b);assertEquals("Next round: 3s",logic.phaseCountdown());
        callbacks.get(20L).run();assertEquals("Next round: 2s",logic.phaseCountdown());callbacks.get(40L).run();assertEquals("Next round: 1s",logic.phaseCountdown());assertTrue(callbacks.containsKey(60L));
        when(game.world()).thenReturn(null);logic.onCleanup();callbacks.get(20L).run();assertNull(logic.phaseCountdown());
        when(game.state()).thenReturn(GameState.ENDING);callbacks.get(60L).run();assertNull(logic.phaseCountdown());
    }
}
