package uk.co.playerready.pulsegames.games.karts;

import java.util.List;
import uk.co.playerready.pulsegames.games.karts.KartPose.Vec;

/** Authored contact ribbon, with an explicit normal at every station. */
public final class KartSurface {
    public record Node(Vec position, Vec up, double halfWidth) {
        public Node {
            if (Math.abs(position.x()) > 30_000_000 || Math.abs(position.y()) > 30_000_000 || Math.abs(position.z()) > 30_000_000)
                throw new IllegalArgumentException("Surface coordinates exceed world bounds");
            up=up.unit();
            if(!Double.isFinite(halfWidth)||halfWidth<2||halfWidth>32)
                throw new IllegalArgumentException("Surface half-width must be 2..32");
        }
    }
    public record Frame(KartPose pose,double halfWidth) {}
    private final String id;
    private final List<Node> nodes;
    private final double[] distances;
    private final Vec[] tangents;
    private double degreesPerBlock;
    public KartSurface(String id,List<Node> nodes) {
        if(id==null||id.isBlank()||nodes.size()<2||nodes.size()>256)
            throw new IllegalArgumentException("Surface requires an id and 2..256 nodes");
        this.id=id;this.nodes=List.copyOf(nodes);distances=new double[nodes.size()];tangents=new Vec[nodes.size()];
        Vec previous=null;
        for(int i=0;i+1<nodes.size();i++) {
            Vec delta=nodes.get(i+1).position().subtract(nodes.get(i).position());double length=delta.length();
            if(length<.25||length>16) throw new IllegalArgumentException(id+": segment length must be .25..16");
            Vec tangent=delta.unit();
            if(previous!=null&&previous.dot(tangent)+1e-9<Math.cos(Math.toRadians(45)))
                throw new IllegalArgumentException(id+": centreline turn exceeds 45 degrees; add intermediate nodes");
            if(nodes.get(i).up().dot(nodes.get(i+1).up())+1e-9<Math.cos(Math.toRadians(45)))
                throw new IllegalArgumentException(id+": normal turn exceeds 45 degrees; add intermediate nodes");
            if(Math.abs(tangent.dot(nodes.get(i).up()))>.35||Math.abs(tangent.dot(nodes.get(i+1).up()))>.35)
                throw new IllegalArgumentException(id+": normal is not perpendicular to travel");
            tangents[i]=previous==null?tangent:previous.add(tangent).unit();
            double normalTurn=Math.toDegrees(Math.acos(Math.clamp(nodes.get(i).up().dot(nodes.get(i+1).up()),-1,1)));
            double pathTurn=previous==null?0:Math.toDegrees(Math.acos(Math.clamp(previous.dot(tangent),-1,1)));
            if((normalTurn+pathTurn)/length>20+1e-9)
                throw new IllegalArgumentException(id+": surface turns too sharply; allow at least one block per 20 degrees");
            degreesPerBlock=Math.max(degreesPerBlock,(normalTurn+pathTurn)*2/length);
            previous=tangent;distances[i+1]=distances[i]+length;
        }
        tangents[nodes.size()-1]=previous;
        if(nodes.get(0).up().y()<.9999||nodes.get(nodes.size()-1).up().y()<.9999
                ||Math.abs(tangents[0].y())>.001||Math.abs(previous.y())>.001)
            throw new IllegalArgumentException(id+": entry and exit must be upright and horizontal");
    }
    public String id(){return id;}
    public double degreesPerBlock(){return degreesPerBlock;}
    public double length(){return distances[distances.length-1];}
    public List<Node> nodes(){return nodes;}
    public Frame sample(double distance) {
        if(!Double.isFinite(distance))throw new IllegalArgumentException("Non-finite surface distance");
        double d=Math.clamp(distance,0,length());int i=0;
        while(i+2<distances.length&&d>distances[i+1])i++;
        double t=(d-distances[i])/(distances[i+1]-distances[i]);
        Node a=nodes.get(i),b=nodes.get(i+1);
        Vec forward=tangents[i].lerp(tangents[i+1],t).unit();
        Vec up=a.up().lerp(b.up(),t);up=up.subtract(forward.multiply(up.dot(forward))).unit();
        Vec right=up.cross(forward).unit();
        return new Frame(new KartPose(a.position().lerp(b.position(),t),right,up,forward),a.halfWidth()+(b.halfWidth()-a.halfWidth())*t);
    }
    public KartPose pose(double distance,double lateral,double headingDegrees) {
        Frame frame=sample(distance);KartPose base=frame.pose();double a=Math.toRadians(headingDegrees);
        Vec forward=base.forward().multiply(Math.cos(a)).subtract(base.right().multiply(Math.sin(a))).unit();
        return new KartPose(base.position().add(base.right().multiply(lateral)),base.up().cross(forward).unit(),base.up(),forward);
    }
    /** Entry-only attachment prevents snapping to a neighbouring overpass or later section. */
    public KartSurfacePhysics.State attach(KartPhysics.State chassis) {
        if(!chassis.grounded()||chassis.verticalSpeed()>0)return null;
        KartPose start=sample(0).pose();Vec p=new Vec(chassis.x(),chassis.y(),chassis.z());Vec delta=p.subtract(start.position());
        double along=delta.dot(start.forward()),lateral=delta.dot(start.right()),height=delta.dot(start.up());
        if(along<-.75||along>Math.min(1.5,distances[1])||Math.abs(height)>.65||Math.abs(lateral)>nodes.get(0).halfWidth()-KartPhysics.WIDTH/2)return null;
        Vec heading=KartPose.upright(chassis).forward();
        if(heading.dot(start.forward())<.5||chassis.speed()<=0)return null;
        double angle=Math.toDegrees(Math.atan2(-heading.dot(start.right()),heading.dot(start.forward())));
        return new KartSurfacePhysics.State(Math.max(0,along),lateral,angle,chassis.speed());
    }
}
