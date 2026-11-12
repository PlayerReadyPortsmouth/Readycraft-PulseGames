package uk.co.playerready.pulsegames.games.karts;

/** An orthonormal chassis frame; position is the centre of its contact plane. */
public record KartPose(Vec position, Vec right, Vec up, Vec forward) {
    public record Vec(double x, double y, double z) {
        public Vec {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite surface vector");
        }
        public Vec add(Vec b) { return new Vec(x+b.x,y+b.y,z+b.z); }
        public Vec subtract(Vec b) { return new Vec(x-b.x,y-b.y,z-b.z); }
        public Vec multiply(double s) { return new Vec(x*s,y*s,z*s); }
        public double dot(Vec b) { return x*b.x+y*b.y+z*b.z; }
        public Vec cross(Vec b) { return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x); }
        public double length() { return Math.sqrt(dot(this)); }
        public Vec unit() {
            double n=length();
            if(!Double.isFinite(n)||n<1e-8) throw new IllegalArgumentException("Degenerate surface frame");
            return multiply(1/n);
        }
        public Vec lerp(Vec b,double t) { return multiply(1-t).add(b.multiply(t)); }
    }
    public KartPose {
        if (Math.abs(right.length()-1)>1e-5 || Math.abs(up.length()-1)>1e-5 || Math.abs(forward.length()-1)>1e-5
                || Math.abs(right.dot(up))>1e-5 || Math.abs(right.dot(forward))>1e-5 || Math.abs(up.dot(forward))>1e-5
                || right.cross(up).dot(forward)<.99999) throw new IllegalArgumentException("Invalid chassis frame");
    }
    public KartPose translate(Vec delta) { return new KartPose(position.add(delta),right,up,forward); }
    public float yaw() { return (float)Math.toDegrees(Math.atan2(-forward.x(),forward.z())); }
    public float pitch() { return (float)Math.toDegrees(Math.asin(Math.clamp(-forward.y(),-1,1))); }
    /** Rotation maps model +X/+Y/+Z to right/up/forward, including bank and inversion. */
    public org.joml.Quaternionf rotation() {
        return new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(
                (float)right.x(),(float)right.y(),(float)right.z(),
                (float)up.x(),(float)up.y(),(float)up.z(),
                (float)forward.x(),(float)forward.y(),(float)forward.z()));
    }
    public static KartPose upright(KartPhysics.State state) {
        double a=Math.toRadians(state.yaw());
        return new KartPose(new Vec(state.x(),state.y(),state.z()),new Vec(Math.cos(a),0,Math.sin(a)),
                new Vec(0,1,0),new Vec(-Math.sin(a),0,Math.cos(a)));
    }
}
