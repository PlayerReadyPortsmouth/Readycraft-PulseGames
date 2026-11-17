package uk.co.playerready.pulsegames.games.karts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uk.co.playerready.pulsegames.games.karts.KartPose.Vec;

/** Flat arena setting: antigrav-surface1 = x,y,z,nx,ny,nz,halfWidth; ... */
public record KartSurfaceConfig(List<KartSurface> surfaces) {
    public KartSurfaceConfig { surfaces=List.copyOf(surfaces); }
    public boolean configured(){return !surfaces.isEmpty();}
    public static KartSurfaceConfig load(Map<String,?> settings) {
        List<KartSurface> surfaces=new ArrayList<>();
        for(String key:settings.keySet()) {
            if(key.startsWith("antigrav-surface")&&!key.matches("antigrav-surface[1-9][0-9]?"))
                throw new IllegalArgumentException("Invalid surface key: "+key+"; use antigrav-surface1..32");
        }
        var keys=settings.keySet().stream().filter(k->k.startsWith("antigrav-surface"))
                .sorted(java.util.Comparator.comparingInt(k->Integer.parseInt(k.substring("antigrav-surface".length())))).toList();
        if(keys.size()>32)throw new IllegalArgumentException("At most 32 anti-gravity surfaces");
        for(String key:keys) {
            if(!key.equals("antigrav-surface"+(surfaces.size()+1)))
                throw new IllegalArgumentException("Number anti-gravity surfaces consecutively from 1");
            if(!(settings.get(key) instanceof String text))throw new IllegalArgumentException(key+": expected text nodes");
            if(text.length()>65536)throw new IllegalArgumentException(key+": surface setting too long");
            List<KartSurface.Node> nodes=new ArrayList<>();
            for(String row:text.split(";",-1)) {
                String[] v=row.trim().split(",",-1);
                if(v.length!=7)throw new IllegalArgumentException(key+": expected x,y,z,nx,ny,nz,halfWidth");
                try {
                    double[] n=new double[7];for(int i=0;i<7;i++)n[i]=Double.parseDouble(v[i].trim());
                    nodes.add(new KartSurface.Node(new Vec(n[0],n[1],n[2]),new Vec(n[3],n[4],n[5]),n[6]));
                } catch(NumberFormatException e){throw new IllegalArgumentException(key+": invalid coordinate",e);}
            }
            surfaces.add(new KartSurface(key,nodes));
        }
        return new KartSurfaceConfig(surfaces);
    }
}
