package uk.co.playerready.pulsegames.core.bot;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirms the review's inference by simulation: with the old gating a precise-mode bot walks one block and then idles.
 * The walker moves at 0.215 blocks/tick (about 4.3 blocks/s), thinks every 5 ticks, and obeys BotPace exactly as
 * BotService.navigate does. This is a model of the gating, not a measurement in game.
 */
class BotPaceTest {
    private static final double WALK = 0.215;
    private static final int NORMAL_PATH_TICKS = 20, JITTER = 2, TICKS = 400;

    /** @param precise false reproduces the old behaviour (fixed interval, no arrival re-issue). */
    private static double blocksPer20Ticks(boolean precise, int lookAheadCells) {
        double x = 0.5; Double dest = null; long nextPath = 0;
        for (long tick = 0; tick < TICKS; tick++) {
            if (tick % 5 == 0) {
                double goal = Math.floor(x) + 0.5 + lookAheadCells;
                boolean arrived = precise && dest != null && BotPace.arrived(new Location(null, x, 0, 0.5), new Location(null, dest, 0, 0.5));
                if (BotPace.mayIssue(precise, tick, nextPath, arrived) && (dest == null || Math.abs(dest - goal) > 0.1)) {
                    dest = goal;
                    nextPath = tick + BotPace.interval(precise, NORMAL_PATH_TICKS, JITTER);
                }
            }
            if (dest != null && Math.abs(dest - x) > 0.15) x += Math.min(WALK, dest - x);
        }
        return (x - 0.5) / TICKS * 20;
    }

    @Test void oldGatingIsOneBlockThenIdle() {
        assertTrue(blocksPer20Ticks(false, 1) < 1.2, "old pace was " + blocksPer20Ticks(false, 1));
    }

    @Test void arrivalReissueAndLookAheadWalkAtNearlyFullSpeed() {
        double after = blocksPer20Ticks(true, 3);
        assertTrue(after > 3.5 && after <= WALK * 20 + 0.01, "new pace was " + after);
        assertTrue(after > 3 * blocksPer20Ticks(false, 1));
    }

    @Test void arrivalAloneAlreadyBeatsTheOldGate() {
        assertTrue(blocksPer20Ticks(true, 1) > 2 * blocksPer20Ticks(false, 1));
    }

    @Test void preciseIntervalIsOneThinkOtherwiseThePathInterval() {
        assertEquals(5, BotPace.interval(true, 35, 5));
        assertEquals(22, BotPace.interval(false, 20, 2));
    }
}
