package uk.co.playerready.pulsegames.games.common;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.MiniGame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RoundControllerTest {
    GameInstance game = mock(GameInstance.class);
    World world = mock(World.class);
    Player a = player("A"), b = player("B"), c = player("C");
    List<Player> all = List.of(a, b, c);
    List<Integer> begun = new ArrayList<>();
    RoundController rounds;

    Player player(String name) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn(name);
        when(p.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(p.getActivePotionEffects()).thenReturn(List.of());
        return p;
    }

    @BeforeEach void setup() {
        var settings = new MemoryConfiguration();
        Arena arena = mock(Arena.class);
        when(arena.settings()).thenReturn(settings);
        when(arena.spectator(world)).thenReturn(new Location(world, 0, 100, 0));
        when(game.arena()).thenReturn(arena);
        when(game.world()).thenReturn(world);
        when(game.players()).thenReturn(all);
        when(game.alivePlayers()).thenReturn(all);
        when(game.everyone()).thenReturn(all);
        when(game.logic()).thenReturn(mock(MiniGame.class));
        when(game.spawnFor(any())).thenReturn(new Location(world, 0, 64, 0));
        for (Player p : all) {
            when(game.player(p.getUniqueId())).thenReturn(p);
            when(p.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        }
        rounds = new RoundController(game, begun::add, null, 75, "Last one standing");
        rounds.start();
    }

    @Test void roundEndsWhenOneRemainsAndPaysPlacementPoints() {
        assertEquals(List.of(1), begun);
        assertTrue(rounds.knockOut(a));
        assertTrue(rounds.active());
        assertTrue(rounds.knockOut(b));
        assertFalse(rounds.active());
        verify(game).addScore(c, 3);
        verify(game).addScore(b, 2);
        verify(game).addScore(a, 1);
        verify(a, atLeastOnce()).setGameMode(GameMode.SPECTATOR);
    }

    @Test void fiveSecondIntermissionThenNextRoundResetsEveryone() {
        rounds.knockOut(a); rounds.knockOut(b);
        for (int i = 0; i < 4; i++) rounds.tick();
        assertFalse(rounds.active());
        assertEquals(List.of(1), begun);
        rounds.tick();
        assertTrue(rounds.active());
        assertEquals(List.of(1, 2), begun);
        assertEquals(2, rounds.round());
        verify(a).teleport(new Location(world, 0, 64, 0));
        verify(a, atLeastOnce()).getInventory();
        assertEquals(3, rounds.contestants().size());
    }

    @Test void clockRunsOutTiesSurvivorsAndMatchGoesToPointsAfterThreeRounds() {
        for (int round = 1; round <= 3; round++) {
            assertEquals(round, rounds.round());
            rounds.knockOut(a);                       // a is first out every round; b and c outlast the clock
            for (int s = 0; s < 75; s++) rounds.tick();
            assertFalse(rounds.active());
            verify(game, never()).end(any(), any());
            for (int s = 0; s < (round < 3 ? 5 : 3); s++) rounds.tick();
        }
        // b and c each scored 3 points a round, a scored 1.
        verify(game).end(argThat(winners -> winners.size() == 2 && winners.contains(b) && winners.contains(c)), eq("rounds"));
        verify(game, times(3)).addScore(b, 3);
        verify(game, times(3)).addScore(a, 1);
    }
}
