package uk.co.playerready.pulsegames.games.supermarket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutPointerTest {
    // Lantern Exchange checkout: x -6..6, y 81..84, z 28..32
    private static double dist(double x, double y, double z) {
        return CheckoutPointer.distance(-6, 81, 28, 6, 84, 32, x, y, z);
    }

    @Test void insideUsesBlockBox() {
        assertTrue(CheckoutPointer.inside(-6, 81, 28, 6, 84, 32, 0.5, 81.0, 30.5));
        assertTrue(CheckoutPointer.inside(-6, 81, 28, 6, 84, 32, 6.9, 84.5, 32.9));
        assertFalse(CheckoutPointer.inside(-6, 81, 28, 6, 84, 32, 0.5, 81.0, 0.5));
        assertFalse(CheckoutPointer.inside(-6, 81, 28, 6, 84, 32, -6.1, 81.0, 30.5));
    }

    @Test void distanceIsToNearestPoint() {
        assertEquals(0, dist(0.5, 81, 30.5), 1e-9);
        assertEquals(27.5, dist(0.5, 81, 0.5), 1e-9); // nearest edge z = 28
        assertEquals(Math.hypot(4, 3), dist(-10, 81, 25), 1e-9);
        assertEquals(9, dist(0.5, 94, 30.5), 1e-9); // above the box top at y 85
    }

    @Test void directionIsRelativeToYaw() {
        // Target due south (+z). Yaw 0 faces south.
        assertEquals("ahead", CheckoutPointer.direction(0, 30, 0f));
        assertEquals("behind", CheckoutPointer.direction(0, 30, 180f));
        // Facing west (yaw 90), south is on the player's left.
        assertEquals("left", CheckoutPointer.direction(0, 30, 90f));
        assertEquals("right", CheckoutPointer.direction(0, 30, -90f));
        // Target south-west, facing south: ahead and to the right (west is the right of a south-facing player).
        assertEquals("ahead right", CheckoutPointer.direction(-20, 20, 0f));
        assertEquals("here", CheckoutPointer.direction(0, 0, 45f));
    }

    @Test void lineSwitchesToAtTheCheckout() {
        assertEquals("<green>At the checkout", CheckoutPointer.line(true, 0, "here"));
        assertTrue(CheckoutPointer.line(false, 22.6, "ahead").contains("23m"));
        assertTrue(CheckoutPointer.line(false, 22.6, "ahead").contains("ahead"));
    }

    @Test void perimeterWalksTheEdge() {
        assertArrayEquals(new double[]{0, 0}, SupermarketSweepGame.perimeterPoint(0, 0, 4, 2, 0), 1e-9);
        assertArrayEquals(new double[]{4, 0}, SupermarketSweepGame.perimeterPoint(0, 0, 4, 2, 4.0 / 12), 1e-9);
        assertArrayEquals(new double[]{4, 2}, SupermarketSweepGame.perimeterPoint(0, 0, 4, 2, 6.0 / 12), 1e-9);
        assertArrayEquals(new double[]{0, 2}, SupermarketSweepGame.perimeterPoint(0, 0, 4, 2, 10.0 / 12), 1e-9);
    }
}
