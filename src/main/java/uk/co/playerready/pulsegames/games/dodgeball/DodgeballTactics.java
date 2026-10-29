package uk.co.playerready.pulsegames.games.dodgeball;

import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

import java.util.function.IntUnaryOperator;

/**
 * Pure Dodgeball bot reasoning: aim with lead, spot a ball that will reach us, pick a side to step to and a range to
 * hold. Vectors are {x, y, z} in blocks and blocks per tick. No Bukkit.
 */
public final class DodgeballTactics {
    private DodgeballTactics() {}

    /** Blocks per tick of a thrown ball (DodgeballGame launches at 1.7). */
    public static final double BALL_SPEED = 1.7;
    private static final double GRAVITY = 0.03;
    /** Longest lead (in blocks) a bot will aim in front of a runner; beyond that it is guessing. */
    private static final double MAX_LEAD = 4;

    /**
     * Where to aim at a target moving at {@code velocity} (blocks per tick): their position at the time the ball
     * arrives, lead capped at four blocks, plus a lift for the ball's drop. Velocity is sampled from positions by the caller.
     */
    public static double[] leadAim(double[] from, double[] target, double[] velocity) {
        double distance = dist(from, target);
        double flight = distance / BALL_SPEED;
        double[] lead = {velocity[0] * flight, 0, velocity[2] * flight};
        double length = Math.hypot(lead[0], lead[2]);
        if (length > MAX_LEAD) { lead[0] *= MAX_LEAD / length; lead[2] *= MAX_LEAD / length; }
        double drop = 0.5 * GRAVITY * flight * flight;
        return new double[]{target[0] + lead[0], target[1] + drop, target[2] + lead[2]};
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** The aim point turned by up to {@code profile.aimErrorDegrees()} of yaw and half that of pitch about {@code from}. */
    public static double[] withAimError(double[] from, double[] aim, BotDifficulty.Profile profile, IntUnaryOperator random) {
        double yaw = (random.applyAsInt(2001) - 1000) / 1000.0 * Math.toRadians(profile.aimErrorDegrees());
        double pitch = (random.applyAsInt(2001) - 1000) / 1000.0 * Math.toRadians(profile.aimErrorDegrees()) * 0.5;
        double dx = aim[0] - from[0], dy = aim[1] - from[1], dz = aim[2] - from[2];
        double flat = Math.hypot(dx, dz), length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double heading = Math.atan2(dz, dx) + yaw, elevation = Math.atan2(dy, flat) + pitch;
        return new double[]{from[0] + Math.cos(elevation) * Math.cos(heading) * length,
                from[1] + Math.sin(elevation) * length, from[2] + Math.cos(elevation) * Math.sin(heading) * length};
    }

    /**
     * Whether a ball (position and velocity per tick) will pass within 1.3 blocks of {@code body} within 14 ticks.
     * Returns the sign of the step that gets out of its way (+1 left of its flight, -1 right) or 0 if it will miss.
     */
    public static int incoming(double[] ball, double[] velocity, double[] body) {
        double speed2 = velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2];
        if (speed2 < 0.01) return 0;
        double rx = body[0] - ball[0], ry = body[1] + 1 - ball[1], rz = body[2] - ball[2];
        double t = (rx * velocity[0] + ry * velocity[1] + rz * velocity[2]) / speed2;
        if (t <= 0 || t > 14) return 0;
        double mx = rx - velocity[0] * t, my = ry - velocity[1] * t, mz = rz - velocity[2] * t;
        if (Math.sqrt(mx * mx + my * my + mz * mz) > 1.3) return 0;
        // Which side of the flight line we are on decides which way to step: away from the line.
        double side = velocity[0] * rz - velocity[2] * rx;
        return side >= 0 ? 1 : -1;
    }

    /** A bot sees and reacts to an incoming ball this often: Hard nearly always, Easy about a third of the time. */
    public static boolean reacts(BotDifficulty tier, IntUnaryOperator random) {
        int percent = switch (tier) { case EASY -> 35; case NORMAL -> 70; case HARD -> 95; };
        return random.applyAsInt(100) < percent;
    }

    /**
     * A point three blocks sideways from {@code body}, perpendicular to a ball's horizontal flight, on the
     * {@code side} given by {@link #incoming}.
     */
    public static double[] sidestep(double[] body, double[] velocity, int side) {
        double length = Math.hypot(velocity[0], velocity[2]);
        if (length < 0.001) return new double[]{body[0] + 3 * side, body[2]};
        // Perpendicular to the flight; a positive side is the side the cross product placed us on.
        return new double[]{body[0] + (velocity[2] / length) * -3 * side, body[2] + (velocity[0] / length) * 3 * side};
    }

    /** Preferred distance to hold: further back when outnumbered, closer when ahead, to keep off the front line. */
    public static double holdRange(int allies, int enemies) {
        return enemies > allies ? 14 : allies > enemies ? 8 : 10;
    }

    /** -1 to back off, +1 to close in, 0 to hold range and strafe. */
    public static int range(double distance, double hold) {
        return distance < hold - 3 ? -1 : distance > hold + 3 ? 1 : 0;
    }
}
