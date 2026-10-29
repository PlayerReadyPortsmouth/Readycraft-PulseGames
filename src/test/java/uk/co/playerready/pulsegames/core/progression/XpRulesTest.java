package uk.co.playerready.pulsegames.core.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XpRulesTest {
    @Test void placementTable() {
        assertEquals(100, XpRules.placementXp(1));
        assertEquals(60, XpRules.placementXp(2));
        assertEquals(40, XpRules.placementXp(3));
        assertEquals(20, XpRules.placementXp(4));
        assertEquals(20, XpRules.placementXp(0), "unplaced still earns the floor");
    }

    @Test void killsPayTwoEachCappedAtTwenty() {
        assertEquals(0, XpRules.killXp(0));
        assertEquals(6, XpRules.killXp(3));
        assertEquals(20, XpRules.killXp(10));
        assertEquals(20, XpRules.killXp(500));
        assertEquals(0, XpRules.killXp(-4));
    }

    @Test void matchXpAddsKillsAndFirstOfDay() {
        assertEquals(100 + 8 + 25, XpRules.matchXp(1, 4, true));
        assertEquals(20, XpRules.matchXp(5, 0, false));
    }

    @Test void levelCurveNeedsHundredPlusFiftyPerLevel() {
        assertEquals(100, XpRules.xpToFinish(1));
        assertEquals(150, XpRules.xpToFinish(2));
        assertEquals(0, XpRules.xpForLevel(1));
        assertEquals(100, XpRules.xpForLevel(2));
        assertEquals(250, XpRules.xpForLevel(3));
        assertEquals(450, XpRules.xpForLevel(4));
        assertEquals(1, XpRules.levelFor(0));
        assertEquals(1, XpRules.levelFor(99));
        assertEquals(2, XpRules.levelFor(100));
        assertEquals(2, XpRules.levelFor(249));
        assertEquals(3, XpRules.levelFor(250));
        var p = XpRules.progress(300);
        assertEquals(3, p.level());
        assertEquals(50, p.into());
        assertEquals(200, p.needed());
    }

    @Test void levelCapsAtHundred() {
        long atCap = XpRules.xpForLevel(100);
        assertEquals(99, XpRules.levelFor(atCap - 1));
        assertEquals(100, XpRules.levelFor(atCap));
        assertEquals(100, XpRules.levelFor(atCap * 10));
        assertTrue(XpRules.progress(atCap * 10).maxed());
        assertEquals("[##########] 100%", XpRules.bar(XpRules.progress(atCap), 10));
    }

    @Test void unlockScheduleIsEveryFiveLevels() {
        assertFalse(XpRules.isUnlockLevel(1));
        assertFalse(XpRules.isUnlockLevel(4));
        assertTrue(XpRules.isUnlockLevel(5));
        assertTrue(XpRules.isUnlockLevel(100));
        assertFalse(XpRules.isUnlockLevel(105));
        assertEquals(5, XpRules.nextUnlockLevel(1));
        assertEquals(10, XpRules.nextUnlockLevel(5));
        assertEquals(100, XpRules.nextUnlockLevel(95));
        assertEquals(0, XpRules.nextUnlockLevel(100));
    }

    @Test void migrationGivesTenXpPerLifetimeWin() {
        assertEquals(0, XpRules.migratedXp(0));
        assertEquals(0, XpRules.migratedXp(-3));
        assertEquals(500, XpRules.migratedXp(50));
        assertEquals(3, XpRules.levelFor(XpRules.migratedXp(25)), "25 wins = 250 XP = level 3");
    }

    @Test void xpOnlyForRoundsThatCount() {
        assertTrue(ProgressionService.eligible(true, true, false, "completed"));
        assertFalse(ProgressionService.eligible(false, true, false, "completed"), "practice or unranked");
        assertFalse(ProgressionService.eligible(true, false, false, "completed"), "spectator");
        assertFalse(ProgressionService.eligible(true, true, true, "completed"), "bot");
        assertFalse(ProgressionService.eligible(true, true, false, "forced"));
        assertFalse(uk.co.playerready.pulsegames.core.game.CompetitiveEligibility.eligible(1, 0, 8, false), "lone human");
        assertTrue(uk.co.playerready.pulsegames.core.game.CompetitiveEligibility.eligible(2, 1, 8, false));
    }

    @Test void barAndTag() {
        assertEquals("[#####-----] 50%", XpRules.bar(new XpRules.Progress(3, 100, 200, false), 10));
        assertEquals("[12]", XpRules.tag(12));
        assertEquals("<green>[12]</green>", XpRules.colouredTag(12));
    }
}
