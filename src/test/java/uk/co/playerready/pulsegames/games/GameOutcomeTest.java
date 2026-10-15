package uk.co.playerready.pulsegames.games;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;
import uk.co.playerready.pulsegames.games.duels.DuelsGame;
import uk.co.playerready.pulsegames.games.elytra.ElytraRacingGame;
import uk.co.playerready.pulsegames.games.karts.PulseKartsGame;
import uk.co.playerready.pulsegames.games.readyplayerz.ReadyPlayerZGame;
import uk.co.playerready.pulsegames.games.villagerdefense.VillagerDefenseGame;

import java.util.List;
import java.util.function.Function;

import static org.mockito.Mockito.*;

class GameOutcomeTest {
    @Test void unfinishedObjectivesDoNotAwardVictories() {
        List<Function<GameInstance, MiniGame>> factories = List.of(
                ElytraRacingGame::new, PulseKartsGame::new,
                ReadyPlayerZGame::new, VillagerDefenseGame::new, DuelsGame::new);
        for (var factory : factories) {
            GameInstance instance = mock(GameInstance.class, RETURNS_DEEP_STUBS);
            when(instance.state()).thenReturn(uk.co.playerready.pulsegames.core.game.GameState.RUNNING);
            when(instance.alivePlayers()).thenReturn(List.of(mock(Player.class), mock(Player.class)));
            factory.apply(instance).onTimeUp();
            verify(instance).end(eq(List.of()), startsWith("time"));
        }
    }

    @Test void duelTimeoutRewardsOnlyTheRoundLeader() {
        GameInstance instance = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        Player winner = mock(Player.class), loser = mock(Player.class);
        when(winner.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(loser.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(instance.alivePlayers()).thenReturn(List.of(winner, loser));
        when(instance.teamOf(winner)).thenReturn(null);
        when(instance.arena().settings().getInt("rounds-to-win", 2)).thenReturn(2);
        DuelsGame logic = new DuelsGame(instance);
        logic.onDeath(loser, winner);
        logic.onTimeUp();
        verify(instance).end(List.of(winner), "time");
    }

    @Test void survivalTimeoutStillRewardsSurvivors() {
        GameInstance instance = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        List<Player> survivors = List.of(mock(Player.class));
        when(instance.alivePlayers()).thenReturn(survivors);
        new MiniGame(instance) {}.onTimeUp();
        verify(instance).end(survivors, "time");
    }
}
