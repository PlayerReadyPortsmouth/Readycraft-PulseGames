package uk.co.playerready.pulsegames.games.blockparty;

import java.util.*;
import java.util.random.RandomGenerator;

/** Bounded, deterministic pattern and walkable-route planning, independent of the world API. */
final class DanceFloor {
    enum Pattern { MOSAIC, DIAGONALS, CROSSED, RINGS }
    record Layout(Pattern pattern, int[] colors) {}
    record Call(int color, int seconds) {}
    static Layout create(int width, int depth, int round, boolean hardcore, Pattern previous, RandomGenerator random) {
        if (width < 1 || depth < 1 || (long)width * depth > 4096) throw new IllegalArgumentException("Floor must contain 1–4096 tiles");
        var patterns = new ArrayList<>(List.of(Pattern.values())); patterns.remove(previous);
        Pattern pattern = patterns.get(random.nextInt(patterns.size()));
        int count = Math.min(10, (hardcore ? 6 : 5) + Math.max(0,round-1)/(hardcore ? 3 : 4));
        int patch = round < 6 ? 4 : 3;
        List<Integer> palette = new ArrayList<>(); for(int i=0;i<10;i++) palette.add(i);
        for(int i=palette.size()-1;i>0;i--) Collections.swap(palette,i,random.nextInt(i+1));
        int offsetX=random.nextInt(patch*2), offsetZ=random.nextInt(patch*2);
        boolean mirrorX=random.nextBoolean(), mirrorZ=random.nextBoolean();
        int[] cells=new int[width*depth];
        for(int x=0;x<width;x++) for(int z=0;z<depth;z++) {
            int a=(mirrorX ? width-1-x : x)+offsetX, b=(mirrorZ ? depth-1-z : z)+offsetZ;
            int value=switch(pattern) {
                case MOSAIC -> (a+(b/patch%2)*(patch/2))/patch+(b/patch)*2;
                case DIAGONALS -> (a+b)/patch;
                case CROSSED -> a/patch+(b/patch)*2;
                case RINGS -> Math.max(Math.abs(2*a-width),Math.abs(2*b-depth))/(2*patch);
            };
            cells[x*depth+z]=palette.get(Math.floorMod(value,count));
        }
        return new Layout(pattern,cells);
    }

    /** A colour must be reachable from every active player through clear floor cells.
     * Prefer the intended deadline; only extend when the actual layout needs it, at most 10s.
     * Three blocks/sec plus one second to react leaves margin below normal walking speed. */
    static Call choose(int width,int depth,int[] colors,boolean[] clear,List<Integer> starts,
                       int baseSeconds,int previous,RandomGenerator random) {
        if(colors.length!=width*depth || clear.length!=colors.length || starts.isEmpty()) return null;
        List<Call> choices=new ArrayList<>();int best=11;
        for(int color : Arrays.stream(colors).distinct().toArray()) {
            if(color<0) continue;
            int[] distances=new int[colors.length];Arrays.fill(distances,-1);
            int[] queue=new int[colors.length];int head=0,tail=0;
            for(int i=0;i<colors.length;i++) if(clear[i]&&colors[i]==color){distances[i]=0;queue[tail++]=i;}
            while(head<tail) {
                int at=queue[head++],x=at/depth,z=at%depth;
                int[] near={x>0?at-depth:-1,x+1<width?at+depth:-1,z>0?at-1:-1,z+1<depth?at+1:-1};
                for(int next:near) if(next>=0&&clear[next]&&distances[next]<0){distances[next]=distances[at]+1;queue[tail++]=next;}
            }
            int far=0;boolean valid=true;
            for(int at:starts) { if(at<0||at>=distances.length||distances[at]<0){valid=false;break;} far=Math.max(far,distances[at]); }
            if(!valid) continue;
            int seconds=Math.max(baseSeconds,1+(far+2)/3);
            if(seconds>10 || seconds>best) continue;
            if(seconds<best){choices.clear();best=seconds;}
            choices.add(new Call(color,seconds));
        }
        var fresh=choices.stream().filter(c->c.color()!=previous).toList();
        if(!fresh.isEmpty())choices=new ArrayList<>(fresh);
        return choices.isEmpty()?null:choices.get(random.nextInt(choices.size()));
    }
}
