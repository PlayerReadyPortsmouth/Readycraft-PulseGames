package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompetitiveEligibilitySnapshotTest {
    /** Stop after eligibility capture, before Paper registry-dependent player setup. */
    private static final class SpawnSetupReached extends RuntimeException {}

    @Test void eligibleStartingRosterRemainsEligibleAfterHumanDeparture() throws Exception {
        GameInstance game = started(6, 6, 12);
        assertTrue(game.ranked());
        Set<UUID> participants = ids(game, "participants"), bots = ids(game, "botIds");
        participants.removeIf(id -> !bots.contains(id));
        assertTrue(game.ranked());
    }
    @Test void practiceStartingRosterCannotGainRewardsWhenBotsDisappear() throws Exception {
        GameInstance game = started(2, 2, 4);
        assertFalse(game.ranked());
        Set<UUID> participants = ids(game, "participants"), bots = ids(game, "botIds");
        participants.removeAll(bots);
        bots.clear();
        assertFalse(game.ranked());
    }
    @Test void roundCannotStartInsidePartyAdmissionCallback() throws Exception {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        GameType type = mock(GameType.class); Arena arena = mock(Arena.class);
        GameInstance game = new GameInstance(plugin, type, GameMode.ffa("solo", "Solo", 2, 4), arena);
        var state = GameInstance.class.getDeclaredField("state"); state.setAccessible(true); state.set(game, GameState.COUNTDOWN);
        when(plugin.playService().admitting(game)).thenReturn(true);
        game.start();
        assertEquals(GameState.COUNTDOWN, game.state());
        verify(plugin, never()).playerState();
    }

    private GameInstance started(int humans, int bots, int capacity) throws Exception {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        GameType type = mock(GameType.class);
        MiniGame logic = mock(MiniGame.class);
        when(type.createLogic(any())).thenReturn(logic);
        Arena arena = mock(Arena.class);
        when(arena.id()).thenReturn("arena");
        GameInstance game = spy(new GameInstance(plugin, type,
                GameMode.ffa("classic", "Classic", 2, capacity), arena));
        var state = GameInstance.class.getDeclaredField("state");
        state.setAccessible(true);
        state.set(game, GameState.WAITING);
        doReturn(capacity).when(game).maxPlayers();
        doReturn(2).when(game).minPlayers();
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < humans + bots; i++) {
            Player player = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            ids(game, "participants").add(id);
            if (i >= humans) ids(game, "botIds").add(id);
            players.add(player);
        }
        doReturn(players).when(game).players();
        var playerState = plugin.playerState();
        doThrow(new SpawnSetupReached()).when(playerState).reset(any());
        assertThrows(SpawnSetupReached.class, game::start);
        return game;
    }
    @SuppressWarnings("unchecked")
    private Set<UUID> ids(GameInstance game, String name) throws Exception {
        var field = GameInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Set<UUID>) field.get(game);
    }
}
