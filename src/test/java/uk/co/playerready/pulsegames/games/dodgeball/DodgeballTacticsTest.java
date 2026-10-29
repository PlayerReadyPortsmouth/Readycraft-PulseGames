package uk.co.playerready.pulsegames.games.dodgeball;

import org.junit.jupiter.api.Test;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;
import static org.junit.jupiter.api.Assertions.*;

class DodgeballTacticsTest {
    @Test void leadsARunnerAndCapsTheLead() {
        double[] still = DodgeballTactics.leadAim(new double[]{0, 1.6, 0}, new double[]{10, 1, 0}, new double[]{0, 0, 0});
        assertEquals(10, still[0], 1e-9);
        assertTrue(still[1] > 1, "aims a little high for the drop");
        double[] running = DodgeballTactics.leadAim(new double[]{0, 1.6, 0}, new double[]{10, 1, 0}, new double[]{0, 0, 0.25});
        assertTrue(running[2] > 1 && running[2] <= 4.0001);
        assertEquals(4, DodgeballTactics.leadAim(new double[]{0, 0, 0}, new double[]{30, 0, 0}, new double[]{0, 0, 0.6})[2], 1e-9);
    }

    @Test void aimErrorStaysInsideTheTierCone() {
        double[] from = {0, 1.6, 0}, aim = {10, 1.6, 0};
        var profile = BotDifficulty.EASY.profile();
        double[] off = DodgeballTactics.withAimError(from, aim, profile, bound -> bound - 1); // the maximum error
        double angle = Math.toDegrees(Math.atan2(off[2], off[0]));
        assertEquals(profile.aimErrorDegrees(), angle, 0.01);
        double[] exact = DodgeballTactics.withAimError(from, aim, profile, bound -> bound / 2);
        assertEquals(10, exact[0], 1e-6);
        assertEquals(0, exact[2], 1e-6);
    }

    @Test void spotsABallThatWillHitAndStepsOutOfItsWay() {
        double[] body = {10, 64, 0};
        // A ball flying along +x straight at the bot, 9 blocks away (about 5 ticks).
        int side = DodgeballTactics.incoming(new double[]{1, 65, 0.2}, new double[]{1.7, 0, 0}, body);
        assertNotEquals(0, side);
        double[] step = DodgeballTactics.sidestep(body, new double[]{1.7, 0, 0}, side);
        assertEquals(10, step[0], 1e-9, "sidesteps across the flight, not along it");
        assertEquals(3, Math.abs(step[1]), 1e-9);
        assertEquals(0, DodgeballTactics.incoming(new double[]{1, 65, 4}, new double[]{1.7, 0, 0}, body), "passes wide");
        assertEquals(0, DodgeballTactics.incoming(new double[]{20, 65, 0}, new double[]{1.7, 0, 0}, body), "moving away");
    }

    @Test void reactionOddsFollowTheTierAndRangeFollowsNumbers() {
        assertTrue(DodgeballTactics.reacts(BotDifficulty.HARD, bound -> 90));
        assertFalse(DodgeballTactics.reacts(BotDifficulty.EASY, bound -> 90));
        assertTrue(DodgeballTactics.holdRange(1, 3) > DodgeballTactics.holdRange(3, 1));
        assertEquals(-1, DodgeballTactics.range(4, 10));
        assertEquals(1, DodgeballTactics.range(20, 10));
        assertEquals(0, DodgeballTactics.range(11, 10));
    }
}
