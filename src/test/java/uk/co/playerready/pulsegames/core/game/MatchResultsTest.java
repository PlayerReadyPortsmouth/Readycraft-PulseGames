package uk.co.playerready.pulsegames.core.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchResultsTest {
    @Test void repeatedDisplayDoesNotPayRewardsAndReturnClearsTransferHold() {
        var plugin = org.mockito.Mockito.mock(uk.co.playerready.pulsegames.PulseGamesPlugin.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(plugin.instances().byPlayer(player)).thenReturn(null);
        var results = new MatchResults(plugin);
        var result = new MatchResults.Result("a", "duels", "classic", "Courtyard", "Victory", "Solo",
                1, 1, 1, 0, 50, true, false, System.currentTimeMillis() + 10000);
        results.publish(player, result); results.publish(player, result);
        assertTrue(results.holdsReturn(player));
        results.command(player, new String[]{"return"});
        assertFalse(results.holdsReturn(player));
        org.mockito.Mockito.verify(plugin.playService()).leave(player);
        org.mockito.Mockito.verify(plugin.economy(), org.mockito.Mockito.never()).addMatchTokens(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test void reportSeparatesPersonalTeamAndActualEndRewardFromEligibility() {
        var result = new MatchResults.Result("a", "duels", "classic", "Courtyard", "Victory", "Team 1",
                3, 7, 2, 1, 100, true, false, 1000);
        assertTrue(result.lines().get(1).contains("Team score: 7"));
        assertTrue(result.lines().get(2).contains("Your score: 3 | Kills: 2 | Deaths: 1"));
        assertTrue(result.lines().get(3).contains("+100"));
        assertTrue(result.lines().get(4).contains("Competitive:"));
    }
    @Test void practiceMakesNoClaimOfCompetitiveProgress() {
        var result = new MatchResults.Result("a", "duels", "classic", "Courtyard", "Defeat", "Solo",
                0, 0, 0, 1, 0, false, true, 1000);
        assertTrue(result.lines().getLast().contains("no competitive records or match rewards"));
        assertThrows(UnsupportedOperationException.class, () -> result.lines().add("extra"));
    }
}
