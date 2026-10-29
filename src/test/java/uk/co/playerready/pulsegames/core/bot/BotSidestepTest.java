package uk.co.playerready.pulsegames.core.bot;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BotSidestepTest {
    private final UUID bot = UUID.randomUUID();

    @Test void stepsToTheFreeSideAndRemembersIt() {
        var s = new BotSidestep();
        // forward is +x; sides are +z (side 1) and -z (side -1)
        var first = s.choose(bot, 1, 0, 0, (dx, dz) -> dz == -1);
        assertEquals(new BotSidestep.Step(0, -1), first);
        // both sides open now: the remembered side wins for a while, so no flip-flopping
        assertEquals(new BotSidestep.Step(0, -1), s.choose(bot, 1, 0, 20, (dx, dz) -> true));
        // memory expires
        assertEquals(new BotSidestep.Step(0, 1), s.choose(bot, 1, 0, 200, (dx, dz) -> true));
    }

    @Test void nullWhenBothSidesAreClosed() {
        assertNull(new BotSidestep().choose(bot, 0, 1, 0, (dx, dz) -> false));
    }

    @Test void sidesArePerpendicularToTheForwardAxis() {
        var s = new BotSidestep();
        var step = s.choose(bot, 0, 1, 0, (dx, dz) -> true);
        assertEquals(0, step.dz()); assertEquals(1, Math.abs(step.dx()));
    }
}
