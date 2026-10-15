package uk.co.playerready.pulsegames.games.karts;

import org.bukkit.Location;
import org.bukkit.World;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.util.Cuboid;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import java.util.*;

/** Explicit driving line; validation is read-only and never loads a world or chunk. */
public final class KartBotRoute {
    public static final String PREFIX = "bot-waypoint";
    private final List<Location> points;
    private final int[] gateTargets;
    private KartBotRoute(List<Location> points, int[] gateTargets) {
        this.points = points; this.gateTargets = gateTargets;
    }
    public List<Location> points() { return points.stream().map(Location::clone).toList(); }
    /** Target waypoint for the next zero-based checkpoint, or finish after the last checkpoint. */
    public int waypointForCheckpoint(int nextCheckpoint) {
        return gateTargets[Math.max(0, Math.min(nextCheckpoint, gateTargets.length - 1))];
    }
    public int gateWaypoint(int gateIndex) { return gateTargets[gateIndex]; }
    public static String validationError(Arena arena) {
        try { load(arena, null); return null; }
        catch (IllegalArgumentException ex) { return ex.getMessage(); }
    }
    public static KartBotRoute load(Arena arena, World world) {
        Map<String,Object> settings = arena.settings().getValues(false);
        return load(settings, arena.regions(), world);
    }
    public static KartBotRoute load(Map<String,Object> settings, Map<String,Cuboid> regions, World world) {
        SortedMap<Integer,String> ordered = new TreeMap<>();
        settings.forEach((key,value) -> {
            if (!key.startsWith(PREFIX)) return;
            String suffix = key.substring(PREFIX.length());
            int n;
            try { n = Integer.parseInt(suffix); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Use bot-waypoint1, bot-waypoint2, ..."); }
            if (n < 1 || n > 128 || !Integer.toString(n).equals(suffix))
                throw new IllegalArgumentException("Bot waypoints must be numbered 1 to 128.");
            ordered.put(n, Objects.toString(value, ""));
        });
        if (ordered.size() < 3) throw new IllegalArgumentException("Kart bots need at least three ordered bot-waypoints.");
        List<Location> points = new ArrayList<>();
        for (var entry : ordered.entrySet()) {
            if (entry.getKey() != points.size()+1) throw new IllegalArgumentException("Number bot-waypoints consecutively from 1.");
            try { points.add(LocUtil.parse(entry.getValue(), world)); }
            catch (RuntimeException ex) { throw new IllegalArgumentException(PREFIX+entry.getKey()+" has invalid coordinates."); }
        }
        List<Cuboid> gates = new ArrayList<>();
        for (int n=1; regions.containsKey("checkpoint"+n); n++) gates.add(regions.get("checkpoint"+n));
        if (gates.isEmpty() || !regions.containsKey("finish")) throw new IllegalArgumentException("Kart bot route needs checkpoints and finish.");
        long checkpointCount=regions.keySet().stream().filter(k->k.startsWith("checkpoint")).count();
        if(checkpointCount!=gates.size()) throw new IllegalArgumentException("Number checkpoint regions consecutively from 1.");
        for(int n=1;n<=gates.size();n++) {
            Object reset=settings.get("checkpoint-spawn"+n);
            try { if(reset==null)throw new IllegalArgumentException(); LocUtil.parse(reset.toString(),world); }
            catch(RuntimeException ex) { throw new IllegalArgumentException("Kart bots need valid checkpoint-spawn"+n+" for recovery."); }
        }
        gates.add(regions.get("finish"));
        for (Cuboid gate:gates) if(gate.contains(points.get(0)))
            throw new IllegalArgumentException("bot-waypoint1 must be after finish and outside all gates.");
        int next=0; int[] targets=new int[gates.size()];
        record Crossing(int gate,double fraction) {}
        for(int i=0;i<points.size();i++) {
            Location from=points.get(i),to=points.get((i+1)%points.size());
            if(Math.pow(from.getX()-to.getX(),2)+Math.pow(from.getY()-to.getY(),2)+Math.pow(from.getZ()-to.getZ(),2)<0.25) throw new IllegalArgumentException("Adjacent bot waypoints must be at least half a block apart.");
            if(distanceSquared(from,to)>24*24)throw new IllegalArgumentException("Bot waypoint segments must be at most 24 blocks; add points along the driving line.");
            List<Crossing> crossings=new ArrayList<>();
            for(int g=0;g<gates.size();g++) {
                if(gates.get(g).contains(from)) continue;
                double fraction=entry(gates.get(g),from,to);
                if(Double.isFinite(fraction)) crossings.add(new Crossing(g,fraction));
            }
            crossings.sort(Comparator.comparingDouble(Crossing::fraction));
            for(Crossing crossing:crossings) {
                if(crossing.gate()!=next) throw new IllegalArgumentException("Bot route must cross checkpoints then finish exactly once, in order.");
                if(next<gates.size()-1) {
                    Location reset=LocUtil.parse(settings.get("checkpoint-spawn"+(next+1)).toString(),world);
                    double dx=to.getX()-from.getX(),dy=to.getY()-from.getY(),dz=to.getZ()-from.getZ();
                    double t=((reset.getX()-from.getX())*dx+(reset.getY()-from.getY())*dy+(reset.getZ()-from.getZ())*dz)/distanceSquared(from,to);
                    double clamped=Math.max(0,Math.min(1,t));
                    Location nearest=new Location(world,from.getX()+clamped*dx,from.getY()+clamped*dy,from.getZ()+clamped*dz);
                    if(distanceSquared(reset,nearest)>9 || t>1 || (t<crossing.fraction()&&!gates.get(next).contains(reset)))
                        throw new IllegalArgumentException("checkpoint-spawn"+(next+1)+" must be within 3 blocks of its route segment, after the gate and before the next waypoint.");
                }
                targets[next++]=(i+1)%points.size();
            }
        }
        if(next!=gates.size()) throw new IllegalArgumentException("Bot route must cross every checkpoint and finish before looping to waypoint1.");
        return new KartBotRoute(List.copyOf(points),targets);
    }
    private static double distanceSquared(Location a,Location b) {
        return Math.pow(a.getX()-b.getX(),2)+Math.pow(a.getY()-b.getY(),2)+Math.pow(a.getZ()-b.getZ(),2);
    }
    private static double entry(Cuboid gate,Location from,Location to) {
        double[] a={from.getX(),from.getY(),from.getZ()},b={to.getX(),to.getY(),to.getZ()};
        double[] lo={gate.minX(),gate.minY(),gate.minZ()},hi={Math.nextDown(gate.maxX()+1d),Math.nextDown(gate.maxY()+1d),Math.nextDown(gate.maxZ()+1d)};
        double enter=0,exit=1;
        for(int axis=0;axis<3;axis++) {
            double delta=b[axis]-a[axis];
            if(!Double.isFinite(delta))return Double.NaN;
            if(delta==0){if(a[axis]<lo[axis]||a[axis]>hi[axis])return Double.NaN;continue;}
            double first=(lo[axis]-a[axis])/delta,last=(hi[axis]-a[axis])/delta;
            enter=Math.max(enter,Math.min(first,last));exit=Math.min(exit,Math.max(first,last));
            if(enter>exit)return Double.NaN;
        }
        return enter;
    }
}
