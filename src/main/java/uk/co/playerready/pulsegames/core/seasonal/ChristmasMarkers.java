package uk.co.playerready.pulsegames.core.seasonal;

import org.bukkit.Location;
import uk.co.playerready.pulsegames.core.game.GameInstance;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import java.util.*;

/** Marker names are values, never YAML paths: repeated spawn signs must survive capture. */
public final class ChristmasMarkers {
    private final GameInstance game;
    private Map<String,List<Location>> index;
    private final Map<String,List<String>> prefixes=new HashMap<>();
    public ChristmasMarkers(GameInstance game) { this.game = game; }
    private Map<String,List<Location>> index() {
        if(index!=null)return index;
        if(game.world()==null)throw new IllegalStateException("Markers require the cloned world");
        Map<String,List<Location>> parsed=new HashMap<>();
        for(var row:game.arena().settings().getMapList("markers")) {
            String key=String.valueOf(row.get("key"));
            parsed.computeIfAbsent(key,k->new ArrayList<>()).add(LocUtil.parse(String.valueOf(row.get("location")),game.world()));
        }
        parsed.replaceAll((key,value)->List.copyOf(value));index=Map.copyOf(parsed);return index;
    }
    public List<Location> points(String key) {
        return index().getOrDefault(key,List.of()).stream().map(Location::clone).toList();
    }
    public Location point(String key) {
        var values=index().get(key);return values==null||values.isEmpty()?null:values.getFirst().clone();
    }
    public List<String> keys(String prefix) {
        return prefixes.computeIfAbsent(prefix,p->index().keySet().stream().filter(k->k.startsWith(p)).sorted(ChristmasMarkers::naturalCompare).toList());
    }
    static int naturalCompare(String a, String b) {
        String[] x=a.split("\\."), y=b.split("\\.");
        for(int i=0;i<Math.min(x.length,y.length);i++) {
            int c;
            if(x[i].matches("[0-9]+") && y[i].matches("[0-9]+")) {
                c=Integer.compare(x[i].length(), y[i].length());
                if(c==0)c=x[i].compareTo(y[i]);
            } else c=x[i].compareTo(y[i]);
            if(c!=0)return c;
        }
        return Integer.compare(x.length,y.length);
    }
}
