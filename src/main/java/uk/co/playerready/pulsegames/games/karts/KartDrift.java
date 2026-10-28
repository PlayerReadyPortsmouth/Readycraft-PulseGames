package uk.co.playerready.pulsegames.games.karts;

/**
 * Bounded, server-tick drift charge. Holding jump through a forward corner charges a release boost in
 * three tiers: mini-turbo at 1 s, super at 2.5 s and ultra at 4 s of held drift.
 */
final class KartDrift {
    static final int[] TIER_CHARGE = {20, 50, 80};
    static final int[] TIER_BOOST_TICKS = {20, 30, 40};
    static final double[] TIER_BOOST_FACTOR = {1.40, 1.55, 1.70};
    static final String[] TIER_NAME = {"MINI", "SUPER", "ULTRA"};
    private static final int MAX_CHARGE = 100;
    record Control(KartPhysics.Input input, double slipDegrees, int boostTicks, double boostFactor) {}
    private int charge, direction;
    private boolean held;

    Control update(boolean pressed, KartPhysics.Input input, KartPhysics.State motion, boolean disabled) {
        boolean eligible = !disabled && motion.grounded() && motion.speed() >= 4 && input.throttle() > 0;
        if (!pressed) {
            int released = eligible && held ? tierFor(charge) : 0;
            reset();
            return released == 0 ? new Control(input, 0, 0, 1)
                    : new Control(input, 0, TIER_BOOST_TICKS[released - 1], TIER_BOOST_FACTOR[released - 1]);
        }
        // A held key cannot re-arm after hitting a wall, reversing or changing turn direction.
        if (!held) { held = true; direction = eligible && Math.abs(input.steering()) >= .25 ? (int)Math.signum(input.steering()) : 0; }
        if (!eligible || direction == 0 || input.steering() * direction < -.1) {
            charge = 0; direction = 0;
            return new Control(input, 0, 0, 1);
        }
        charge = Math.min(MAX_CHARGE, charge + 1);
        double steer = direction * Math.clamp(.55 + input.steering() * direction * .45, .2, 1);
        return new Control(new KartPhysics.Input(input.throttle(), steer), direction * 16, 0, 1);
    }
    private static int tierFor(int charge) { return charge >= TIER_CHARGE[2] ? 3 : charge >= TIER_CHARGE[1] ? 2 : charge >= TIER_CHARGE[0] ? 1 : 0; }
    /** 0 = uncharged, 1 = mini, 2 = super, 3 = ultra. */
    int tier() { return tierFor(charge); }
    void cancelCharge() { charge = 0; direction = 0; }
    void reset() { charge = direction = 0; held = false; }
}
