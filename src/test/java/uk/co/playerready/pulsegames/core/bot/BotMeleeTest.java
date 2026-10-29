package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.util.Cuboid;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class BotMeleeTest {
    static final class Fixed extends Random {
        final double value;
        Fixed(double value) { this.value = value; }
        @Override public double nextDouble() { return value; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return value >= 0.5; }
    }
    final AtomicLong clock = new AtomicLong();
    final UUID id = new UUID(7, 11);
    final BotDifficulty.Profile normal = BotDifficulty.NORMAL.profile(), hard = BotDifficulty.HARD.profile(), easy = BotDifficulty.EASY.profile();
    final Location bot = new Location(null, 0.5, 80, 0.5), target = new Location(null, 0.5, 80, 3.5);
    final Predicate<Location> everywhere = at -> true;

    BotMelee melee(double random) { return new BotMelee(id, new Fixed(random), clock::get); }

    @Test void strafeSideFlipsEveryTwentyToFortyTicks() {
        var melee = melee(0.0); // always strafing, shortest cycle (20 ticks), 2-block steps
        double first = Math.signum(melee.strafeGoal(bot, target, normal, everywhere, false).getX() - bot.getX());
        clock.set(19);
        assertEquals(first, Math.signum(melee.strafeGoal(bot, target, normal, everywhere, false).getX() - bot.getX()));
        clock.set(20);
        assertEquals(-first, Math.signum(melee.strafeGoal(bot, target, normal, everywhere, false).getX() - bot.getX()));
        clock.set(40);
        assertEquals(first, Math.signum(melee.strafeGoal(bot, target, normal, everywhere, false).getX() - bot.getX()));
    }

    @Test void randomCycleLengthStaysInsideTwentyToFortyTicks() {
        for (long seed = 0; seed < 50; seed++) {
            clock.set(0);
            var melee = new BotMelee(id, new Random(seed), clock::get);
            melee.strafeGoal(bot, target, hard, everywhere, true);
            double side = Math.signum(melee.strafeGoal(bot, target, hard, everywhere, true).getX() - bot.getX());
            clock.set(19);
            assertEquals(side, Math.signum(melee.strafeGoal(bot, target, hard, everywhere, true).getX() - bot.getX()), "no flip before 20");
            clock.set(41);
            assertEquals(-side, Math.signum(melee.strafeGoal(bot, target, hard, everywhere, true).getX() - bot.getX()), "flipped by 40");
        }
    }

    @Test void neverChoosesAnUnsafeTile() {
        assertNull(melee(0.0).strafeGoal(bot, target, normal, at -> false, false));
        for (long seed = 0; seed < 100; seed++) {
            var melee = new BotMelee(id, new Random(seed), clock::get);
            Predicate<Location> onlyWest = at -> at.getX() < bot.getX();
            for (long t = 0; t < 200; t += 5) {
                clock.set(t);
                Location goal = melee.strafeGoal(bot, target, hard, onlyWest, true);
                if (goal != null) assertTrue(onlyWest.test(goal));
            }
        }
    }

    @Test void blockedSideFallsBackToTheOtherAndRangedHoldsDistance() {
        var melee = melee(0.0);
        Location goal = melee.strafeGoal(bot, target, normal, at -> at.getX() > bot.getX(), true);
        assertNotNull(goal);
        assertTrue(goal.getX() > bot.getX());
        assertEquals(bot.getZ(), goal.getZ(), 1e-9, "ranged strafing neither closes nor backs off");
    }

    @Test void tooCloseBacksOffAndStandStillCycleHolds() {
        var tooClose = new Location(null, 0.5, 80, 1.0);
        Location goal = melee(0.0).strafeGoal(bot, tooClose, normal, everywhere, false);
        assertTrue(goal.getZ() < bot.getZ(), "backs away from a target inside 1.4 blocks");
        // High random roll > strafe chance: a holding cycle in melee range has no destination at all.
        assertNull(melee(0.99).strafeGoal(bot, new Location(null, 0.5, 80, 2.0), easy, everywhere, false));
    }

    @Test void aimErrorIsBoundedPerTierAndEasyLooksAtTheTorso() {
        Location eye = new Location(null, 0.5, 81.6, 0.5), targetEye = new Location(null, 0.5, 81.6, 8.5);
        for (BotDifficulty tier : BotDifficulty.values()) {
            for (long seed = 0; seed < 100; seed++) {
                var melee = new BotMelee(id, new Random(seed), clock::get);
                Location look = melee.aim(eye, targetEye, tier.profile(), false);
                double angle = Math.toDegrees(look.toVector().subtract(eye.toVector()).angle(targetEye.toVector().subtract(eye.toVector())));
                assertTrue(angle <= tier.profile().aimErrorDegrees() * 1.2 + 0.01, tier + " " + angle);
                assertEquals(8.0, look.toVector().distance(eye.toVector()), 1e-6);
            }
        }
        var zero = new BotDifficulty.Profile(10, 90, 20, 20, 0, 0.5, 0.5, 0.5, 2, 0.3);
        assertEquals(81.6, melee(0.3).aim(eye, targetEye, zero, false).getY(), 1e-9);
        assertTrue(melee(0.3).aim(eye, targetEye, zero, true).getY() < 81.6, "torso is lower than the eyes");
    }

    @Test void aimIsResampledSlowlyNotEveryThink() {
        Location eye = new Location(null, 0, 81, 0), targetEye = new Location(null, 0, 81, 8);
        var melee = new BotMelee(id, new Random(5), clock::get);
        Location a = melee.aim(eye, targetEye, easy, false);
        clock.set(25);
        assertEquals(a, melee.aim(eye, targetEye, easy, false));
        clock.set(65);
        assertNotEquals(a, melee.aim(eye, targetEye, easy, false));
    }

    @Test void missSwingsBesideTheTarget() {
        Location eye = new Location(null, 0, 81, 0), targetEye = new Location(null, 0, 81, 3);
        Location miss = melee(0.7).missPoint(eye, targetEye);
        assertEquals(3, miss.getZ(), 1e-9);
        assertTrue(Math.abs(miss.getX()) >= 0.9 && Math.abs(miss.getX()) <= 1.5);
    }

    @Test void sprintFollowsTierChanceClosesAndTapsAfterEachHit() {
        var h = melee(0.0);
        assertTrue(h.sprint(6, false, hard), "closing sprints");
        assertFalse(h.sprint(2.0, false, hard), "no sprint standing in range between hits");
        assertTrue(h.sprint(2.0, true, hard), "sprints on the hit");
        h.afterHit();
        assertFalse(h.sprint(6, false, hard), "released for one think after a hit");
        clock.set(5);
        assertTrue(h.sprint(6, false, hard));
        // A high roll beats Easy's chance (max 0.56) but not Hard's.
        assertFalse(melee(0.95).sprint(6, false, easy));
        assertTrue(new BotMelee(sprintyId(), new Fixed(0.95), clock::get).sprint(6, false, hard));
    }

    UUID sprintyId() {
        for (long i = 0; ; i++) { var u = new UUID(i, 3); if (BotPersonality.aggression(u) > 0.6) return u; }
    }

    @Test void jumpsPerTier() {
        assertFalse(melee(0.0).jump(2.5, true, true, false, easy), "easy never jumps");
        var h = melee(0.0);
        assertTrue(h.jump(2.5, true, true, false, hard), "crit jump when cooldown is full at 2-3 blocks");
        clock.set(5);
        assertFalse(h.jump(2.5, true, true, false, hard), "not again straight away");
        clock.set(100);
        assertFalse(h.jump(5.0, true, true, false, hard), "too far for a crit");
        assertFalse(h.jump(2.5, false, true, false, hard), "never mid-air");
        assertFalse(h.jump(2.5, true, false, false, hard), "cooldown not ready");
        assertTrue(h.jump(5.0, true, false, true, hard), "jump-reset after being hit");
        assertFalse(melee(0.99).jump(2.5, true, true, false, normal), "a high roll skips the jump");
    }

    @Test void retreatsOnlyBelowThresholdWithAHealingItemAndWithHysteresis() {
        var m = melee(0.0);
        assertFalse(m.retreating(0.31, true, normal));
        assertFalse(m.retreating(0.30, false, normal), "nothing to heal with: keep fighting");
        assertTrue(m.retreating(0.30, true, normal));
        assertTrue(m.retreating(0.45, true, normal), "stays backed off while healing");
        assertFalse(m.retreating(0.55, true, normal), "re-engages once healed");
        assertTrue(m.retreating(0.10, true, normal));
        clock.set(120);
        assertFalse(m.retreating(0.10, true, normal), "gives up after six seconds");
        assertFalse(melee(0.0).retreating(0.25, true, easy), "easy bots hold on longer (20%)");
        assertTrue(melee(0.0).retreating(0.20, true, easy));
    }

    @Test void retreatGoalGoesAwayTriesSidesAndAdmitsBeingCornered() {
        Location goal = melee(0.0).retreatGoal(bot, target, everywhere);
        assertTrue(goal.getZ() < bot.getZ());
        Location side = melee(0.0).retreatGoal(bot, target, at -> at.getZ() > -1.5 || at.getX() != bot.getX() && at.getZ() > -3);
        assertNull(melee(0.0).retreatGoal(bot, target, at -> false));
        assertTrue(side == null || side.getZ() > -4);
    }

    // Ground is every x <= 10: the void starts past x = 10.
    final Predicate<Location> platform = at -> at.getX() <= 10;

    @Test void sumoBotLeavesTheEdgeAndLinesTheTargetUpAgainstIt() {
        Location nearEdge = new Location(null, 9.5, 80, 0.5);
        assertTrue(BotMelee.edgeDirection(nearEdge, platform).getX() > 0.5, "edge is to the east");
        assertNull(BotMelee.edgeDirection(new Location(null, 2, 80, 0), platform), "far from the edge");
        Location inward = melee(0.0).sumoGoal(nearEdge, new Location(null, 5, 80, 0.5), platform);
        assertTrue(inward.getX() < nearEdge.getX() - 2, "steps back from the void");
        Location behindTarget = melee(0.0).sumoGoal(new Location(null, 4, 80, 0.5), new Location(null, 9, 80, 0.5), platform);
        assertEquals(7.2, behindTarget.getX(), 0.5, "goes to the side of the target away from the edge");
        assertNull(melee(0.0).sumoGoal(new Location(null, 1, 80, 0.5), new Location(null, 3, 80, 0.5), platform), "no edge decision mid-platform");
    }

    @Test void kothSpotsSpreadInsideTheHillPerBot() {
        var hill = new Cuboid(0, 60, 0, 9, 65, 9);
        var seen = new java.util.HashSet<String>();
        for (int i = 0; i < 20; i++) {
            Location spot = BotMelee.spreadPoint(hill, null, new UUID(i * 7919L, i));
            assertTrue(spot.getX() >= 0 && spot.getX() <= 10 && spot.getZ() >= 0 && spot.getZ() <= 10);
            seen.add(spot.getBlockX() + "," + spot.getBlockZ());
        }
        assertTrue(seen.size() > 8, "bots do not stack on one block");
    }

    @Test void personalitiesDifferStablyBetweenBots() {
        var a = new UUID(1, 2); var b = new UUID(3, 4);
        assertEquals(BotPersonality.aggression(a), BotPersonality.aggression(a));
        assertNotEquals(BotPersonality.aggression(a), BotPersonality.aggression(b));
        for (int i = 0; i < 500; i++) {
            var u = new UUID(i, i * 31L);
            for (double v : new double[]{BotPersonality.aggression(u), BotPersonality.strafeBias(u), BotPersonality.jumpiness(u)})
                assertTrue(v >= 0 && v < 1);
        }
    }
}
