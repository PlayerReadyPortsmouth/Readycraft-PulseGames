package uk.co.playerready.pulsegames.core.ui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmLayoutTest {
    @Test void yesSummaryNoSitInTheMiddleRowInOrder() {
        assertEquals(27, ConfirmLayout.ROWS * 9);
        assertEquals(List.of(11, 13, 15), List.of(ConfirmLayout.YES, ConfirmLayout.SUMMARY, ConfirmLayout.NO));
        assertEquals(1, ConfirmLayout.SUMMARY / 9);
    }

    @Test void dangerChangesTheYesColourOnly() {
        assertEquals(Material.LIME_CONCRETE, ConfirmLayout.yesMaterial(false));
        assertEquals(Material.ORANGE_CONCRETE, ConfirmLayout.yesMaterial(true));
    }

    @Test void summaryDropsBlanksAndIsCapped() {
        assertEquals(List.of("a", "b"), ConfirmLayout.summary(Arrays.asList("a", null, " ", "b")));
        assertEquals(ConfirmLayout.MAX_SUMMARY, ConfirmLayout.summary(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9")).size());
    }
}
