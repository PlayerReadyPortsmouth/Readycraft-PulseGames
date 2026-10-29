package uk.co.playerready.pulsegames.games.skywars;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SkywarsTacticsTest {
    @Test void lootsSwordThenArmourThenBlocksThenBowThenPearls() {
        List<String> chest = Arrays.asList("ENDER_PEARL", "BOW", null, "OAK_PLANKS", "IRON_CHESTPLATE", "COOKED_BEEF", "IRON_SWORD");
        assertEquals(List.of(6, 4, 3, 1, 0, 5), SkywarsTactics.lootOrder(chest));
    }

    @Test void goesForTheNearestUnlootedChestOnItsOwnLevel() {
        double[][] chests = {{2, 64, 2}, {10, 64, 0}, {3, 90, 0}};
        assertEquals(0, SkywarsTactics.nearestChest(0, 64, 0, chests, new boolean[]{false, false, false}));
        assertEquals(1, SkywarsTactics.nearestChest(0, 64, 0, chests, new boolean[]{true, false, false}));
        assertEquals(-1, SkywarsTactics.nearestChest(0, 64, 0, chests, new boolean[]{true, true, false}), "26 blocks up is another island");
    }

    @Test void bridgesToTheBetterKittedPlayerWhenNotMuchFurther() {
        double[][] players = {{10, 0, 0}, {16, 0, 10}};
        assertEquals(1, SkywarsTactics.richestTarget(0, 0, players));
        assertEquals(0, SkywarsTactics.richestTarget(0, 0, new double[][]{{10, 0, 0}, {70, 0, 30}}), "out of range never wins");
        assertEquals(-1, SkywarsTactics.richestTarget(0, 0, new double[0][]));
    }

    @Test void plansLootThenBridgeThenPace() {
        assertEquals(SkywarsTactics.Plan.LOOT, SkywarsTactics.plan(true, true));
        assertEquals(SkywarsTactics.Plan.BRIDGE, SkywarsTactics.plan(false, true));
        assertEquals(SkywarsTactics.Plan.WANDER, SkywarsTactics.plan(false, false));
        double[] offset = SkywarsTactics.wanderOffset(bound -> 2);
        assertEquals(0, offset[0], 1e-9);
        assertEquals(4, offset[1], 1e-9);
    }

    @Test void pearlsOutOnlyWhenHurtAndPressedAndNotOnEasy() {
        assertTrue(SkywarsTactics.shouldPearlEscape(0.3, 3, true, BotDifficulty.NORMAL));
        assertFalse(SkywarsTactics.shouldPearlEscape(0.3, 3, true, BotDifficulty.EASY));
        assertFalse(SkywarsTactics.shouldPearlEscape(0.8, 3, true, BotDifficulty.HARD));
        assertFalse(SkywarsTactics.shouldPearlEscape(0.3, 9, true, BotDifficulty.HARD));
        assertFalse(SkywarsTactics.shouldPearlEscape(0.3, 3, false, BotDifficulty.HARD));
    }
}
