package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChallengeAndStreakTest {
    private final UUID p = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 10, 28);
    private final ChallengeDef three = new ChallengeDef("three", "Three games", ChallengeDef.Type.DISTINCT_GAMES, "", 3, 40);
    private final ChallengeDef win = new ChallengeDef("win", "Win", ChallengeDef.Type.WINS, "", 1, 30);
    private final ChallengeDef survive = new ChallengeDef("av", "Survive", ChallengeDef.Type.SURVIVE_SECONDS, "avalanche", 90, 30);
    private final List<ChallengeDef> all = List.of(three, win, survive);

    @Test void threeDifferentGamesCountsDistinctGamesOnly() {
        var store = new ChallengeStore(new YamlConfiguration());
        assertTrue(store.record(p, day, "spleef", false, 10, all).isEmpty());
        assertTrue(store.record(p, day, "spleef", false, 10, all).isEmpty());
        assertTrue(store.record(p, day, "koth", false, 10, all).isEmpty());
        assertEquals(List.of(three), store.record(p, day, "tntrun", false, 10, all));
        assertTrue(store.done(p, day, three));
    }

    @Test void winAndAvalancheSurvivalCompleteOnceEach() {
        var store = new ChallengeStore(new YamlConfiguration());
        assertTrue(store.record(p, day, "koth", false, 500, List.of(win, survive)).isEmpty(), "survival elsewhere does not count");
        assertTrue(store.record(p, day, "avalanche", false, 89, List.of(win, survive)).isEmpty());
        assertEquals(List.of(survive), store.record(p, day, "avalanche", false, 90, List.of(win, survive)));
        assertEquals(List.of(win), store.record(p, day, "spleef", true, 5, List.of(win, survive)));
        assertTrue(store.record(p, day, "spleef", true, 5, List.of(win, survive)).isEmpty(), "no second reward");
    }

    @Test void progressResetsOnANewDay() {
        var store = new ChallengeStore(new YamlConfiguration());
        store.record(p, day, "spleef", true, 5, all);
        assertEquals(1, store.progress(p, day, win));
        assertEquals(0, store.progress(p, day.plusDays(1), win));
        assertFalse(store.done(p, day.plusDays(1), win));
    }

    @Test void defsLoadFromConfigAndSkipBadEntries() {
        var yaml = new YamlConfiguration();
        yaml.set("a.type", "wins"); yaml.set("a.target", 2); yaml.set("a.reward", 10);
        yaml.set("b.type", "nonsense");
        var defs = ChallengeDef.load(yaml);
        assertEquals(1, defs.size());
        assertEquals(new ChallengeDef("a", "a", ChallengeDef.Type.WINS, "", 2, 10), defs.getFirst());
    }

    @Test void streakGrowsOnWinsResetsOnLossAndKeepsBest() {
        var s = new StreakStore(new YamlConfiguration());
        assertEquals(1, s.win(p)); assertEquals(2, s.win(p)); assertEquals(3, s.win(p));
        s.loss(p);
        assertEquals(0, s.current(p));
        assertEquals(1, s.win(p));
        assertEquals(3, s.best(p));
    }

    @Test void firstWinOfTheDayPaysOncePerDay() {
        var s = new StreakStore(new YamlConfiguration());
        assertTrue(s.claimFirstWin(p, day));
        assertFalse(s.claimFirstWin(p, day));
        assertTrue(s.claimFirstWin(p, day.plusDays(1)));
    }
}
