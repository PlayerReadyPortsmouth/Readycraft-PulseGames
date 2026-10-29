package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import java.util.UUID;
import uk.co.playerready.pulsegames.core.bot.BotDifficulty;

/** Pure 20 Hz driver: supplies ordinary controls, never moves a kart or changes its physics. */
public final class KartBotDriver {
    public static final double ARRIVAL_RADIUS = 1.5;
    public record Point(double x, double y, double z) {}
    /** {@code cornerAhead}: a sharp bend is close, the place to drop a banana for followers cutting through. */
    public record Decision(KartPhysics.Input input, boolean reset, boolean useTurboSafe, boolean drift, boolean cornerAhead) {
        public Decision(KartPhysics.Input input, boolean reset, boolean useTurboSafe) {
            this(input, reset, useTurboSafe, false, false);
        }
        public Decision(KartPhysics.Input input, boolean reset, boolean useTurboSafe, boolean drift) {
            this(input, reset, useTurboSafe, drift, false);
        }
    }
    /**
     * Hints from the match: boost-scaled target speed, a jump gap ahead, mid-flight, the rubber-band
     * {@code paceScale} (bounded, invisible), the current lap (varies the racing line) and tunables.
     */
    public record Context(double speedScale, boolean ignoreDrops, boolean airborne, double paceScale, int lap, KartBotTuning tuning) {
        public static final Context NONE = new Context(1, false, false);
        public Context {
            paceScale = Double.isFinite(paceScale) ? Math.clamp(paceScale, .8, 1.2) : 1;
            if (tuning == null) tuning = KartBotTuning.DEFAULT;
        }
        public Context(double speedScale, boolean ignoreDrops, boolean airborne) {
            this(speedScale, ignoreDrops, airborne, 1, 1, KartBotTuning.DEFAULT);
        }
    }
    /** Fixed per bot from its UUID, alongside kart style. */
    public enum Personality {
        CAUTIOUS(1, 1.3, -10, 60), STEADY(.8, 1, 0, 40), AGGRESSIVE(.55, .7, 10, 30);
        final double laneFactor, itemTempo; final int bendBonus, driftCooldown;
        Personality(double lane, double itemTempo, int bendBonus, int cooldown) {
            this.laneFactor = lane; this.itemTempo = itemTempo; this.bendBonus = bendBonus; this.driftCooldown = cooldown;
        }
        /** Multiplies the ticks between item checks: below 1 is keener. */
        public double itemTempo() { return itemTempo; }
    }
    private static final double KP = 1 / 32.0, KD = .04;
    private final Personality personality;
    private final double lanePhase;
    private final boolean easyDrifter;
    private final long hash;
    private boolean easyTier;
    private int overtakeUntil, overtakeSide, lastSteerTick;
    private double lastBest;
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
        hash = Long.hashCode(identity.getLeastSignificantBits() * 0x9E3779B97F4A7C15L ^ identity.getMostSignificantBits());
        personality = Personality.values()[Math.floorMod(hash, 3)];
        lanePhase = Math.floorMod(hash >>> 3, 628) / 100.0;
        easyDrifter = Math.floorMod(hash >>> 7, 100) / 100.0 < KartBotTuning.DEFAULT.easyDrifterShare;
    }
    public Personality personality() { return personality; }
    /** Racing-line offset for this lap in blocks, always within +/- the configured lane width. */
    double laneOffset(int lap, KartBotTuning tuning) {
        return tuning.laneWidth * personality.laneFactor * Math.sin(lanePhase + lap * 1.7);
    }
    public void reset() { stuck = reverseTicks = recoveryCount = ticks = 0; lastX = Double.NaN; heldSteering = 0; drifting = false; driftTicks = driftCooldown = driftPreviewUntil = driftDirection = 0; driftTarget = null; overtakeUntil = overtakeSide = lastSteerTick = 0; lastBest = 0; }
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
        var tuning = context.tuning();
        double distance = Math.hypot(target.x()-state.x(),target.z()-state.z());
        double look = Math.clamp(Math.abs(state.speed())*.45+1.8, 2, 6);
        double trueDesired = Math.toDegrees(Math.atan2(-(target.x()-state.x()), target.z()-state.z()));
        double corner = 0, cornerSigned = 0, run = 30;
        if (following != null) {
            cornerSigned = angle(Math.toDegrees(Math.atan2(-(following.x()-target.x()),following.z()-target.z()))-trueDesired);
            corner = Math.abs(cornerSigned);
            run = Math.hypot(following.x()-target.x(),following.z()-target.z());
        }
        // Racing line: a lane offset perpendicular to the segment, plus a look-ahead blend towards the next waypoint.
        Point aim = aimPoint(state, target, following, look, context, terrain, opponents, corner, distance);
        double desired = Math.toDegrees(Math.atan2(-(aim.x()-state.x()), aim.z()-state.z()));
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
            // PD: the derivative term damps the reaction-delay overshoot the old best/40 had.
            double rate = lastSteerTick == 0 || Math.abs(best-lastBest) > 45 ? 0
                    : Math.clamp((best-lastBest)/Math.max(1,ticks-lastSteerTick), -15, 15);
            lastBest = best; lastSteerTick = ticks;
            heldSteering = Math.clamp(best*KP + rate*KD,-1,1);
        }
        int tier = difficulty.ordinal(); easyTier = difficulty == BotDifficulty.EASY;
        // The ceiling is the kart's own physics maximum for its style; pace and boost never push past it.
        double ceiling = KartPhysics.MAX_SPEED * style.speed();
        double max = Math.min(tuning.speedFraction[tier] * ceiling * context.paceScale() + preference, ceiling)
                * Math.clamp(context.speedScale(), 1, 2);
        double bend = Math.max(Math.abs(error), distance < 12 ? corner : 0);
        double[] cs = tuning.cornerSpeeds; double scale = new double[]{.85,1,1.1}[tier];
        double targetSpeed = bend > 100 ? cs[2]*scale : bend > 55 ? cs[1]*scale : bend > 25 ? cs[0]*scale : max;
        targetSpeed = Math.min(max, targetSpeed);
        if (!directSafe) targetSpeed = Math.min(targetSpeed, 2);
        // Emergency braking uses the same reverse input as a player; stop before reversing.
        boolean immediate = safe(state,state.yaw(),Math.max(.6,Math.abs(state.speed())*.15),terrain,opponents);
        if (!immediate) targetSpeed = 0;
        double throttle = state.speed() > targetSpeed+.3 ? (state.speed()>1 ? -1 : 0)
                : state.speed() < targetSpeed-.2 ? 1 : 0;
        // A new waypoint only ends a drift when the bend stops; the same direction carries straight on.
        boolean targetChanged = drifting && !target.equals(driftTarget)
                && !(Math.abs(cornerSigned) >= 20 && Math.signum(cornerSigned) == driftDirection);
        double maxBend = (difficulty == BotDifficulty.HARD ? 80 : 65) + personality.bendBonus;
        int maxDrift = tuning.driftMaxTicks[tier];
        boolean easyOk = difficulty != BotDifficulty.EASY || (easyDrifter && Math.abs(error) <= 50);
        // Do not start into a chicane: the next bend would cancel the drift straight away.
        boolean chicane = !drifting && corner > 40 && Math.signum(cornerSigned) != Math.signum(error);
        boolean wantsDrift = easyOk && !chicane && following != null && state.grounded()
                && state.speed() >= 4 && state.speed() <= KartPhysics.MAX_SPEED + .01
                && directSafe && immediate && distance > 6
                && Math.abs(error) >= (drifting ? 12 : 25) && Math.abs(error) <= maxBend
                && Math.abs(heldSteering) >= .25 && !targetChanged
                && (!drifting || Math.signum(heldSteering) == driftDirection)
                && (drifting || driftCooldown == 0)
                && driftTicks < maxDrift;
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
        // Turbo is held for a real straight: this stretch plus the next segment.
        boolean straight = directSafe && immediate && Math.abs(error)<10 && corner<20 && distance>10
                && state.speed()>5 && distance + run >= 24;
        return new Decision(new KartPhysics.Input(throttle,heldSteering),false,straight,false,
                corner >= 40 && distance < 12);
    }
    private void cancelDrift() {
        if (drifting) driftCooldown = easyTier ? 240 : personality.driftCooldown;
        drifting = false; driftTicks = 0; driftPreviewUntil = driftDirection = 0; driftTarget = null;
    }

    /**
     * Target point for steering: the waypoint shifted sideways onto this bot's lane (fading out near the
     * waypoint so arrival still works), nudged towards the next waypoint on gentle bends. A lane is only used
     * if the ordinary safety probes accept the line to it; an overtake swaps to the clear side.
     */
    Point aimPoint(KartPhysics.State state, Point target, Point following, double look, Context context,
                           KartPhysics.CollisionSpace terrain, List<KartPhysics.State> opponents, double corner, double distance) {
        var tuning = context.tuning();
        double fx = following == null ? target.x()-state.x() : following.x()-target.x();
        double fz = following == null ? target.z()-state.z() : following.z()-target.z();
        double norm = Math.hypot(fx,fz);
        if (norm < 1) return target;
        fx /= norm; fz /= norm;
        double px = -fz, pz = fx; // perpendicular, positive on the side a positive steer turns towards
        // Swap lanes when a kart is close ahead: go round on the side it is not on.
        double sin = -Math.sin(Math.toRadians(state.yaw())), cos = Math.cos(Math.toRadians(state.yaw()));
        for (var other : opponents) {
            double rx = other.x()-state.x(), rz = other.z()-state.z();
            double along = rx*sin + rz*cos, lateral = rx*px + rz*pz;
            if (along > 1 && along < tuning.overtakeRange && Math.abs(lateral) < 2.2 && ticks >= overtakeUntil - 20) {
                overtakeSide = lateral >= 0 ? -1 : 1; overtakeUntil = ticks + 40;
                break;
            }
        }
        double offset = ticks < overtakeUntil ? overtakeSide * tuning.laneWidth : laneOffset(context.lap(), tuning);
        double fade = Math.clamp((distance-2.5)/8, 0, 1);
        double blend = distance < 10 && corner < 50 ? .35 * (1 - distance/10) : 0;
        for (double scale : new double[]{1, .5, 0}) {
            double lateral = offset * fade * scale;
            Point candidate = new Point(target.x()+px*lateral+(following == null ? 0 : (following.x()-target.x())*blend),
                    target.y(), target.z()+pz*lateral+(following == null ? 0 : (following.z()-target.z())*blend));
            double bearing = Math.toDegrees(Math.atan2(-(candidate.x()-state.x()), candidate.z()-state.z()));
            if (scale == 0) return candidate;
            // Keep room on the outer side too, so a lane never hugs a wall the line itself would clear.
            double outer = Math.signum(lateral) * .6;
            var shifted = new KartPhysics.State(state.x()+px*outer, state.y(), state.z()+pz*outer, state.yaw(),
                    state.speed(), state.verticalSpeed(), state.grounded());
            if (safe(state, bearing, Math.min(look, distance), terrain, opponents)
                    && safe(shifted, bearing, Math.min(look, distance), terrain, opponents)) return candidate;
        }
        return target;
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
