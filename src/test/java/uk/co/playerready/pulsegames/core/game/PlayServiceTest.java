package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.lobby.LobbyService;
import uk.co.playerready.pulsegames.core.party.Party;
import uk.co.playerready.pulsegames.core.party.PartyManager;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayServiceTest {
    private PulseGamesPlugin plugin;
    private InstanceManager instances;
    private PartyManager parties;
    private LobbyService lobby;
    private PlayService service;
    private Player leader;
    private GameType type;
    private final GameMode mode = GameMode.ffa("classic", "Classic", 2, 16);
    private final Queue<Runnable> callbacks = new ArrayDeque<>();

    @BeforeEach
    void setup() {
        plugin = mock(PulseGamesPlugin.class);
        instances = mock(InstanceManager.class);
        parties = mock(PartyManager.class);
        lobby = mock(LobbyService.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.instances()).thenReturn(instances);
        when(plugin.parties()).thenReturn(parties);
        when(plugin.lobby()).thenReturn(lobby);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> {
            callbacks.add(invocation.getArgument(1, Runnable.class));
            return null;
        }).when(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(10L));
        leader = player();
        type = mock(GameType.class);
        when(type.displayName()).thenReturn("Test Game");
        service = new PlayService(plugin);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private GameInstance destination(GameState state) {
        GameInstance instance = mock(GameInstance.class, RETURNS_DEEP_STUBS);
        when(instance.state()).thenReturn(state);
        when(instance.joinable(anyInt())).thenReturn(true);
        when(instance.add(any(Player.class))).thenReturn(true);
        when(instance.arena().displayName()).thenReturn("Test Map");
        return instance;
    }

    private Player party() {
        Player member = player();
        Party party = mock(Party.class);
        when(parties.partyOf(leader)).thenReturn(party);
        when(party.isLeader(leader)).thenReturn(true);
        when(party.onlineMembers()).thenReturn(List.of(leader, member));
        return member;
    }

    @Test
    void leavingDuringLoadingCancelsLaterAdmission() {
        GameInstance target = destination(GameState.LOADING);
        when(instances.findOrCreate(type, mode, 1)).thenReturn(target);
        service.join(leader, type, mode);
        service.leave(leader);
        when(target.state()).thenReturn(GameState.WAITING);
        callbacks.remove().run();
        verify(target, never()).add(any());
        verify(lobby).sendToLobby(leader);
    }

    @Test
    void newerQueueSupersedesOlderLoadingCallback() {
        GameInstance first = destination(GameState.LOADING);
        GameInstance second = destination(GameState.WAITING);
        when(instances.findOrCreate(type, mode, 1)).thenReturn(first, second);
        service.join(leader, type, mode);
        service.join(leader, type, mode);
        when(first.state()).thenReturn(GameState.WAITING);
        callbacks.remove().run();
        verify(first, never()).add(any());
        verify(second).add(leader);
    }

    @Test
    void evenUnavailableNewQueueCancelsOldRequest() {
        GameInstance first = destination(GameState.LOADING);
        when(instances.findOrCreate(type, mode, 1)).thenReturn(first).thenReturn(null);
        service.join(leader, type, mode);
        service.join(leader, type, mode);
        when(first.state()).thenReturn(GameState.WAITING);
        callbacks.remove().run();
        verify(first, never()).add(any());
    }

    @Test
    void oneMemberLeavingCancelsEntirePendingParty() {
        Player member = party();
        GameInstance target = destination(GameState.LOADING);
        when(instances.findOrCreate(type, mode, 2)).thenReturn(target);
        service.join(leader, type, mode);
        service.leave(member);
        when(target.state()).thenReturn(GameState.WAITING);
        callbacks.remove().run();
        verify(target, never()).add(any());
    }

    @Test
    void offlineMemberPreventsPartialPartyAdmission() {
        Player member = party();
        GameInstance target = destination(GameState.LOADING);
        when(instances.findOrCreate(type, mode, 2)).thenReturn(target);
        service.join(leader, type, mode);
        when(member.isOnline()).thenReturn(false);
        when(target.state()).thenReturn(GameState.WAITING);
        callbacks.remove().run();
        verify(target, never()).add(any());
        verify(lobby).sendToLobby(leader);
    }

    @Test
    void quitCancellationSurvivesReconnect() {
        GameInstance target = destination(GameState.LOADING);
        when(instances.findOrCreate(type, mode, 1)).thenReturn(target);
        service.join(leader, type, mode);
        service.cancelPending(leader);
        when(target.state()).thenReturn(GameState.WAITING);
        callbacks.remove().run();
        verify(target, never()).add(any());
    }

    @Test
    void capacityLossRequeuesWholePartyBeforeAddingAnyone() {
        Player member = party();
        GameInstance first = destination(GameState.LOADING);
        GameInstance second = destination(GameState.WAITING);
        when(instances.findOrCreate(type, mode, 2)).thenReturn(first, second);
        service.join(leader, type, mode);
        when(first.state()).thenReturn(GameState.WAITING);
        when(first.joinable(2)).thenReturn(false);
        callbacks.remove().run();
        verify(first, never()).add(any());
        verify(second).add(leader);
        verify(second).add(member);
    }

    @Test
    void unexpectedAdmissionFailureRollsBackThenRetriesWholeParty() {
        Player member = party();
        GameInstance first = destination(GameState.WAITING);
        GameInstance second = destination(GameState.WAITING);
        when(first.add(member)).thenReturn(false);
        when(instances.findOrCreate(type, mode, 2)).thenReturn(first, second);
        service.join(leader, type, mode);
        verify(first).remove(leader, true);
        verify(second).add(leader);
        verify(second).add(member);
    }

    @Test
    void timeoutPreservesExistingGameAndDoesNotTeleportOut() {
        GameInstance current = destination(GameState.RUNNING);
        GameInstance target = destination(GameState.LOADING);
        when(instances.byPlayer(leader)).thenReturn(current);
        when(instances.findOrCreate(type, mode, 1)).thenReturn(target);
        service.join(leader, type, mode);
        int count = 0;
        while (!callbacks.isEmpty() && count++ < 100) callbacks.remove().run();
        assertEquals(60, count);
        verify(current, never()).remove(any(), anyBoolean());
        verify(lobby, never()).sendToLobby(leader);
        verify(target, never()).add(any());
    }

    @Test
    void failedLoadingReturnsUngroupedPlayerSafelyToLobby() {
        GameInstance target = destination(GameState.LOADING);
        when(instances.findOrCreate(type, mode, 1)).thenReturn(target).thenReturn(null);
        service.join(leader, type, mode);
        when(target.state()).thenReturn(GameState.RESETTING);
        when(target.joinable(anyInt())).thenReturn(false);
        callbacks.remove().run();
        verify(lobby).sendToLobby(leader);
        verify(target, never()).add(any());
    }
}
