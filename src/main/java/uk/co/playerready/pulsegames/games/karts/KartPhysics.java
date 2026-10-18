package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** Fixed 20 Hz chassis simulation. No native vehicle physics or chunk generation. */
public final class KartPhysics {
    // Covers the 1.5 x 1.875 model at every yaw (diagonal approximately 2.4012).
    public static final double WIDTH = 2.41, HEIGHT = 1.8, STEP_HEIGHT = .6;
    public static final double BODY_WIDTH = 1.5, BODY_LENGTH = 1.875;
    public static final double MAX_SPEED = 12, REVERSE_SPEED = 3.5;
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
        default boolean clearAt(double x, double y, double z, float yaw) {
            return clear(x,y,z,WIDTH,HEIGHT);
        }
    }

    public static State tick(State state, Input input, double speedMultiplier, CollisionSpace space) {
        return tick(state,input,speedMultiplier,space,KartStyle.BALANCED);
    }

    public static State tick(State state, Input input, double speedMultiplier, CollisionSpace space, KartStyle style) {
        return tick(state, input, speedMultiplier, space, style, 0);
    }

    public static State tick(State state, Input input, double speedMultiplier, CollisionSpace space, KartStyle style,
                             double slipDegrees) {
        double multiplier = finiteClamp(speedMultiplier, 0, 2);
        double speed = state.speed();
        if (input.throttle() == 0) speed = approach(speed, 0, 5 * DT);
        else if (speed * input.throttle() < 0) speed = approach(speed, 0, 22 * DT);
        else speed += input.throttle() * 9 * style.acceleration() * DT;
        speed = Math.clamp(speed, -REVERSE_SPEED * multiplier, MAX_SPEED * multiplier * style.speed());
        float yaw = (float) ((state.yaw() + input.steering() * 126 * style.steering() * DT
                * Math.min(1, Math.abs(speed) / 2) * Math.signum(speed)) % 360);
        double x = state.x(), y = state.y(), z = state.z();
        double turn = ((yaw-state.yaw()+540)%360)-180;
        int turnSteps = Math.max(1,(int)Math.ceil(Math.abs(turn)));
        for(int i=1;i<=turnSteps;i++) {
            if(!space.clearAt(x,y,z,(float)(state.yaw()+turn*i/turnSteps))) { yaw=state.yaw(); break; }
        }
        boolean grounded = !space.clearAt(x, y - .03, z, yaw);
        double heading = yaw - finiteClamp(slipDegrees, -20, 20);
        double dx = -Math.sin(Math.toRadians(heading)) * speed * DT;
        double dz = Math.cos(Math.toRadians(heading)) * speed * DT;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / SUBSTEP));
        for (int i = 0; i < steps; i++) {
            double nx = x + dx / steps, nz = z + dz / steps;
            if (space.clearAt(nx, y, nz, yaw)) { x = nx; z = nz; continue; }
            // A small step is permitted only from supported ground, with clearance above.
            boolean stepped = false;
            if (grounded) {
                for (double lift = .1; lift <= STEP_HEIGHT + EPSILON; lift += .1) {
                    if (!space.clearAt(x, y + lift, z, yaw)) break;
                    if (space.clearAt(nx, y + lift, nz, yaw)) {
                        x = nx; z = nz; y += lift; stepped = true; break;
                    }
                }
            }
            if (!stepped) {
                // Keep the safe tangent of a shallow scrape, never the blocked component.
                // Require exactly one free axis: inside corners and head-on impacts stop.
                boolean xClear = space.clearAt(nx, y, z, yaw);
                boolean zClear = space.clearAt(x, y, nz, yaw);
                double length = Math.hypot(dx, dz);
                if (xClear != zClear && length > EPSILON) {
                    double tangent = xClear ? Math.abs(dx) : Math.abs(dz);
                    double normal = xClear ? Math.abs(dz) : Math.abs(dx);
                    if (tangent > normal * 1.5) {
                        double slideX = xClear ? dx * .85 : 0;
                        double slideZ = zClear ? dz * .85 : 0;
                        double sx = x + slideX / steps, sz = z + slideZ / steps;
                        if (space.clearAt(sx, y, sz, yaw)) {
                            speed *= tangent / length * .85;
                            dx = slideX; dz = slideZ;
                            x = sx; z = sz;
                            continue;
                        }
                    }
                }
                speed = 0;
                break;
            }
        }
        double vertical = grounded ? 0 : Math.max(-12, state.verticalSpeed() - 24 * DT);
        // Always probe down, including after climbing a slab or leaving an edge.
        if (vertical == 0) vertical = -24 * DT;
        double dy = vertical * DT;
        int verticalSteps = Math.max(1, (int) Math.ceil(Math.abs(dy) / SUBSTEP));
        grounded = false;
        for (int i = 0; i < verticalSteps; i++) {
            double move = dy / verticalSteps;
            if (space.clearAt(x, y + move, z, yaw)) { y += move; continue; }
            double low = 0, high = 1;
            for (int j = 0; j < 14; j++) {
                double mid = (low + high) / 2;
                if (space.clearAt(x, y + move * mid, z, yaw)) low = mid; else high = mid;
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
        return new CollisionSpace() {
            @Override public boolean clear(double x,double y,double z,double width,double height) {
                return clearShape(x,y,z,0,width,width,height);
            }
            @Override public boolean clearAt(double x,double y,double z,float yaw) {
                return clearShape(x,y,z,yaw,BODY_WIDTH,BODY_LENGTH,HEIGHT);
            }
            private boolean clearShape(double x,double y,double z,float yaw,double width,double length,double height) {
                if (!Double.isFinite(x+y+z+yaw) || y < world.getMinHeight() || y+height >= world.getMaxHeight()) return false;
                double radians=Math.toRadians(yaw), c=Math.cos(radians), s=Math.sin(radians);
                double ex=(Math.abs(c)*width+Math.abs(s)*length)/2;
                double ez=(Math.abs(s)*width+Math.abs(c)*length)/2;
                int minX=(int)Math.floor(x-ex+EPSILON),maxX=(int)Math.floor(x+ex-EPSILON);
                int minZ=(int)Math.floor(z-ez+EPSILON),maxZ=(int)Math.floor(z+ez-EPSILON);
                for(int cx=minX>>4;cx<=maxX>>4;cx++) for(int cz=minZ>>4;cz<=maxZ>>4;cz++)
                    if(!world.isChunkLoaded(cx,cz))return false;
                for(int bx=minX;bx<=maxX;bx++)for(int bz=minZ;bz<=maxZ;bz++)
                    for(int by=(int)Math.floor(y);by<=Math.floor(y+height-EPSILON);by++) {
                        Block block=world.getBlockAt(bx,by,bz);
                        if(block.isLiquid())return false;
                        if(block.isPassable())continue;
                        BoundingBox box=block.getBoundingBox();
                        if(box.getMaxY()>y && box.getMinY()<y+height-EPSILON
                                && rectangles(x,z,yaw,width,length,(box.getMinX()+box.getMaxX())/2,
                                (box.getMinZ()+box.getMaxZ())/2,0,box.getWidthX(),box.getWidthZ()))return false;
                    }
                return true;
            }
        };
    }
    /** Separating-axis test; touching chassis edges do not overlap. */
    public static boolean overlaps(State a, State b) {
        return Math.abs(a.y()-b.y())<HEIGHT-EPSILON && rectangles(a.x(),a.z(),a.yaw(),BODY_WIDTH,BODY_LENGTH,
                b.x(),b.z(),b.yaw(),BODY_WIDTH,BODY_LENGTH);
    }
    private static boolean rectangles(double ax,double az,float ay,double aw,double al,
                                      double bx,double bz,float by,double bw,double bl) {
        double ac=Math.cos(Math.toRadians(ay)),as=Math.sin(Math.toRadians(ay));
        double bc=Math.cos(Math.toRadians(by)),bs=Math.sin(Math.toRadians(by));
        double[][] axes={{ac,as},{-as,ac},{bc,bs},{-bs,bc}};
        for(double[] axis:axes) {
            double separation=Math.abs((bx-ax)*axis[0]+(bz-az)*axis[1]);
            double ar=(aw*Math.abs(ac*axis[0]+as*axis[1])+al*Math.abs(-as*axis[0]+ac*axis[1]))/2;
            double br=(bw*Math.abs(bc*axis[0]+bs*axis[1])+bl*Math.abs(-bs*axis[0]+bc*axis[1]))/2;
            if(separation>=ar+br-EPSILON)return false;
        }
        return true;
    }
    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private static double finiteClamp(double value, double low, double high) {
        return Double.isFinite(value) ? Math.clamp(value, low, high) : 0;
    }
}
