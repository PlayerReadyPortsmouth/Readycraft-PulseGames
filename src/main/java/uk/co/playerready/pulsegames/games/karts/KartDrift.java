package uk.co.playerready.pulsegames.games.karts;

/** Bounded, server-tick drift charge. Holding jump through a forward corner charges a release boost. */
final class KartDrift {
    record Control(KartPhysics.Input input, double slipDegrees, int boostTicks) {}
    private int charge, direction;
    private boolean held;

    Control update(boolean pressed, KartPhysics.Input input, KartPhysics.State motion, boolean disabled) {
        boolean eligible = !disabled && motion.grounded() && motion.speed() >= 4 && input.throttle() > 0;
        if (!pressed) {
            int boost = eligible && held ? charge >= 25 ? 30 : charge >= 12 ? 16 : 0 : 0;
            reset();
            return new Control(input, 0, boost);
        }
        // A held key cannot re-arm after hitting a wall, reversing or changing turn direction.
        if (!held) { held = true; direction = eligible && Math.abs(input.steering()) >= .25 ? (int)Math.signum(input.steering()) : 0; }
        if (!eligible || direction == 0 || input.steering() * direction < -.1) {
            charge = 0; direction = 0;
            return new Control(input, 0, 0);
        }
        charge = Math.min(60, charge + 1);
        double steer = direction * Math.clamp(.55 + input.steering() * direction * .45, .2, 1);
        return new Control(new KartPhysics.Input(input.throttle(), steer), direction * 16, 0);
    }
    int tier() { return charge >= 25 ? 2 : charge >= 12 ? 1 : 0; }
    void cancelCharge() { charge = 0; direction = 0; }
    void reset() { charge = direction = 0; held = false; }
}
