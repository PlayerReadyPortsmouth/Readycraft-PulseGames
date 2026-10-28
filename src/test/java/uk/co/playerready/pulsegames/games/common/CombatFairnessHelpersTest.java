package uk.co.playerready.pulsegames.games.common;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CombatFairnessHelpersTest {
    private Location at(double x, double z) { return new Location(null, x, 64, z); }

    @Test void safeRespawnNeverPicksTheSpawnBesideAnEnemy() {
        List<Location> anchors = List.of(at(0, 0), at(50, 0), at(100, 0));
        List<Location> enemies = List.of(at(1, 1));
        for (long seed = 0; seed < 100; seed++) {
            // Three anchors -> pool of one: always the farthest.
            assertEquals(2, SafeRespawn.pick(anchors, enemies, new Random(seed)));
        }
    }

    @Test void safeRespawnHandlesNoEnemiesAndNoAnchors() {
        assertEquals(-1, SafeRespawn.pick(List.of(), List.of(at(0, 0)), new Random(1)));
        int pick = SafeRespawn.pick(List.of(at(0, 0), at(9, 9)), List.of(), new Random(1));
        assertTrue(pick == 0 || pick == 1);
    }

    @Test void safeRespawnRandomisesAmongTheFarthestThird() {
        List<Location> anchors = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) anchors.add(at(i * 10, 0));
        List<Location> enemies = List.of(at(0, 0));
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (long seed = 0; seed < 200; seed++) seen.add(SafeRespawn.pick(anchors, enemies, new Random(seed)));
        assertEquals(java.util.Set.of(6, 7, 8), seen);
    }

    @Test void spawnProtectionLastsTwoSecondsThenExpires() {
        AtomicLong now = new AtomicLong(0);
        SpawnProtection protection = new SpawnProtection(now::get);
        UUID id = UUID.randomUUID();
        assertFalse(protection.isProtected(id));
        protection.protect(id, 2);
        now.set(1_900_000_000L);
        assertTrue(protection.isProtected(id));
        now.set(2_000_000_000L);
        assertFalse(protection.isProtected(id));
        protection.protect(id, 2);
        protection.clear(id);
        assertFalse(protection.isProtected(id));
    }

    @Test void assistsCountRecentOtherDamagersOnce() {
        AssistTracker tracker = new AssistTracker(8_000);
        UUID victim = UUID.randomUUID(), killer = UUID.randomUUID(), helper = UUID.randomUUID(), old = UUID.randomUUID();
        tracker.hit(victim, old, 0);
        tracker.hit(victim, helper, 5_000);
        tracker.hit(victim, killer, 9_000);
        assertEquals(List.of(helper), tracker.assisters(victim, killer, 10_000));
        assertTrue(tracker.assisters(victim, killer, 10_000).isEmpty()); // consumed
    }
}
