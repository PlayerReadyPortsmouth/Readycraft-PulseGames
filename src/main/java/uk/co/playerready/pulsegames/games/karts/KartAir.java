package uk.co.playerready.pulsegames.games.karts;

/**
 * Ramp flight: launch impulse, air-time cap and the landing trick. Pure; the match applies the results.
 * Only a flight started by a jump pad can trick, so stairs or ledges cannot be used to farm landing boosts.
 */
final class KartAir {
    static final double MIN_LAUNCH_SPEED = 3;
    static final int MAX_AIR_TICKS = 30, TRICK_MIN_AIR_TICKS = 4, TRICK_SPIN_TICKS = 12, PAD_COOLDOWN = 40;
    static final double SPIN_DEGREES_PER_TICK = 360.0 / TRICK_SPIN_TICKS;
    static final double TRICK_FACTOR = 1.35;
    static final int TRICK_BOOST_TICKS = 16;
    /** Fastest forced descent once the air-time cap is reached; matches the physics terminal speed. */
    static final double FORCED_DESCENT = -12;

    /** Result of one simulated tick. {@code spinDegrees} is the cosmetic seat yaw offset. */
    record Step(boolean trickStarted, boolean landed, boolean landedWithTrick, boolean capped, double spinDegrees) {
        static final Step IDLE = new Step(false, false, false, false, 0);
    }
    private boolean flying, tricked;
    private int airTicks, spinTicks = -1;

    boolean flying() { return flying; }

    /** Vertical launch speed in blocks/second: 5 at a standstill up to 12.5 at 1.5x top speed, scaled by power. */
    static double launchSpeed(double speed, double power) {
        double fraction = Math.min(1.5, Math.max(0, Double.isFinite(speed) ? speed : 0) / KartPhysics.MAX_SPEED);
        return (5 + 5 * fraction) * Math.clamp(Double.isFinite(power) ? power : 1, .5, 1.5);
    }

    /** The launched chassis state, or the same state when the kart is too slow or already in the air. */
    KartPhysics.State launch(KartPhysics.State motion, double power) {
        if (flying || !motion.grounded() || motion.speed() < MIN_LAUNCH_SPEED) return motion;
        flying = true; tricked = false; airTicks = 0; spinTicks = -1;
        return new KartPhysics.State(motion.x(), motion.y(), motion.z(), motion.yaw(), motion.speed(),
                launchSpeed(motion.speed(), power), false);
    }

    Step tick(boolean grounded, boolean trickPressed) {
        if (!flying) return Step.IDLE;
        airTicks++;
        // The tick after launch can still touch the pad block; a real landing needs a little air first.
        if (grounded && airTicks >= 2) {
            boolean bonus = tricked;
            cancel();
            return new Step(false, true, bonus, false, 0);
        }
        boolean started = false;
        if (trickPressed && !tricked && airTicks >= TRICK_MIN_AIR_TICKS) { tricked = true; spinTicks = 0; started = true; }
        double spin = 0;
        if (spinTicks >= 0 && spinTicks < TRICK_SPIN_TICKS) { spinTicks++; spin = spinTicks * SPIN_DEGREES_PER_TICK; }
        return new Step(started, false, false, airTicks > MAX_AIR_TICKS, spin % 360);
    }
    void cancel() { flying = tricked = false; airTicks = 0; spinTicks = -1; }
}
