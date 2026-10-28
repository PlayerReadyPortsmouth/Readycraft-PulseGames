package uk.co.playerready.pulsegames.games.dodgeball;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.game.GameState;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DodgeballSuddenDeathTest {
    GameInstance game;
    DodgeballGame logic;
    Player a, b, c;

    Player player(String name) {
        Player p = mock(Player.class);
        when(p.getName()).thenReturn(name);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }

    @BeforeEach void setup() {
        game = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        a = player("A"); b = player("B"); c = player("C");
        when(game.state()).thenReturn(GameState.RUNNING);
        when(game.teamOf(any())).thenReturn(null);
        when(game.alivePlayers()).thenReturn(List.of(a, b));
        logic = new DodgeballGame(game);
    }

    @Test void levelOnLivesGoesToSuddenDeathOnOneLifeAndExtendsTheClock() {
        int before = logic.timeLimitSeconds();
        logic.onTimeUp();
        verify(game, never()).end(any(), any());
        verify(game, never()).eliminate(any());
        assertTrue(logic.timeLimitSeconds() > before);
        verify(a).showTitle(any(net.kyori.adventure.title.Title.class));
        assertTrue(logic.sidebar(a).stream().anyMatch(l -> l.contains("SUDDEN DEATH")));
        assertTrue(logic.sidebar(a).getFirst().contains("1"), "everyone is down to one life");
    }

    @Test void secondTimeoutAfterSuddenDeathSharesTheWinBetweenFinalistsNotABlanketDraw() {
        logic.onTimeUp();
        logic.onTimeUp();
        verify(game).end(List.of(a, b), "time-draw");
    }

    @Test void fewerLivesAtTheClockMeansOut() throws Exception {
        var lives = DodgeballGame.class.getDeclaredField("lives");
        lives.setAccessible(true);
        @SuppressWarnings("unchecked") var map = (Map<UUID, Integer>) lives.get(logic);
        map.put(a.getUniqueId(), 3);
        map.put(b.getUniqueId(), 1);
        logic.onTimeUp();
        verify(game).eliminate(b);
        verify(game, never()).eliminate(a);
    }

    @Test void leadingSidesCountsTeamsTogether() {
        var lives = new HashMap<UUID, Integer>();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        lives.put(p1, 1); lives.put(p2, 3); lives.put(p3, 3);
        var sides = Map.<UUID, Object>of(p1, "red", p2, "red", p3, "blue");
        // red has 4 lives between two players, blue has 3: red leads and keeps both players.
        assertEquals(Set.of(p1, p2), DodgeballGame.leadingSides(lives, List.of(p1, p2, p3), sides::get));
        // free-for-all: each player is their own side, so the two on 3 tie.
        assertEquals(Set.of(p2, p3), DodgeballGame.leadingSides(lives, List.of(p1, p2, p3), id -> id));
    }

    @Test void roundsRankingPutsMoreLivesFirstThenLastOut() {
        UUID x = UUID.randomUUID(), y = UUID.randomUUID(), z = UUID.randomUUID(), w = UUID.randomUUID();
        var lives = Map.of(x, 3, y, 1, z, 3);
        var tiers = DodgeballGame.livesTiers(lives, List.of(List.of(x, y, z), List.of(w)), Set.of(x, y, z));
        assertEquals(List.of(List.of(x, z), List.of(y), List.of(w)).stream().map(Set::copyOf).toList(),
                tiers.stream().map(Set::copyOf).toList());
    }
}
