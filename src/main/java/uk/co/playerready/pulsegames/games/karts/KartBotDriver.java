package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import java.util.UUID;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

/** Pure 20 Hz driver: supplies ordinary controls, never moves a kart or changes its physics. */
public final class KartBotDriver {
    public static final double ARRIVAL_RADIUS = 1.5;
    public record Point(double x, double y, double z) {}
    public record Decision(KartPhysics.Input input, boolean reset, boolean useTurboSafe) {}
    private final double preference;
    private int stuck, reverseTicks, recoveryCount, ticks;
    private double lastX = Double.NaN, lastZ, heldSteering;
    public KartBotDriver(UUID identity) {
        preference = (Math.floorMod(identity.hashCode(), 1001) / 1000.0 - .5) * .12;
    }
    public void reset() { stuck = reverseTicks = recoveryCount = ticks = 0; lastX = Double.NaN; heldSteering = 0; }
    public int stuckTicks() { return stuck; }
    public boolean recovering() { return reverseTicks > 0; }

    public Decision update(KartPhysics.State state, Point target, Point following,
                           BotDifficulty difficulty, KartPhysics.CollisionSpace terrain,
                           List<KartPhysics.State> opponents) {
        ticks++;
        if (Double.isFinite(lastX) && Math.hypot(state.x()-lastX,state.z()-lastZ) < .025) stuck++;
        else { stuck = Math.max(0, stuck-3); if (state.speed() > 3) recoveryCount = 0; }
        lastX = state.x(); lastZ = state.z();
        double desired = Math.toDegrees(Math.atan2(-(target.x()-state.x()), target.z()-state.z()));
        double error = angle(desired-state.yaw());
        if (stuck >= 45 && reverseTicks == 0) {
            stuck = 0;
            if (++recoveryCount >= 3) return new Decision(new KartPhysics.Input(0,0), true, false);
            reverseTicks = 28;
        }
        if (reverseTicks > 0) {
            reverseTicks--;
            double backYaw = state.yaw()+180;
            boolean clear = safe(state,backYaw,1.5,terrain,opponents);
            return new Decision(new KartPhysics.Input(clear ? -1 : 0,
                    -Math.copySign(.85, Math.abs(error)>5 ? error : preference == 0 ? 1 : preference)),false,false);
        }
        int reaction = difficulty == BotDifficulty.EASY ? 4 : difficulty == BotDifficulty.NORMAL ? 2 : 1;
        double look = Math.clamp(Math.abs(state.speed())*.45+1.8, 2, 6);
        boolean directSafe = safe(state,state.yaw(),look,terrain,opponents);
        if (ticks % reaction == 0 || ticks == 1 || !directSafe) {
            double bestScore = -Double.MAX_VALUE;
            double best = error;
            // Fixed, bounded fan probes. No world access is performed outside CollisionSpace.
            for (double offset : new double[]{0,-25,25,-50,50,-80,80}) {
                double heading = state.yaw()+offset;
                if (!safe(state,heading,look,terrain,opponents)) continue;
                double score = -Math.abs(angle(desired-heading)) - Math.abs(offset)*.12 + offset*preference;
                if (score > bestScore) { bestScore = score; best = angle(heading-state.yaw()); }
            }
            // A clear direct route retains continuous aim instead of quantising every turn.
            if (safe(state,desired,look,terrain,opponents)) best = error;
            heldSteering = Math.clamp(best/40,-1,1);
        }
        double max = difficulty == BotDifficulty.EASY ? 6.0 : difficulty == BotDifficulty.NORMAL ? 8.0 : 9.5;
        max += preference;
        double distance = Math.hypot(target.x()-state.x(),target.z()-state.z());
        double corner = following == null ? 0 : Math.abs(angle(Math.toDegrees(Math.atan2(
                -(following.x()-target.x()),following.z()-target.z()))-desired));
        double bend = Math.max(Math.abs(error), distance < 9 ? corner : 0);
        double targetSpeed = Math.min(max, bend > 100 ? 2.2 : bend > 55 ? 3.5 : bend > 25 ? 5.5 : max);
        if (!directSafe) targetSpeed = Math.min(targetSpeed, 2);
        // Emergency braking uses the same reverse input as a player; stop before reversing.
        boolean immediate = safe(state,state.yaw(),Math.max(.6,Math.abs(state.speed())*.15),terrain,opponents);
        if (!immediate) targetSpeed = 0;
        double throttle = state.speed() > targetSpeed+.3 ? (state.speed()>1 ? -1 : 0)
                : state.speed() < targetSpeed-.2 ? 1 : 0;
        return new Decision(new KartPhysics.Input(throttle,heldSteering),false,
                directSafe && immediate && Math.abs(error)<10 && corner<20 && distance>10 && state.speed()>5);
    }
    private static boolean safe(KartPhysics.State state, double yaw, double distance,
                                KartPhysics.CollisionSpace terrain, List<KartPhysics.State> opponents) {
        double sin = -Math.sin(Math.toRadians(yaw)), cos = Math.cos(Math.toRadians(yaw));
        for (int step=1;step<=3;step++) {
            double d=distance*step/3, x=state.x()+sin*d,z=state.z()+cos*d;
            if (!terrain.clearAt(x,state.y(),z,(float)yaw)) return false;
            // Centre and four footprint corners need support: do not steer over a drop.
            double w=KartPhysics.BODY_WIDTH*.4,l=KartPhysics.BODY_LENGTH*.4;
            for (double[] p : new double[][]{{0,0},{-w,-l},{w,-l},{-w,l},{w,l}})
                if (terrain.clear(x+cos*p[0]+sin*p[1],state.y()-.65,z-sin*p[0]+cos*p[1],.2,.65)) return false;
            for (var other : opponents)
                if (KartPhysics.overlaps(new KartPhysics.State(x,state.y(),z,(float)yaw,0,0,true),other)) return false;
        }
        return true;
    }
    private static double angle(double value) { return ((value+180)%360+360)%360-180; }
}
