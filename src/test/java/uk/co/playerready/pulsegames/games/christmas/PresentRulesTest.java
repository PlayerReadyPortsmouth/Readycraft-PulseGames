package uk.co.playerready.pulsegames.games.christmas;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.playerready.pulsegames.games.christmas.PresentRules.*;

class PresentRulesTest {
    private Present toy() { return new Present(Colour.RED, null, false, -1, false); }
    @Test void colourBowAndUnwrappedValuesMatchRules() {
        assertEquals(0, score(toy(), false));
        assertEquals(3, score(toy().wrap(Colour.RED), false));
        assertEquals(6, score(toy().wrap(Colour.RED).withBow(), false));
        assertEquals(1, score(toy().wrap(Colour.BLUE), false));
        assertEquals(1, score(toy().wrap(Colour.BLUE).withBow(), false));
    }
    @Test void stealBonusOnlyStartsAfterCrossTeamPickupAndNeverAccumulatesPerPass() {
        Present owned = toy().wrap(Colour.RED).pickedUp(0);
        assertFalse(owned.stolen());
        assertEquals(3, score(owned.pickedUp(0), false));
        assertEquals(4, score(owned.pickedUp(1), false));
        assertEquals(4, score(owned.pickedUp(1).pickedUp(0).pickedUp(1), false));
        assertEquals(1, score(toy().pickedUp(0).pickedUp(1), false));
        assertEquals(14, score(owned.withBow().pickedUp(1), true));
    }
    @Test void bowsCannotWrapUnfinishedToysAndRewrappingRemovesBow() {
        assertEquals(toy(), toy().withBow());
        assertFalse(toy().wrap(Colour.RED).withBow().wrap(Colour.BLUE).bow());
    }
    @Test void fullCarryStackCanBeWrappedInOrderWithoutLosingEarlierPresents() {
        Deque<Present> stack = new ArrayDeque<>(List.of(toy(), toy().wrap(Colour.RED), toy()));
        assertNotNull(nextUnwrapped(stack)); assertTrue(wrapNext(stack, Colour.RED));
        assertNotNull(nextUnwrapped(stack)); assertTrue(wrapNext(stack, Colour.BLUE));
        assertNull(nextUnwrapped(stack)); assertFalse(wrapNext(stack, Colour.GOLD));
        assertEquals(3, stack.size()); bowAll(stack);
        assertTrue(stack.stream().allMatch(Present::bow));
        assertEquals(13, stack.stream().mapToInt(p -> score(p, false)).sum());
    }
    @Test void rolesCapacitySlownessAndWrappingShortageHaveConcreteEffects() {
        assertEquals(4, capacity(Role.PACKER)); assertEquals(3, capacity(Role.SPEEDY));
        assertTrue(walkSpeed(Role.SPEEDY, 3) > walkSpeed(Role.WRAPPER, 3));
        assertTrue(walkSpeed(Role.PACKER, 4) < walkSpeed(Role.PACKER, 3));
        assertEquals(30, wrapTicks(Role.WRAPPER, false));
        assertEquals(60, wrapTicks(Role.SPEEDY, false));
        assertEquals(120, wrapTicks(Role.PACKER, true));
    }
}
