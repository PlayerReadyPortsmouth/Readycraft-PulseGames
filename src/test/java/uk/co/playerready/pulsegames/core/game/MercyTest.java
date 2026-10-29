package uk.co.playerready.pulsegames.core.game;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MercyTest {
    // ---- decision table, one row per game ----------------------------------------------

    @Test void bedwarsNeedsEveryOtherBedGoneAndTwiceTheNumbers() {
        assertTrue(Mercy.bedwars(true, 4, 2, 2.0));
        assertTrue(Mercy.bedwars(true, 4, 1, 2.0));
        assertFalse(Mercy.bedwars(true, 3, 2, 2.0), "1.5x is not enough");
        assertFalse(Mercy.bedwars(false, 8, 1, 2.0), "someone still has a bed");
        assertFalse(Mercy.bedwars(true, 0, 0, 2.0), "nobody left to win");
    }

    @Test void scoreGamesNeedABigGapLateOn() {
        // target 100: gap of 60 with under 40% of 600 s (240 s) left
        assertTrue(Mercy.scoreGap(70, 10, 100, 200, 600, 0.6, 0.4));
        assertFalse(Mercy.scoreGap(70, 20, 100, 200, 600, 0.6, 0.4), "gap 50 is short of 60");
        assertFalse(Mercy.scoreGap(70, 10, 100, 300, 600, 0.6, 0.4), "too much time left");
        assertFalse(Mercy.scoreGap(70, 10, 100, 240, 600, 0.6, 0.4), "exactly 40% left is not under 40%");
        assertTrue(Mercy.scoreGap(60, 0, 100, 0, 600, 0.6, 0.4), "gap exactly 60%");
        assertFalse(Mercy.scoreGap(5, 0, 0, 0, 600, 0.6, 0.4), "no target, no rule");
    }

    @Test void scoreGapReadsConfigWithDefaults() {
        YamlConfiguration cfg = new YamlConfiguration();
        assertTrue(Mercy.scoreGap(cfg, "koth", 100, 20, 120, 100, 600));
        cfg.set("mercy.koth.gap-percent", 90);
        assertFalse(Mercy.scoreGap(cfg, "koth", 100, 20, 120, 100, 600));
    }

    @Test void roundsNeedAnUnreachableLead() {
        assertTrue(Mercy.roundsUnreachable(30, 8, 2, 8), "22 ahead, 16 to play for");
        assertFalse(Mercy.roundsUnreachable(24, 8, 2, 8), "16 ahead can still be caught");
        assertFalse(Mercy.roundsUnreachable(30, 0, 0, 8), "no rounds left means the match is ending anyway");
    }

    @Test void kartsEndOnlyWhenTheLastHumanIsDone() {
        assertTrue(Mercy.botsOnly(true, 0, 3));
        assertFalse(Mercy.botsOnly(true, 1, 3));
        assertFalse(Mercy.botsOnly(false, 0, 3));
        assertFalse(Mercy.botsOnly(true, 0, 0));
    }

    @Test void leadingSideGroupsTeamsAndFindsTheRunnerUp() {
        // "a1 a2" are one team on 30, "b1" a solo on 8, "c1" a solo on 12
        var scores = java.util.Map.of("a1", 30, "a2", 30, "b1", 8, "c1", 12);
        var lead = Mercy.leading(List.of("a1", "a2", "b1", "c1"), s -> s.startsWith("a") ? "A" : s, scores::get);
        assertEquals("a1", lead.member());
        assertEquals(30, lead.best());
        assertEquals(12, lead.second());
        assertNull(Mercy.leading(List.<String>of(), s -> s, s -> 0));
    }

    // ---- countdown -----------------------------------------------------------------------

    @Test void holdThenCountdownThenFire() {
        Mercy.Timer timer = new Mercy.Timer();
        for (int i = 0; i < 3; i++) assertEquals(Mercy.Phase.HOLDING, timer.tick(true, 3, 4).phase());
        Mercy.Step start = timer.tick(true, 3, 4);
        assertEquals(Mercy.Phase.START, start.phase());
        assertEquals(4, start.secondsLeft());
        assertEquals(3, timer.tick(true, 3, 4).secondsLeft());
        assertEquals(2, timer.tick(true, 3, 4).secondsLeft());
        assertEquals(1, timer.tick(true, 3, 4).secondsLeft());
        assertEquals(Mercy.Phase.FIRE, timer.tick(true, 3, 4).phase());
    }

    @Test void noHoldStartsAtOnceAndFiresAfterTheCountdown() {
        Mercy.Timer timer = new Mercy.Timer();
        assertEquals(Mercy.Phase.START, timer.tick(true, 0, 10).phase());
        for (int i = 0; i < 9; i++) assertEquals(Mercy.Phase.COUNT, timer.tick(true, 0, 10).phase());
        assertEquals(Mercy.Phase.FIRE, timer.tick(true, 0, 10).phase());
    }

    @Test void scoringDuringTheCountdownCancelsItAndRestartsTheHold() {
        Mercy.Timer timer = new Mercy.Timer();
        timer.tick(true, 2, 10);
        timer.tick(true, 2, 10);
        assertEquals(Mercy.Phase.START, timer.tick(true, 2, 10).phase());
        timer.tick(true, 2, 10);
        assertEquals(Mercy.Phase.CANCEL, timer.tick(false, 2, 10).phase());
        assertFalse(timer.counting());
        assertEquals(Mercy.Phase.IDLE, timer.tick(false, 2, 10).phase());
        assertEquals(Mercy.Phase.HOLDING, timer.tick(true, 2, 10).phase(), "the hold starts again from nothing");
    }

    @Test void aBrokenHoldStartsOver() {
        Mercy.Timer timer = new Mercy.Timer();
        timer.tick(true, 60, 10);
        timer.tick(true, 60, 10);
        assertEquals(Mercy.Phase.IDLE, timer.tick(false, 60, 10).phase(), "no countdown was showing, so nothing to cancel");
        for (int i = 0; i < 60; i++) assertEquals(Mercy.Phase.HOLDING, timer.tick(true, 60, 10).phase());
        assertEquals(Mercy.Phase.START, timer.tick(true, 60, 10).phase());
    }

    // ---- draws and wording ----------------------------------------------------------------

    @Test void aMercyEndNeverRecordsADrawButARealTieDoes() {
        assertEquals(Mercy.Stat.LOSS, Mercy.statForNonWinner(false, true, false), "mercy always has a winner");
        assertEquals(Mercy.Stat.DRAW, Mercy.statForNonWinner(false, false, false), "timed-out tie is a draw");
        assertEquals(Mercy.Stat.LOSS, Mercy.statForNonWinner(false, false, true), "co-op nobody won");
        assertEquals(Mercy.Stat.NONE, Mercy.statForNonWinner(true, false, false), "forced stop");
    }

    @Test void aTimedOutTieAlreadyPaysBothSidesSecondPlaceTokens() {
        assertEquals(20, Placements.bonus(1, false, 4, 4, 20, 10));
        assertEquals(0, Placements.bonus(1, true, 4, 4, 20, 10), "a real winner's rival gets no joint-first bonus");
    }

    @Test void headlineAndSwitches() {
        assertEquals("Game over: Red win by a mile", Mercy.headline("Red", true));
        assertEquals("Game over: Ana wins by a mile", Mercy.headline("Ana", false));
        YamlConfiguration cfg = new YamlConfiguration();
        assertTrue(Mercy.enabled(cfg, "bedwars"));
        cfg.set("mercy.bedwars.enabled", false);
        assertFalse(Mercy.enabled(cfg, "bedwars"));
        assertTrue(Mercy.enabled(cfg, "koth"));
        cfg.set("mercy.enabled", false);
        assertFalse(Mercy.enabled(cfg, "koth"));
        assertEquals(10, Mercy.countdownSeconds(new YamlConfiguration()));
        assertEquals(15, Mercy.holdSeconds(new YamlConfiguration(), "x", 15));
    }

    @Test void theResultsScreenShowsTheEndedEarlyNoteOnlyWhenSet() {
        var plain = new MatchResults.Result("m", "bedwars", "solo", "Map", "Victory", "Team 1", 0, 0, 0, 0, 0, true, false, 0L);
        var early = new MatchResults.Result("m", "bedwars", "solo", "Map", "Victory", "Team 1", 0, 0, 0, 0, 0, true, false, 0L,
                0, 0, List.of(), List.of(), Mercy.NOTE);
        assertFalse(plain.lines().contains(Mercy.NOTE));
        assertTrue(early.lines().contains("Ended early: clearly decided"));
    }
}
