package uk.co.playerready.pulsegames.core.game;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.party.Party;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PartyQueueAdmissionTest {
    @Test void partyMemberAlreadyAtDestinationIsNotRemovedOrCountedTwice() {
        PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        Player leader = player(), member = player();
        Party party = mock(Party.class);
        when(party.isLeader(leader)).thenReturn(true);
        when(party.onlineMembers()).thenReturn(List.of(leader, member));
        when(party.size()).thenReturn(2);
        when(plugin.parties().partyOf(leader)).thenReturn(party);
        when(plugin.parties().partyOf(member)).thenReturn(party);
        GameType type = mock(GameType.class);
        when(type.id()).thenReturn("spleef");
        when(plugin.gameAvailable(type)).thenReturn(true);
        GameMode mode = GameMode.ffa("classic", "Classic", 2, 4);
        GameInstance destination = mock(GameInstance.class);
        when(plugin.instances().findOrCreate(type, mode, 2)).thenReturn(destination);
        when(destination.state()).thenReturn(GameState.WAITING);
        when(destination.isParticipant(member)).thenReturn(true);
        when(plugin.instances().byPlayer(member)).thenReturn(destination);
        when(destination.joinable(1)).thenReturn(true);
        when(destination.add(leader)).thenReturn(true);
        new PlayService(plugin).join(leader, type, mode);
        verify(destination).add(leader);
        verify(destination, never()).add(member);
        verify(destination, never()).remove(member, true);
    }
    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }
}
