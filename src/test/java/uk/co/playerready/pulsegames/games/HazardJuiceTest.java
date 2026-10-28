package uk.co.playerready.pulsegames.games;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.games.avalanche.AvalancheGame;
import uk.co.playerready.pulsegames.games.common.HazardFeedback;

import static org.junit.jupiter.api.Assertions.*;

class HazardJuiceTest {
    @Test void pitchRisesEverySecondOfTheCountdownWithinMinecraftLimits() {
        float last = 0;
        for (int step = 0; step < 7; step++) {
            float pitch = HazardFeedback.risingPitch(step, 7);
            assertTrue(pitch > last);
            assertTrue(pitch >= 0.5f && pitch <= 2f);
            last = pitch;
        }
        assertEquals(2f, HazardFeedback.risingPitch(6, 7), 0.001f);
    }

    @Test void eliminationCueIsLouderThanTheEngineDefault() {
        var quiet = new uk.co.playerready.pulsegames.core.game.MiniGame(null) {}.eliminationSound();
        assertEquals(0.3f, quiet.volume());
        assertTrue(HazardFeedback.LOUD_ELIMINATION.volume() >= 1.0f);
    }

    @Test void finalThreeOnlyFiresWhenThreeAreLeftOfAWiderField() {
        assertFalse(HazardFeedback.finalThree(java.util.List.of(), 4, 8));
        assertTrue(HazardFeedback.finalThree(java.util.List.of(), 3, 8));
        assertFalse(HazardFeedback.finalThree(java.util.List.of(), 3, 3), "a three-player game starts at the final three");
    }

    @Test void avalancheTelegraphsInTheLastSecondAndCallsOnlyTrueNearMisses() {
        assertTrue(AvalancheGame.shouldTelegraph(20));
        assertFalse(AvalancheGame.shouldTelegraph(35));
        assertTrue(AvalancheGame.isNearMiss(1.4));
        assertFalse(AvalancheGame.isNearMiss(0.5), "that one hits");
        assertFalse(AvalancheGame.isNearMiss(4));
    }

    @Test void tntCrumbleAcceleratesLateAndRespectsTheFloorAndOffSwitch() {
        var m = uk.co.playerready.pulsegames.games.tntrun.TntRunGame.class;
        assertEquals(8, crumble(0, 60, 4));
        assertEquals(7, crumble(60, 60, 4));
        assertEquals(4, crumble(600, 60, 4));
        assertEquals(8, crumble(600, 0, 4), "0 turns acceleration off");
    }

    private int crumble(int elapsed, int every, int min) {
        try {
            var method = uk.co.playerready.pulsegames.games.tntrun.TntRunGame.class.getDeclaredMethod("crumbleDelay", int.class, int.class, int.class);
            method.setAccessible(true);
            return (int) method.invoke(null, elapsed, every, min);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Test void blockPartyShowsNameAndShapeAndShortensRatherThanHidesTheHardcoreHint() throws Exception {
        var cls = uk.co.playerready.pulsegames.games.blockparty.BlockPartyGame.class;
        var label = cls.getDeclaredMethod("callLabel", int.class, Material.class);
        label.setAccessible(true);
        assertEquals("▲ RED", label.invoke(null, 0, Material.RED_WOOL));
        assertEquals("■ LIGHT BLUE", label.invoke(null, 1, Material.LIGHT_BLUE_GLAZED_TERRACOTTA));
        var hint = cls.getDeclaredMethod("hintSeconds", boolean.class, int.class, int.class);
        hint.setAccessible(true);
        assertEquals(2, hint.invoke(null, true, 8, 5), "hardcore late rounds: short hint");
        assertEquals(5, hint.invoke(null, false, 8, 5));
        assertEquals(1, hint.invoke(null, true, 8, 1));
    }

    @Test void propHuntRoundsSwapSeekersToPlayersWhoHaveNotSoughtYet() throws Exception {
        var ids = java.util.stream.IntStream.range(0, 5).mapToObj(i -> java.util.UUID.randomUUID()).toList();
        var turns = new java.util.HashMap<java.util.UUID, Integer>();
        var method = uk.co.playerready.pulsegames.games.prophunt.PropHuntGame.class.getDeclaredMethod(
                "pickSeekers", java.util.List.class, java.util.Map.class, int.class, java.util.Random.class);
        method.setAccessible(true);
        var seen = new java.util.HashSet<java.util.UUID>();
        for (int round = 0; round < 5; round++) {
            @SuppressWarnings("unchecked")
            var picked = (java.util.Set<java.util.UUID>) method.invoke(null, ids, turns, 1, new java.util.Random(round));
            assertEquals(1, picked.size());
            assertTrue(seen.add(picked.iterator().next()), "nobody seeks twice before everyone has");
            picked.forEach(id -> turns.merge(id, 1, Integer::sum));
        }
    }
}
