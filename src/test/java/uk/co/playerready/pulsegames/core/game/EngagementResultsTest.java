package uk.co.playerready.pulsegames.core.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.games.GameCatalog;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EngagementResultsTest {

    private static final class Fixture {
        final PulseGamesPlugin plugin = mock(PulseGamesPlugin.class, RETURNS_DEEP_STUBS);
        final GameType type = mock(GameType.class);
        final GameMode mode = mock(GameMode.class);
        GameInstance game;
        final List<Player> players = new ArrayList<>();

        Fixture(int count) throws Exception { this(count, g -> new MiniGame(g) { }); }

        Fixture(int count, java.util.function.Function<GameInstance, MiniGame> logic) throws Exception {
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(type.createLogic(any())).thenAnswer(inv -> logic.apply(inv.getArgument(0)));
            when(type.id()).thenReturn("spleef");
            when(mode.id()).thenReturn("classic");
            game = new GameInstance(plugin, type, mode, mock(Arena.class));
            for (int i = 0; i < count; i++) {
                Player p = mock(Player.class);
                UUID id = UUID.randomUUID();
                when(p.getUniqueId()).thenReturn(id);
                when(p.getName()).thenReturn("P" + (i + 1));
                when(p.isOnline()).thenReturn(true);
                when(p.getLocation()).thenReturn(mock(Location.class));
                when(plugin.getServer().getPlayer(id)).thenReturn(p);
                players.add(p);
                set("participants", id);
            }
            field("state").set(game, GameState.RUNNING);
        }

        java.lang.reflect.Field field(String name) throws Exception {
            var f = GameInstance.class.getDeclaredField(name); f.setAccessible(true); return f;
        }
        @SuppressWarnings("unchecked")
        void set(String collection, UUID id) throws Exception { ((Collection<UUID>) field(collection).get(game)).add(id); }
        void alive(Player... ps) throws Exception { for (Player p : ps) set("alive", p.getUniqueId()); }
        void out(Player... ps) throws Exception { for (Player p : ps) set("outOrder", p.getUniqueId()); }
        void end(List<Player> winners, String reason) {
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
                game.end(winners, reason);
            }
        }
    }

    @Test void placementsFollowEliminationOrderAndPayBonusesToSecondAndThird() throws Exception {
        var f = new Fixture(4);
        var p1 = f.players.get(0); var p2 = f.players.get(1); var p3 = f.players.get(2); var p4 = f.players.get(3);
        f.out(p1, p2, p3);
        f.alive(p4);
        assertEquals(1, f.game.placement(p4));
        assertEquals(4, f.game.placement(p1));
        f.end(List.of(p4), "last-standing");
        assertEquals(List.of(p4, p3, p2, p1), f.game.placementOrder());
        verify(f.plugin.economy()).addMatchTokens(p3, 20, "placement");
        verify(f.plugin.economy()).addMatchTokens(p2, 10, "placement");
        verify(f.plugin.economy(), never()).addMatchTokens(eq(p1), anyInt(), eq("placement"));
        verify(f.plugin.stats(), times(3)).addLoss(any(), eq("spleef"));
        verify(f.plugin.stats(), never()).addDraw(any(), any());
    }

    @Test void recordFinishOrdersRaceFinishersAheadOfSurvivors() throws Exception {
        var f = new Fixture(3);
        var p1 = f.players.get(0); var p2 = f.players.get(1); var p3 = f.players.get(2);
        f.alive(p1, p2, p3);
        f.game.recordFinish(p2);
        f.game.recordFinish(p1);
        f.game.recordFinish(p2);
        assertEquals(1, f.game.placement(p2));
        assertEquals(2, f.game.placement(p1));
        assertEquals(3, f.game.placement(p3));
    }

    @Test void drawIsNotALossAndJointFirstGetsNoWinReward() throws Exception {
        var f = new Fixture(4);
        var p1 = f.players.get(0); var p2 = f.players.get(1);
        f.alive(p1, p2);
        f.out(f.players.get(2), f.players.get(3));
        f.end(List.of(), "time-draw");
        verify(f.plugin.stats(), never()).addLoss(any(), any());
        verify(f.plugin.stats(), never()).addWin(any(), any());
        verify(f.plugin.stats(), times(4)).addDraw(any(), eq("spleef"));
        verify(f.plugin.economy(), never()).addMatchTokens(any(), eq(50), any());
        verify(f.plugin.economy()).addMatchTokens(p1, 10, "game played");
        verify(f.plugin.economy()).addMatchTokens(p1, 20, "placement");
    }

    @Test void playerWhoLeftEarlyStillGetsAResultWhenTheMatchEnds() throws Exception {
        var f = new Fixture(4);
        var gone = f.players.get(3);
        f.alive(f.players.get(0), f.players.get(1), f.players.get(2), gone);
        f.game.remove(gone, false);
        assertEquals(4, f.game.placement(gone));
        f.end(List.of(f.players.get(0)), "last-standing");
        verify(f.plugin.results()).publishLate(eq(gone), argThat(r -> r.place() == 4 && r.field() == 4
                && r.outcome().equals("Finished 4th of 4")));
        verify(f.plugin.economy(), never()).addMatchTokens(eq(gone), anyInt(), any());
    }

    @Test void spectatorQueueActionRequeuesTheSameModeWhileTheMatchRuns() throws Exception {
        var f = new Fixture(3);
        var out = f.players.get(0);
        when(f.plugin.parties().partyOf(out)).thenReturn(null);
        f.game.spectatorTools().queueSame(out);
        verify(f.plugin.playService()).join(out, f.type, f.mode);
    }

    @Test void spectatorQueueIsRefusedWhilePartyMatesAreStillPlayingOrAlreadyQueued() throws Exception {
        var f = new Fixture(3);
        var out = f.players.get(0); var mate = f.players.get(1);
        f.alive(mate);
        var party = mock(uk.co.playerready.pulsegames.core.party.Party.class);
        when(party.onlineMembers()).thenReturn(List.of(out, mate));
        when(f.plugin.parties().partyOf(out)).thenReturn(party);
        f.game.spectatorTools().queueSame(out);
        verify(f.plugin.playService(), never()).join(any(), any(), any());
        when(f.plugin.parties().partyOf(out)).thenReturn(null);
        when(f.plugin.playService().hasPending(out)).thenReturn(true);
        f.game.spectatorTools().queueSame(out);
        verify(f.plugin.playService(), never()).join(any(), any(), any());
    }

    @Test void emotesAreRateLimitedPerPlayer() throws Exception {
        var f = new Fixture(2);
        var tools = f.game.spectatorTools();
        UUID a = f.players.get(0).getUniqueId(), b = f.players.get(1).getUniqueId();
        assertTrue(tools.tryEmote(a, 10_000));
        assertFalse(tools.tryEmote(a, 11_000));
        assertTrue(tools.tryEmote(b, 11_000));
        assertTrue(tools.tryEmote(a, 10_000 + SpectatorTools.EMOTE_COOLDOWN_MS));
    }

    @Test void resultActionsAreOnlyBlockedWhileActivelyPlayingAnotherRound() throws Exception {
        var f = new Fixture(2);
        var p = f.players.get(0);
        assertTrue(MatchResults.canAct(null, p, "m"));
        f.alive(p);
        assertFalse(MatchResults.canAct(f.game, p, "other"));
        assertTrue(MatchResults.canAct(f.game, p, f.game.id()));       // same match, e.g. spectating
        f.field("alive").set(f.game, new LinkedHashSet<UUID>());        // eliminated
        assertTrue(MatchResults.canAct(f.game, p, "other"));
    }

    @Test void coopRoundNobodyWonIsFramedAsWellPlayedAndTheSquadSummaryReachesTheResultsMenu() throws Exception {
        var f = new Fixture(2, g -> new MiniGame(g) {
            @Override public boolean cooperative() { return true; }
            @Override public EndSummary endSummary(List<Player> winners) {
                return new EndSummary("<gold><b>Well played!", "<gray>Round 7 reached", List.of("<gold>Round reached: <white>7", "<light_purple>MVP: <white>P1"));
            }
        });
        var p1 = f.players.get(0);
        f.alive(p1);
        f.end(List.of(), "overrun");
        var title = ArgumentCaptor.forClass(net.kyori.adventure.title.Title.class);
        verify(p1).showTitle(title.capture());
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertEquals("Well played!", plain.serialize(title.getValue().title()));
        assertEquals("Round 7 reached", plain.serialize(title.getValue().subtitle()));
        verify(f.plugin.results()).publish(eq(p1), argThat(r -> r.summary().equals(List.of("Round reached: 7", "MVP: P1"))
                && r.place() == 0 && r.outcome().equals("Round ended")));
    }

    @Test void everyRegisteredGameHasAnObjectiveCardThatFitsTheSidebar() {
        var registry = new GameRegistry();
        GameCatalog.registerAll(registry);
        assertFalse(registry.all().isEmpty());
        for (GameType type : registry.all()) {
            ObjectiveCard card = type.card();
            assertNotEquals(ObjectiveCard.fallback(type), card, type.id() + " has no authored card");
            assertFalse(card.title().isBlank());
            assertFalse(card.subtitle().isBlank());
            assertTrue(card.tip().length() <= 36, type.id() + " tip too long: " + card.tip());
        }
    }

    @Test void resultSummaryShowsPlaceAndPodiumInFriendlyWords() {
        var result = new MatchResults.Result("m", "spleef", "classic", "Arena", "Finished 3rd of 8", "Solo",
                0, 0, 0, 1, 10, true, false, 1000, 3, 8, List.of("1st: Ana", "2nd: Bo", "3rd: Cy"));
        assertEquals("You finished 3rd of 8. Well played!", result.placeLines().get(0));
        assertTrue(result.placeLines().get(1).contains("1st: Ana | 2nd: Bo | 3rd: Cy"));
        assertTrue(result.lines().stream().noneMatch(l -> l.contains("Defeat")));
    }
}
