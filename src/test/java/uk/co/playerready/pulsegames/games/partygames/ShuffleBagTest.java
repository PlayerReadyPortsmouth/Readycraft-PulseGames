package uk.co.playerready.pulsegames.games.partygames;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShuffleBagTest {
    @Test void drawsEveryItemOnceBeforeAnyRepeat() {
        var bag = new ShuffleBag<>(List.of(1, 2, 3, 4, 5, 6, 7, 8), new Random(3));
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) assertTrue(seen.add(bag.draw()));
        assertEquals(8, seen.size());
    }

    @Test void refillNeverStartsWithThePreviousDraw() {
        for (long seed = 0; seed < 200; seed++) {
            var bag = new ShuffleBag<>(List.of("a", "b", "c"), new Random(seed));
            String last = null;
            for (int i = 0; i < 30; i++) {
                String next = bag.draw();
                assertNotEquals(last, next);
                last = next;
            }
        }
    }

    @Test void emptyBagAndSingleItemAreSafe() {
        assertNull(new ShuffleBag<Integer>(List.of(), new Random(1)).draw());
        var one = new ShuffleBag<>(List.of("x"), new Random(1));
        assertEquals("x", one.draw());
        assertEquals("x", one.draw());
    }
}
