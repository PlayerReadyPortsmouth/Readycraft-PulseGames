package uk.co.playerready.pulsegames.games.karts;

import uk.co.playerready.pulsegames.games.karts.KartPose.Vec;

/** Movement on the contact ribbon, not a visual-only roll of world-horizontal movement. */
public final class KartSurfacePhysics {
    private static final double DT=.05;
    private KartSurfacePhysics() {}
    public record State(double distance,double lateral,double headingDegrees,double speed) {
        public State {
            if(!Double.isFinite(distance+lateral+headingDegrees+speed))throw new IllegalArgumentException("Non-finite surface state");
            headingDegrees=Math.IEEEremainder(headingDegrees,360);
        }
    }
    public record Step(State state,KartPhysics.State chassis,KartPose pose,boolean detached,Vec worldVelocity) {}
    public static Step tick(KartSurface surface,State state,KartPhysics.Input input,double multiplier,
                            KartPhysics.CollisionSpace space,KartStyle style) {
        multiplier=Double.isFinite(multiplier)?Math.clamp(multiplier,0,2):0;
        double speed=state.speed();
        if(input.throttle()==0)speed=approach(speed,0,5*DT);
        else if(speed*input.throttle()<0)speed=approach(speed,0,22*DT);
        else speed+=input.throttle()*9*style.acceleration()*DT;
        speed=Math.clamp(speed,-KartPhysics.REVERSE_SPEED*multiplier,KartPhysics.MAX_SPEED*multiplier*style.speed());
        double turn=input.steering()*126*style.steering()*DT*Math.min(1,Math.abs(speed)/2)*Math.signum(speed);
        double heading=state.headingDegrees()+turn;
        // Forward is +Z and positive Minecraft yaw turns towards -X.
        double ds=Math.cos(Math.toRadians(heading))*speed*DT,dl=-Math.sin(Math.toRadians(heading))*speed*DT;
        int steps=Math.max(1,Math.max((int)Math.ceil(Math.abs(speed)*DT/.05),(int)Math.ceil(Math.abs(turn))));
        steps=Math.max(steps,(int)Math.ceil(Math.abs(ds)*surface.degreesPerBlock()));
        double distance=state.distance(),lateral=state.lateral(),acceptedHeading=state.headingDegrees();
        boolean detached=false;
        for(int i=1;i<=steps;i++) {
            double nd=state.distance()+ds*i/steps,nl=state.lateral()+dl*i/steps,nh=state.headingDegrees()+turn*i/steps;
            double clamped=Math.clamp(nd,0,surface.length());KartSurface.Frame frame=surface.sample(clamped);
            // The full diagonal stays on the ribbon; leaving it releases adhesion, never clamps the driver to a rail.
            boolean leave=nd<0||nd>surface.length()||Math.abs(nl)>frame.halfWidth()-KartPhysics.WIDTH/2;
            KartPose candidate=surface.pose(clamped,nl,nh);
            if(!space.clearPose(candidate)){speed=0;break;}
            distance=clamped;lateral=nl;acceptedHeading=nh;
            if(leave){detached=true;break;}
        }
        State next=new State(distance,lateral,acceptedHeading,speed);KartPose pose=surface.pose(distance,lateral,acceptedHeading);
        Vec velocity=pose.forward().multiply(speed);
        // Ordinary physics resumes with the actual world velocity, including vertical wall travel.
        double horizontal=Math.hypot(velocity.x(),velocity.z());float yaw=detached&&horizontal>1e-8?(float)Math.toDegrees(Math.atan2(-velocity.x(),velocity.z())):pose.yaw();
        KartPhysics.State chassis=new KartPhysics.State(pose.position().x(),pose.position().y(),pose.position().z(),yaw,
                detached?horizontal:speed,detached?velocity.y():0,!detached);
        return new Step(next,chassis,pose,detached,velocity);
    }
    private static double approach(double value,double target,double step){return value<target?Math.min(target,value+step):Math.max(target,value-step);}
}
