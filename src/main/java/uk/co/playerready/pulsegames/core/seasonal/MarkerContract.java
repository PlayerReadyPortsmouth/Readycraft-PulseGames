package uk.co.playerready.pulsegames.core.seasonal;

import java.util.*;
import java.util.regex.Pattern;

/** The 16 November builder contract, validated before any world mutation. */
public final class MarkerContract {
    public static final List<String> COLOURS=List.of("red","green","blue","gold");
    private static final String ID="[a-z0-9_-]+";
    private static final Pattern PP=Pattern.compile("pp\\.(spawn\\.(red|green|blue|gold)|belt\\.[12]\\.(feed|[1-9][0-9]*)|wrap\\.(red|green|blue|gold)\\."+ID+"|bow\\.(red|green|blue|gold)|sleigh\\.(red|green|blue|gold)(\\.launch)?|event\\.naughty|spectator|return|bounds\\.(min|max))");
    private static final Pattern GH=Pattern.compile("gh\\.(grinch\\.spawn|villager\\.spawn|tree\\."+ID+"|cave\\.(pile|door|back)|chimney\\."+ID+"|snow\\.fresh\\."+ID+"\\.(min|max)|path\\.cleared\\."+ID+"\\.(min|max)|bell\\."+ID+"|kennel|spectator|return|bounds\\.(min|max))");
    private MarkerContract() {}
    public static boolean validKey(String game,String key) {
        return (game.equals("presentpanic") ? PP : GH).matcher(key).matches();
    }
    public static float yaw(String facing) {
        return switch(facing) { case "", "s" -> 0; case "w" -> 90; case "n" -> 180; case "e" -> -90;
            default -> throw new IllegalArgumentException("Facing must be blank, n, e, s or w"); };
    }
    public static List<String> problems(String game,List<String> keys) {
        List<String> errors=new ArrayList<>();Map<String,Integer> counts=new TreeMap<>();
        for(String key:keys) {
            if(!validKey(game,key))errors.add("Unknown marker: "+key);
            counts.merge(key,1,Integer::sum);
        }
        String p=game.equals("presentpanic")?"pp":"gh";
        for(String key:List.of(p+".return",p+".bounds.min",p+".bounds.max"))need(counts,key,1,errors);
        need(counts,p+".spectator",4,errors);
        for(var entry:counts.entrySet()) {
            boolean repeats=entry.getKey().matches("pp\\.spawn\\..+|pp\\.spectator|gh\\.(grinch\\.spawn|villager\\.spawn|spectator)");
            if(!repeats && entry.getValue()!=1)errors.add("Duplicate marker: "+entry.getKey());
        }
        if(p.equals("pp")) {
            for(String colour:COLOURS) {
                need(counts,"pp.spawn."+colour,6,errors);need(counts,"pp.bow."+colour,1,errors);
                need(counts,"pp.sleigh."+colour,1,errors);need(counts,"pp.sleigh."+colour+".launch",1,errors);
                long wraps=counts.keySet().stream().filter(k->k.startsWith("pp.wrap."+colour+".")).count();
                if(wraps<4)errors.add("Need four wrapping stations for "+colour);
            }
            need(counts,"pp.event.naughty",1,errors);
            for(int belt=1;belt<=2;belt++) {
                String prefix="pp.belt."+belt+".";need(counts,prefix+"feed",1,errors);
                int nodes=(int)counts.keySet().stream().filter(k->k.startsWith(prefix)&&!k.endsWith("feed")).count();
                if(nodes<2)errors.add("Need at least two path nodes for belt "+belt);
                for(int i=1;i<=nodes;i++)need(counts,prefix+i,1,errors);
            }
        } else {
            need(counts,"gh.grinch.spawn",3,errors);need(counts,"gh.villager.spawn",12,errors);
            for(String suffix:List.of("pile","door","back"))need(counts,"gh.cave."+suffix,1,errors);
            need(counts,"gh.kennel",1,errors);
            prefix(counts,"gh.tree.",16,errors);prefix(counts,"gh.chimney.",10,errors);prefix(counts,"gh.bell.",3,errors);
            for(String prefix:List.of("gh.snow.fresh.","gh.path.cleared.")) {
                prefix(counts,prefix,2,errors);
                for(String key:counts.keySet())if(key.startsWith(prefix)) {
                    String base=key.substring(0,key.lastIndexOf('.'));
                    need(counts,base+".min",1,errors);need(counts,base+".max",1,errors);
                }
            }
        }
        return errors.stream().distinct().toList();
    }
    private static void prefix(Map<String,Integer> counts,String prefix,int count,List<String> errors) {
        if(counts.keySet().stream().filter(k->k.startsWith(prefix)).count()<count)errors.add("Need "+count+" markers for "+prefix);
    }
    private static void need(Map<String,Integer> counts,String key,int count,List<String> errors) {
        if(counts.getOrDefault(key,0)<count)errors.add("Need "+count+" × "+key+" (found "+counts.getOrDefault(key,0)+")");
    }
}
