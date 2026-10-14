package uk.co.playerready.pulsegames.core.team;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.party.PartyManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamAllocatorTest {
    @Test void calibratedSingletonsBalanceStrength() {
        List<Player> players = players(4);
        PartyManager parties = mock(PartyManager.class);
        when(parties.groupsOf(players)).thenReturn(players.stream().map(List::of).toList());
        var teams = TeamAllocator.allocate(players, 2, 2, parties, p -> players.indexOf(p) < 2 ? 3 : 1);
        for (GameTeam team : teams) {
            assertEquals(2, team.members().size());
            assertEquals(4, players.stream().filter(team::contains).mapToInt(p -> players.indexOf(p) < 2 ? 3 : 1).sum());
        }
    }
    @Test void equallySizedStrongPartiesDistributeAcrossTeamsWithoutSplitting() {
        List<Player> players = players(8);
        List<List<Player>> groups = List.of(players.subList(0, 2), players.subList(2, 4),
                players.subList(4, 6), players.subList(6, 8));
        PartyManager parties = mock(PartyManager.class);
        when(parties.groupsOf(players)).thenReturn(groups);
        var teams = TeamAllocator.allocate(players, 4, 2, parties, p -> players.indexOf(p) >= 4 ? 3 : 1);
        for (GameTeam team : teams) {
            assertEquals(4, team.members().size());
            assertEquals(8, players.stream().filter(team::contains).mapToInt(p -> players.indexOf(p) >= 4 ? 3 : 1).sum());
            assertEquals(2, players.stream().filter(team::contains).filter(p -> players.indexOf(p) >= 4).count());
        }
        for (List<Player> group : groups)
            assertTrue(teams.stream().anyMatch(team -> group.stream().allMatch(team::contains)));
    }
    @Test void partyIntegrityTakesPriorityOverSkillBalance() {
        List<Player> players = players(4);
        PartyManager parties = mock(PartyManager.class);
        when(parties.groupsOf(players)).thenReturn(List.of(players.subList(0, 2), List.of(players.get(2)), List.of(players.get(3))));
        var teams = TeamAllocator.allocate(players, 2, 2, parties, p -> players.indexOf(p) < 2 ? 3 : 1);
        assertTrue(teams.stream().anyMatch(t -> t.contains(players.get(0)) && t.contains(players.get(1))));
    }
    @Test void fragmentedPartiesUseExtraMapTeamsOrRejectWithoutSplitting() {
        List<Player> players = players(6);
        PartyManager parties = mock(PartyManager.class);
        when(parties.groupsOf(players)).thenReturn(List.of(players.subList(0, 2), players.subList(2, 4), players.subList(4, 6)));
        assertFalse(TeamAllocator.canFit(players, 3, 2, parties));
        var teams = TeamAllocator.allocate(players, 3, 3, parties);
        assertEquals(3, teams.size());
        assertTrue(teams.stream().allMatch(t -> t.members().size() == 2));
    }
    @Test void oversizedPartyRejectedButSoloOpponentsRemainAllowed() {
        List<Player> players = players(3);
        PartyManager parties = mock(PartyManager.class);
        when(parties.groupsOf(players)).thenReturn(List.of(players));
        assertFalse(TeamAllocator.canFit(players, 2, 2, parties));
        assertEquals(3, TeamAllocator.allocate(players, 1, 3, parties).size());
    }
    private List<Player> players(int count) {
        List<Player> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            result.add(player);
        }
        return result;
    }
}
