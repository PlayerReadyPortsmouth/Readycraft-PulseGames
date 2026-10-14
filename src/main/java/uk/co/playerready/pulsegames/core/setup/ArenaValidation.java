package uk.co.playerready.pulsegames.core.setup;

import org.bukkit.*;
import uk.co.playerready.pulsegames.core.game.GameMode;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import java.util.*;

/** Read-only pre-publication checks. Never loads chunks or changes the build. */
public final class ArenaValidation {
    public record Issue(boolean error,String message) {}
    public record Report(List<Issue> issues) {
        public Report {issues=List.copyOf(issues);}
        public boolean valid(){return issues.stream().noneMatch(Issue::error);}
    }
    private static void error(List<Issue> out,String message){out.add(new Issue(true,message));}
    public static Report check(ArenaSetupManager.Session s) {
        List<Issue> out=new ArrayList<>();World w=s.world;
        int min=s.minPlayers==null?2:s.minPlayers,max=s.maxPlayers==null?Math.max(2,s.spawns.size()):s.maxPlayers;
        if(min<1||max<min||max>128)error(out,"Player limits must satisfy 1 <= minimum <= maximum <= 128.");
        point(out,"Waiting lobby",s.lobby,w,true,true);
        point(out,"Spectator",s.spectator,w,true,false);
        for(int i=0;i<s.spawns.size();i++)point(out,"Spawn "+(i+1),s.spawns.get(i),w,true,true);
        for(SetupRequirement req:s.game.setup()){
            switch(req.kind()){
                case SPAWNS -> {if(req.required()&&s.spawns.size()<req.min())error(out,"Set at least "+req.min()+" spawns.");}
                case REGION -> {if(req.required()&&!s.regions.containsKey(req.key()))error(out,"Missing region: "+req.key());}
                case REGION_PREFIX -> {
                    List<Integer> ids=new ArrayList<>();
                    for(String key:s.regions.keySet())if(key.startsWith(req.key())){
                        String suffix=key.substring(req.key().length());
                        try{int n=Integer.parseInt(suffix);if(n<1||!Integer.toString(n).equals(suffix))throw new NumberFormatException();ids.add(n);}
                        catch(NumberFormatException ex){error(out,"Use numbered regions "+req.key()+"1, "+req.key()+"2, ... (found "+key+").");}
                    }
                    Collections.sort(ids);
                    if(req.required()&&ids.size()<req.min())error(out,"Need at least "+req.min()+" numbered "+req.key()+" regions.");
                    for(int i=0;i<ids.size();i++)if(ids.get(i)!=i+1){error(out,"Number "+req.key()+" regions consecutively from 1.");break;}
                }
                case LOCATION_SETTING -> point(out,req.key(),Objects.toString(s.settings.get(req.key()),null),w,req.required(),false);
                case VALUE_SETTING -> {if(req.required()&&!s.settings.containsKey(req.key()))error(out,"Missing setting: "+req.key());}
            }
        }
        s.regions.forEach((name,box)->{if(w!=null&&(box.minY()<w.getMinHeight()||box.maxY()>=w.getMaxHeight()))error(out,"Region "+name+" exceeds the world's build height.");});
        Object voidY=s.settings.get("void-y");
        if(voidY!=null){try{double y=Double.parseDouble(voidY.toString());if(!Double.isFinite(y))throw new NumberFormatException();
            for(String spawn:s.spawns)if(LocUtil.parse(spawn,w).getY()<=y){error(out,"A spawn is at or below void-y.");break;}
        }catch(RuntimeException ex){error(out,"void-y and spawn coordinates must be finite numbers.");}}
        for(String selected:s.modes)if(s.game.modes().stream().noneMatch(m->m.id().equals(selected)||GameMode.mapModeId(m.id()).equals(selected)))error(out,"Unknown mode: "+selected);
        for(GameMode mode:s.game.modes()) {
            if(!s.modes.isEmpty()&&s.modes.stream().noneMatch(id->id.equals(mode.id())||id.equals(GameMode.mapModeId(mode.id()))))continue;
            String prefix="modes."+mode.id()+".";String generic="modes."+GameMode.mapModeId(mode.id())+".";
            int modeMin=integer(out,s.settings.getOrDefault(prefix+"min-players",s.settings.getOrDefault(generic+"min-players",min)),mode.id()+" minimum",min);
            int modeMax=integer(out,s.settings.getOrDefault(prefix+"max-players",s.settings.getOrDefault(generic+"max-players",max)),mode.id()+" maximum",max);
            if(modeMin<1||modeMax<modeMin||modeMax>128||Math.max(modeMin,mode.minPlayers())>Math.min(modeMax,mode.maxPlayers()))error(out,"Mode "+mode.id()+" has incompatible player limits.");
            if(mode.isTeams()){
                int capacity=s.game.id().equals("duels")?2:32;
                int count=integer(out,s.settings.getOrDefault(prefix+"team-count",s.settings.getOrDefault(generic+"team-count",s.settings.getOrDefault("team-count",Math.min(capacity,s.spawns.size())))),mode.id()+" team count",0);
                if(count>capacity)error(out,"Mode "+mode.id()+" supports at most "+capacity+" teams.");
                if(count<2||count>s.spawns.size())error(out,"Mode "+mode.id()+" needs 2 or more teams, with one spawn per team.");
                if(count*mode.teamSize()<Math.max(modeMin,mode.minPlayers()))error(out,"Mode "+mode.id()+" cannot seat its minimum player count.");
                if(s.game.id().equals("bedwars"))for(int i=1;i<=Math.min(count,128);i++){
                    if(!s.regions.containsKey("bed"+i))error(out,"Missing bed region for team "+i);
                    point(out,"generator"+i,Objects.toString(s.settings.get("generator"+i),null),w,true,false);
                }
            }
        }
        if(s.game.id().equals("deathrun")){
            long traps=s.regions.keySet().stream().filter(k->k.matches("trap[1-9][0-9]*")).count();
            long triggers=s.regions.keySet().stream().filter(k->k.matches("trigger[1-9][0-9]*")).count();
            if(traps!=triggers)error(out,"Each trap needs a matching trigger with the same number.");
        }
        if(s.game.id().equals("dropper"))for(String name:s.regions.keySet())if(name.matches("checkpoint[1-9][0-9]*"))point(out,"checkpoint-spawn"+name.substring(10),Objects.toString(s.settings.get("checkpoint-spawn"+name.substring(10)),null),w,true,true);
        s.settings.forEach((key, value) -> {
            if (!Set.of("bot-patrol-points", "bot-hide-points", "bot-recovery-points").contains(key) && !key.startsWith("bot-route-")) return;
            if (!(value instanceof List<?> points)) { error(out, key + " must be a location list."); return; }
            if (points.size() > 128) error(out, key + " exceeds 128 points.");
            if (key.startsWith("bot-route-") && points.size() < 2) error(out, key + " needs at least two ordered waypoints.");
            for (int i = 0; i < Math.min(128, points.size()); i++)
                point(out, key + " #" + (i + 1), Objects.toString(points.get(i), null), w, true, true);
        });
        if(!s.regions.containsKey("bounds"))out.add(new Issue(false,"No bounds region recorded. Verify the playable boundary during map testing."));
        return new Report(out.stream().distinct().toList());
    }
    static int integer(List<Issue> out,Object raw,String label,int fallback){try{return Integer.parseInt(raw.toString());}catch(RuntimeException ex){error(out,label+" must be a whole number.");return fallback;}}
    static void point(List<Issue> out,String label,String raw,World w,boolean required,boolean standable){
        if(raw==null||raw.isBlank()){if(required)error(out,"Missing "+label+".");return;}
        Location loc;
        try{loc=LocUtil.parse(raw,w);}catch(RuntimeException ex){error(out,label+" has invalid coordinates.");return;}
        if(w==null){error(out,"Load the map world before validation.");return;}
        if(loc.getY()<w.getMinHeight()||loc.getY()>=w.getMaxHeight()-1||!w.getWorldBorder().isInside(loc)){error(out,label+" lies outside world height/border.");return;}
        if(!w.isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4)){out.add(new Issue(false,label+": visit this chunk to check physical clearance."));return;}
        if(!standable)return;
        var feet=loc.getBlock();var head=feet.getRelative(org.bukkit.block.BlockFace.UP);var below=feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        if(!feet.isPassable()||!head.isPassable())error(out,label+" does not have clear feet/head space.");
        if(feet.isLiquid()||head.isLiquid())out.add(new Issue(false,label+" is in liquid; check mode suitability."));
        if(!below.getType().isSolid())out.add(new Issue(false,label+" has no solid floor directly beneath it; verify this launch/spawn."));
    }
}
