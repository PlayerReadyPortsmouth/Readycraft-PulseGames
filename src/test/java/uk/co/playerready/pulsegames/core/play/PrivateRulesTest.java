package uk.co.playerready.pulsegames.core.play;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.game.CompetitiveEligibility;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrivateRulesTest {
    private static final PrivateRules.MapOption A = new PrivateRules.MapOption("a", "tpl1"),
            A2 = new PrivateRules.MapOption("a2", "tpl1"), B = new PrivateRules.MapOption("b", "tpl2");

    @Test void onlyPartyMembersAreAdmittedButBotsAre() {
        UUID friend = UUID.randomUUID(), stranger = UUID.randomUUID();
        Set<UUID> party = Set.of(friend);
        assertTrue(PrivateRules.admits(party, friend, false));
        assertFalse(PrivateRules.admits(party, stranger, false));
        assertTrue(PrivateRules.admits(party, stranger, true));
        assertTrue(new PrivateSetup("g", "m", null, true, false, null, friend, party).admits(friend));
        assertFalse(new PrivateSetup("g", "m", null, true, false, null, friend, party).admits(stranger));
    }

    @Test void privateTokensFollowCompetitiveEligibility() {
        // One person with bots on a 12-player map: bots outnumber people, so no tokens.
        assertFalse(CompetitiveEligibility.eligible(1, 6, 12, false));
        assertFalse(CompetitiveEligibility.eligible(1, 3, 4, false));
        // Mostly people: tokens are paid. Friends only, no bots, also counts.
        assertTrue(CompetitiveEligibility.eligible(6, 6, 12, false));
        assertTrue(CompetitiveEligibility.eligible(3, 1, 4, false));
        assertTrue(CompetitiveEligibility.eligible(2, 0, 12, false));
        assertTrue(PrivateRules.TOKEN_NOTE.contains("most players are people"));
    }

    @Test void minimumPlayersTable() {
        // bots usable: one person is enough, never more than the mode asks.
        assertEquals(1, PrivateRules.minPlayers(2, true));
        assertEquals(1, PrivateRules.minPlayers(4, true));
        // no bots: two people, again never above the mode's own minimum.
        assertEquals(2, PrivateRules.minPlayers(4, false));
        assertEquals(2, PrivateRules.minPlayers(2, false));
        assertEquals(1, PrivateRules.minPlayers(1, false));
    }

    @Test void mapOptOutHoldsUnlessLeaderChoseBots() {
        assertTrue(PrivateRules.mapAllowsBots(true, false));
        assertFalse(PrivateRules.mapAllowsBots(false, false));
        assertTrue(PrivateRules.mapAllowsBots(false, true));
        assertFalse(PrivateRules.botsWanted(false, true));
        assertFalse(PrivateRules.botsWanted(true, false));
        UUID l = UUID.randomUUID();
        assertFalse(new PrivateSetup("g", "m", null, true, false, null, l, Set.of(l)).explicitBots());
        assertTrue(new PrivateSetup("g", "m", null, true, true, null, l, Set.of(l)).explicitBots());
        assertFalse(new PrivateSetup("g", "m", null, false, true, null, l, Set.of(l)).explicitBots());
    }

    @Test void busyMapSwapsForFreeCopyOrReportsBusy() {
        var all = List.of(A, A2, B);
        assertEquals("a", PrivateRules.pickArena(all, Set.of(), "a", n -> 0).arenaId());
        assertEquals("a2", PrivateRules.pickArena(all, Set.of("a"), "a", n -> 0).arenaId());
        assertEquals(PrivateRules.Status.BUSY, PrivateRules.pickArena(all, Set.of("a", "a2"), "a", n -> 0).status());
        assertEquals(PrivateRules.Status.BUSY, PrivateRules.pickArena(List.of(A, B), Set.of("a", "b"), null, n -> 0).status());
        assertEquals("b", PrivateRules.pickArena(List.of(A, B), Set.of("a"), null, n -> 0).arenaId());
        assertEquals(PrivateRules.Status.NONE, PrivateRules.pickArena(all, Set.of(), "zzz", n -> 0).status());
        assertEquals(PrivateRules.Status.NONE, PrivateRules.pickArena(List.of(), Set.of(), null, n -> 0).status());
    }

    @Test void rematchReusesTheSetup() {
        UUID leader = UUID.randomUUID(), friend = UUID.randomUUID(), newLeader = UUID.randomUUID();
        var first = new PrivateSetup("skywars", "solo", "island", false, true, PrivateSetup.Level.HARD, leader, Set.of(leader, friend));
        var again = first.withPeople(newLeader, Set.of(newLeader, friend));
        assertEquals("skywars", again.gameId());
        assertEquals("solo", again.modeId());
        assertEquals("island", again.mapId());
        assertFalse(again.fillBots());
        assertEquals(PrivateSetup.Level.HARD, again.level());
        assertEquals(newLeader, again.leader());
        assertEquals(Set.of(newLeader, friend), again.members());
    }

    @Test void draftDefaultsAndChoices() {
        var draft = new PrivateDraft();
        assertTrue(draft.fillBots());
        assertEquals(PrivateSetup.Level.AUTO, draft.level());
        assertFalse(draft.ready());
        draft.setGame("skywars", null);
        assertFalse(draft.ready());
        draft.setMode("solo");
        draft.setMap("island");
        assertTrue(draft.ready());
        draft.setMode("doubles");
        assertNull(draft.mapId());
        UUID l = UUID.randomUUID();
        assertFalse(draft.toSetup(l, Set.of(l)).explicitBots());
        draft.toggleBots();
        draft.toggleBots();
        assertTrue(draft.toSetup(l, Set.of(l)).explicitBots());
        for (int i = 0; i < PrivateSetup.Level.values().length; i++) draft.nextLevel();
        assertEquals(PrivateSetup.Level.AUTO, draft.level());
        assertEquals("easy", PrivateSetup.Level.EASY.setting());
        assertNull(PrivateSetup.Level.AUTO.setting());
        assertEquals(PrivateSetup.Level.ADAPTIVE, PrivateSetup.Level.parse(" adaptive "));
    }
}
