package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** Fixed 20 Hz chassis simulation. No native vehicle physics or chunk generation. */
public final class KartPhysics {
    // Covers the 1.5 x 1.875 model at every yaw (diagonal approximately 2.4012).
    public static final double WIDTH = 2.41, HEIGHT = 1.8, STEP_HEIGHT = .6;
    public static final double MAX_SPEED = 10, REVERSE_SPEED = 3.5;
    private static final double DT = .05, SUBSTEP = .1, EPSILON = .00001;
    private KartPhysics() {}

    /** Position is the centre of the chassis floor; speed is blocks/second. */
    public record State(double x, double y, double z, float yaw, double speed,
                        double verticalSpeed, boolean grounded) {}
    /** Positive throttle drives forward; positive steering turns right. */
    public record Input(double throttle, double steering) {
        public Input { throttle = finiteClamp(throttle, -1, 1); steering = finiteClamp(steering, -1, 1); }
    }
    @FunctionalInterface public interface CollisionSpace {
        boolean clear(double x, double y, double z, double width, double height);
    }

    public static State tick(State state, Input input, double speedMultiplier, CollisionSpace space) {
        double multiplier = finiteClamp(speedMultiplier, 0, 2);
        double speed = state.speed();
        if (input.throttle() == 0) speed = approach(speed, 0, 5 * DT);
        else if (speed * input.throttle() < 0) speed = approach(speed, 0, 22 * DT);
        else speed += input.throttle() * 9 * DT;
        speed = Math.clamp(speed, -REVERSE_SPEED * multiplier, MAX_SPEED * multiplier);
        float yaw = (float) ((state.yaw() + input.steering() * 105 * DT
                * Math.min(1, Math.abs(speed) / 2) * Math.signum(speed)) % 360);
        double x = state.x(), y = state.y(), z = state.z();
        boolean grounded = !space.clear(x, y - .03, z, WIDTH, HEIGHT);
        double dx = -Math.sin(Math.toRadians(yaw)) * speed * DT;
        double dz = Math.cos(Math.toRadians(yaw)) * speed * DT;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / SUBSTEP));
        for (int i = 0; i < steps; i++) {
            double nx = x + dx / steps, nz = z + dz / steps;
            if (space.clear(nx, y, nz, WIDTH, HEIGHT)) { x = nx; z = nz; continue; }
            // A small step is permitted only from supported ground, with clearance above.
            boolean stepped = false;
            if (grounded) {
                for (double lift = .1; lift <= STEP_HEIGHT + EPSILON; lift += .1) {
                    if (!space.clear(x, y + lift, z, WIDTH, HEIGHT)) break;
                    if (space.clear(nx, y + lift, nz, WIDTH, HEIGHT)) {
                        x = nx; z = nz; y += lift; stepped = true; break;
                    }
                }
            }
            if (!stepped) { speed = 0; break; }
        }
        double vertical = grounded ? 0 : Math.max(-12, state.verticalSpeed() - 24 * DT);
        // Always probe down, including after climbing a slab or leaving an edge.
        if (vertical == 0) vertical = -24 * DT;
        double dy = vertical * DT;
        int verticalSteps = Math.max(1, (int) Math.ceil(Math.abs(dy) / SUBSTEP));
        grounded = false;
        for (int i = 0; i < verticalSteps; i++) {
            double move = dy / verticalSteps;
            if (space.clear(x, y + move, z, WIDTH, HEIGHT)) { y += move; continue; }
            double low = 0, high = 1;
            for (int j = 0; j < 14; j++) {
                double mid = (low + high) / 2;
                if (space.clear(x, y + move * mid, z, WIDTH, HEIGHT)) low = mid; else high = mid;
            }
            y += move * low;
            grounded = move < 0;
            vertical = 0;
            break;
        }
        return new State(x, y, z, yaw, speed, vertical, grounded);
    }

    /** Conservative world-space block bounds support slabs and reject unloaded space. */
    public static CollisionSpace world(World world) {
        return (x, y, z, width, height) -> {
            if (!Double.isFinite(x + y + z) || y < world.getMinHeight() || y + height >= world.getMaxHeight()) return false;
            BoundingBox chassis = new BoundingBox(x - width / 2 + EPSILON, y + EPSILON,
                    z - width / 2 + EPSILON, x + width / 2 - EPSILON, y + height - EPSILON, z + width / 2 - EPSILON);
            int minX = (int) Math.floor(chassis.getMinX()), maxX = (int) Math.floor(chassis.getMaxX());
            int minZ = (int) Math.floor(chassis.getMinZ()), maxZ = (int) Math.floor(chassis.getMaxZ());
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
                    if (!world.isChunkLoaded(cx, cz)) return false;
            for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++)
                for (int by = (int) Math.floor(chassis.getMinY()); by <= Math.floor(chassis.getMaxY()); by++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (block.isLiquid()) return false;
                    if (!block.isPassable() && chassis.overlaps(block.getBoundingBox())) return false;
                }
            return true;
        };
    }
    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private static double finiteClamp(double value, double low, double high) {
        return Double.isFinite(value) ? Math.clamp(value, low, high) : 0;
    }
}
