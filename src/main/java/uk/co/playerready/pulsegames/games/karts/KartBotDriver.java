package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import java.util.UUID;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

/** Pure 20 Hz driver: supplies ordinary controls, never moves a kart or changes its physics. */
public final class KartBotDriver {
    public static final double ARRIVAL_RADIUS = 1.5;
    public record Point(double x, double y, double z) {}
    public record Decision(KartPhysics.Input input, boolean reset, boolean useTurboSafe, boolean drift) {
        public Decision(KartPhysics.Input input, boolean reset, boolean useTurboSafe) {
            this(input, reset, useTurboSafe, false);
        }
    }
    /** Arcade hints from the match: boost-scaled target speed, a jump gap ahead, or mid-flight. */
    public record Context(double speedScale, boolean ignoreDrops, boolean airborne) {
        public static final Context NONE = new Context(1, false, false);
    }
    private boolean ignoreDrops;
    private final double preference;
    private int stuck, reverseTicks, recoveryCount, ticks;
    private boolean drifting;
    private int driftTicks, driftCooldown, driftPreviewUntil, driftDirection;
    private double previewSteering;
    private Point driftTarget;
    private double lastX = Double.NaN, lastZ, heldSteering;
    public KartBotDriver(UUID identity) {
        preference = (Math.floorMod(identity.hashCode(), 1001) / 1000.0 - .5) * .12;
    }
    public void reset() { stuck = reverseTicks = recoveryCount = ticks = 0; lastX = Double.NaN; heldSteering = 0; drifting = false; driftTicks = driftCooldown = driftPreviewUntil = driftDirection = 0; driftTarget = null; }
    public int stuckTicks() { return stuck; }
    public boolean recovering() { return reverseTicks > 0; }

    public Decision update(KartPhysics.State state, Point target, Point following,
                           BotDifficulty difficulty, KartPhysics.CollisionSpace terrain,
                           List<KartPhysics.State> opponents) {
        return update(state,target,following,difficulty,terrain,opponents,KartStyle.BALANCED);
    }

    public Decision update(KartPhysics.State state, Point target, Point following,
                           BotDifficulty difficulty, KartPhysics.CollisionSpace terrain,
                           List<KartPhysics.State> opponents, KartStyle style) {
        return update(state,target,following,difficulty,terrain,opponents,style,Context.NONE);
    }

    /**
     * Over a jump pad a bot stops probing for drops (the gap is the point) and in the air it just holds throttle
     * and keeps its heading; boosted, it raises its target speed so it does not brake against its own boost.
     */
    public Decision update(KartPhysics.State state, Point target, Point following,
                           BotDifficulty difficulty, KartPhysics.CollisionSpace terrain,
                           List<KartPhysics.State> opponents, KartStyle style, Context context) {
        ticks++;
        ignoreDrops = context.ignoreDrops();
        if (context.airborne()) { cancelDrift(); return new Decision(new KartPhysics.Input(1,0),false,false); }
        if (driftCooldown > 0) driftCooldown--;
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
            cancelDrift();
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
        max = (max + preference) * Math.clamp(context.speedScale(), 1, 2);
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
        boolean targetChanged = drifting && !target.equals(driftTarget);
        double maxBend = difficulty == BotDifficulty.HARD ? 80 : 65;
        boolean wantsDrift = difficulty != BotDifficulty.EASY && following != null && state.grounded()
                && state.speed() >= 4 && state.speed() <= KartPhysics.MAX_SPEED + .01
                && directSafe && immediate && distance > 6
                && Math.abs(error) >= (drifting ? 12 : 25) && Math.abs(error) <= maxBend
                && Math.abs(heldSteering) >= .25 && !targetChanged
                && (!drifting || Math.signum(heldSteering) == driftDirection)
                && (drifting || driftCooldown == 0)
                && driftTicks < (difficulty == BotDifficulty.HARD ? 50 : 28);
        KartPhysics.Input driftInput = new KartPhysics.Input(1,heldSteering);
        // Reuse the longer preview for at most two ticks. Fresh short probes still reject
        // changing terrain/traffic on every tick, including the direction of the rear slip.
        boolean driftSafe = wantsDrift && safe(state,state.yaw()-Math.signum(heldSteering)*16,
                Math.max(1.5,state.speed()*.25),terrain,opponents);
        if (driftSafe && (!drifting || ticks >= driftPreviewUntil || Math.abs(heldSteering-previewSteering)>.1)) {
            driftSafe = safeDrift(state,driftInput,terrain,opponents,style);
            if (driftSafe) { driftPreviewUntil = ticks + 2; previewSteering = heldSteering; }
        }
        if (driftSafe) {
            drifting = true; driftTarget = target; driftDirection = (int)Math.signum(heldSteering); driftTicks++;
            return new Decision(driftInput,false,false,true);
        }
        if (drifting) {
            // Releasing on an unsafe approach must cancel charge, not trigger a boost.
            boolean releaseSafe = directSafe && immediate && Math.abs(error)<15 && (distance>10 || corner<25)
                    && safe(state,state.yaw(),Math.max(8,state.speed()),terrain,opponents);
            if (!releaseSafe) throttle = Math.min(0,throttle);
            else throttle = 1; // A safe release must remain eligible for the shared earned boost.
            cancelDrift();
        }
        return new Decision(new KartPhysics.Input(throttle,heldSteering),false,
                directSafe && immediate && Math.abs(error)<10 && corner<20 && distance>10 && state.speed()>5,false);
    }
    private void cancelDrift() {
        if (drifting) driftCooldown = 40;
        drifting = false; driftTicks = 0; driftPreviewUntil = driftDirection = 0; driftTarget = null;
    }

    /** Preview ordinary shared drift physics; every future chassis needs terrain support and traffic clearance. */
    private boolean safeDrift(KartPhysics.State state, KartPhysics.Input input,
                                     KartPhysics.CollisionSpace terrain, List<KartPhysics.State> opponents, KartStyle style) {
        KartDrift preview = new KartDrift();
        var future = state;
        for (int step=0; step<12; step++) {
            var control = preview.update(true,input,future,false);
            var next = KartPhysics.tick(future,control.input(),1,terrain,style,control.slipDegrees());
            if (!next.grounded() || next.speed() < future.speed()-.1
                    || !safe(next,next.yaw(),.5,terrain,opponents)) return false;
            future = next;
        }
        return true;
    }

    private boolean safe(KartPhysics.State state, double yaw, double distance,
                                KartPhysics.CollisionSpace terrain, List<KartPhysics.State> opponents) {
        double sin = -Math.sin(Math.toRadians(yaw)), cos = Math.cos(Math.toRadians(yaw));
        for (int step=1;step<=3;step++) {
            double d=distance*step/3, x=state.x()+sin*d,z=state.z()+cos*d;
            if (!terrain.clearAt(x,state.y(),z,(float)yaw)) return false;
            // Centre and four footprint corners need support: do not steer over a drop.
            double w=KartPhysics.BODY_WIDTH*.4,l=KartPhysics.BODY_LENGTH*.4;
            if (!ignoreDrops) for (double[] p : new double[][]{{0,0},{-w,-l},{w,-l},{-w,l},{w,l}})
                if (terrain.clear(x+cos*p[0]+sin*p[1],state.y()-.65,z-sin*p[0]+cos*p[1],.2,.65)) return false;
            for (var other : opponents)
                if (KartPhysics.overlaps(new KartPhysics.State(x,state.y(),z,(float)yaw,0,0,true),other)) return false;
        }
        return true;
    }
    private static double angle(double value) { return ((value+180)%360+360)%360-180; }
}
