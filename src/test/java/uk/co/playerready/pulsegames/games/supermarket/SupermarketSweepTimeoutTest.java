package uk.co.playerready.pulsegames.games.supermarket;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupermarketSweepTimeoutTest {
    @Test void nobodyWinsWithZeroItems() {
        assertEquals(List.of(), SupermarketSweepGame.topCollectors(Map.of("a", 0, "b", 0)));
        assertEquals(List.of(), SupermarketSweepGame.topCollectors(Map.of()));
    }

    @Test void tiesShareFirstAndTheFirstJoinerIsNotFavoured() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("early", 3);
        counts.put("late", 3);
        counts.put("behind", 1);
        assertEquals(List.of("early", "late"), SupermarketSweepGame.topCollectors(counts));
    }
}
