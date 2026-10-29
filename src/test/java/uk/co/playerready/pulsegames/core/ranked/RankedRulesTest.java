package uk.co.playerready.pulsegames.core.ranked;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RankedRulesTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID();

    @Test void kScheduleIs32ForTenGamesThen20() {
        assertEquals(32, Elo.k(0));
        assertEquals(32, Elo.k(9));
        assertEquals(20, Elo.k(10));
        assertEquals(16, Elo.delta(1000, 1000, 1.0, 0, 1.0));
        assertEquals(10, Elo.delta(1000, 1000, 1.0, 10, 1.0));
    }

    @Test void eloIsZeroSumForEqualKAndFavouritesGainLess() {
        assertEquals(0.5, Elo.expected(1000, 1000), 1e-9);
        int win = Elo.delta(1400, 1000, 1.0, 20, 1.0), upset = Elo.delta(1000, 1400, 1.0, 20, 1.0);
        assertTrue(win < upset);
        assertEquals(0, Elo.delta(1000, 1000, 0.5, 0, 1.0));
        assertEquals(-Elo.delta(1200, 1000, 1.0, 5, 1.0), Elo.delta(1000, 1200, 0.0, 5, 1.0));
    }

    @Test void ratingNeverGoesBelowZero() { assertEquals(0, Elo.apply(10, -32)); }

    @Test void divisionMappingUsesThresholdsAndUnrankedUntilFiveGames() {
        var d = new Divisions(Divisions.defaults());
        assertEquals("Wood", d.of(899).name());
        assertEquals("Stone", d.of(900).name());
        assertEquals("Stone", d.of(1099).name());
        assertEquals("Iron", d.of(1100).name());
        assertEquals("Gold", d.of(1499).name());
        assertEquals("Diamond", d.of(1500).name());
        assertEquals(58, d.distanceToNext(1042));
        assertEquals(0, d.distanceToNext(1800));
        assertEquals("Unranked", d.label(1600, 4));
        assertEquals("Diamond", d.label(1600, 5));
        assertEquals("", d.badge(1600, 2));
        assertTrue(d.badge(1600, 5).contains("DIA"));
    }

    @Test void divisionsComeFromConfig() throws Exception {
        var cfg = new YamlConfiguration();
        cfg.loadFromString("ranked:\n  divisions:\n    - {name: Low, min: 0, tag: LO}\n    - {name: High, min: 1000, tag: HI}\n");
        var d = Divisions.fromConfig(cfg);
        assertEquals("High", d.of(1000).name());
        assertEquals(2, d.all().size());
    }

    @Test void matchmakingRangeWidensBy50EveryTenSecondsUpToTheCap() {
        var r = QueueRange.DEFAULT;
        assertEquals(100, r.at(0));
        assertEquals(100, r.at(9_999));
        assertEquals(150, r.at(10_000));
        assertEquals(300, r.at(40_000));
        assertEquals(400, r.at(600_000));
    }

    @Test void queuePairsOnlyWithinTheWidenedGapAndSameKit() {
        var q = new RankedQueue(QueueRange.DEFAULT);
        q.add(new RankedQueue.Ticket(A, "classic", 1000, 0));
        q.add(new RankedQueue.Ticket(B, "classic", 1220, 0));
        q.add(new RankedQueue.Ticket(C, "sumo", 1000, 0));
        assertTrue(q.match(0).isEmpty());
        assertTrue(q.match(10_000).isEmpty(), "gap 220 needs range 250");
        var pairs = q.match(30_000);
        assertEquals(1, pairs.size());
        assertEquals(1, q.all().size(), "the sumo player keeps waiting");
    }

    @Test void queuePicksTheClosestAndRemovesCancelled() {
        var q = new RankedQueue(QueueRange.DEFAULT);
        q.add(new RankedQueue.Ticket(A, "classic", 1000, 0));
        q.add(new RankedQueue.Ticket(B, "classic", 1090, 5));
        q.add(new RankedQueue.Ticket(C, "classic", 1010, 10));
        var pairs = q.match(10);
        assertEquals(C, pairs.getFirst().b().player());
        q.remove(B);
        assertEquals(0, q.all().size());
    }

    @Test void seasonSoftResetHalvesTheDistanceFrom1000() {
        assertEquals(1000, SeasonRules.softReset(1000));
        assertEquals(1300, SeasonRules.softReset(1600));
        assertEquals(750, SeasonRules.softReset(500));
        assertEquals(1000, SeasonRules.softReset(Elo.apply(0, 0) + 1000));
    }

    @Test void seasonActionsFollowIdAndEndDate() {
        var ends = LocalDate.of(2027, 1, 31);
        assertEquals(SeasonRules.Action.ADOPT, SeasonRules.decide(null, false, "1", ends, LocalDate.of(2026, 11, 1)));
        assertEquals(SeasonRules.Action.NONE, SeasonRules.decide("1", false, "1", ends, ends));
        assertEquals(SeasonRules.Action.END, SeasonRules.decide("1", false, "1", ends, ends.plusDays(1)));
        assertEquals(SeasonRules.Action.NONE, SeasonRules.decide("1", true, "1", ends, ends.plusDays(9)));
        assertEquals(SeasonRules.Action.ROLLOVER, SeasonRules.decide("1", false, "2", ends, ends.minusDays(40)));
        assertEquals(SeasonRules.Action.ADOPT, SeasonRules.decide("1", true, "2", ends, ends.plusDays(9)));
        assertNull(SeasonRules.parseEnds("not a date"));
    }

    @Test void rolloverArchivesPeakDivisionAndHalvesRatings() {
        var data = new YamlConfiguration();
        var store = new RankedStore(data.createSection("_ranked"), this, () -> { });
        store.put(A, "Ann", "classic", new RankedStore.Rating(1200, 12, 1620));
        store.put(B, "Bob", "classic", new RankedStore.Rating(1300, 3, 1300));
        store.archiveAndSoftReset("1", new Divisions(Divisions.defaults()));
        assertEquals("Diamond", store.archived("1", A, "classic"));
        assertNull(store.archived("1", B, "classic"), "unplaced players have no peak");
        assertEquals(1100, store.get(A, "classic").rating());
        assertEquals(1100, store.get(A, "classic").peak());
        assertEquals(12, store.get(A, "classic").games());
    }

    @Test void historyKeepsTheLastTenAndBoardSkipsUnplaced() {
        var store = new RankedStore(new YamlConfiguration().createSection("r"), this, () -> { });
        for (int i = 0; i < 12; i++) store.addHistory(A, "classic|W|" + i + "|Bob|1");
        assertEquals(10, store.history(A).size());
        assertEquals("classic|W|11|Bob|1", store.history(A).getFirst());
        store.put(A, "Ann", "sumo", new RankedStore.Rating(1100, 6, 1100));
        store.put(B, "Bob", "sumo", new RankedStore.Rating(1500, 2, 1500));
        assertEquals(List.of(A), store.top("sumo", 10).stream().map(RankedStore.Row::player).toList());
    }

    @Test void repeatOpponentsGetSmallerChangesInsideTheWindowOnly() {
        var d = new RepeatDampener(600_000L, 1.0, 0.5, 0.25);
        assertEquals(1.0, d.factor(A, B, 0));
        d.record(A, B, 0);
        assertEquals(0.5, d.factor(B, A, 1_000));
        d.record(A, B, 1_000);
        assertEquals(0.25, d.factor(A, B, 2_000));
        assertEquals(1.0, d.factor(A, B, 700_000), "outside ten minutes");
        assertEquals(1.0, d.factor(A, C, 2_000));
        assertEquals(8, Elo.delta(1000, 1000, 1.0, 0, 0.5));
    }

    @Test void disconnectCountsOnlyAfterGraceAndNotIfTheyReturnInTime() {
        long grace = 30_000;
        assertEquals(DisconnectGrace.Verdict.WAITING, DisconnectGrace.verdict(0, -1, 29_999, grace));
        assertEquals(DisconnectGrace.Verdict.COUNTS, DisconnectGrace.verdict(0, -1, 30_000, grace));
        assertEquals(DisconnectGrace.Verdict.VOID, DisconnectGrace.verdict(0, 12_000, 12_000, grace));
        assertEquals(DisconnectGrace.Verdict.COUNTS, DisconnectGrace.verdict(0, 40_000, 40_000, grace));
        assertEquals(18_000, DisconnectGrace.remainingMs(0, 12_000, grace));
    }

    @Test void botTierIsTheNearestAnchor() {
        assertEquals(BotDifficulty.EASY, BotTier.forRating(800, 900, 1200, 1500));
        assertEquals(BotDifficulty.NORMAL, BotTier.forRating(1100, 900, 1200, 1500));
        assertEquals(BotDifficulty.HARD, BotTier.forRating(1700, 900, 1200, 1500));
    }

    @Test void textHidesRatingUntilPlacedAndFormatsHistory() {
        var d = new Divisions(Divisions.defaults());
        assertTrue(RankedText.standing(d, new RankedStore.Rating(1000, 2, 1000)).startsWith("Unranked (2 of 5"));
        assertEquals("Gold, 1342", RankedText.standing(d, new RankedStore.Rating(1342, 8, 1342)));
        assertEquals("158 points to Diamond", RankedText.nextStep(d, new RankedStore.Rating(1342, 8, 1342)));
        assertEquals("Won against Bob (+13) in Sumo", RankedText.historyLine(RankedText.historyRecord("sumo", 1.0, 13, "Bob", 1)));
        assertEquals("Searching... (range ±150)", RankedText.searching(150));
    }
}
