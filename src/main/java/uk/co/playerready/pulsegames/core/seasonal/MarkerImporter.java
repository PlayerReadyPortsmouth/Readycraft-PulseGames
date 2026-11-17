package uk.co.playerready.pulsegames.core.seasonal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.util.LocUtil;
import uk.co.playerready.pulsegames.core.util.YamlFiles;
import uk.co.playerready.pulsegames.core.world.WorldService;
import java.io.*;
import java.util.*;

/** Bounded, paced scan -> validated manifest -> strip -> capture -> publish arena. */
public final class MarkerImporter {
    private final PulseGamesPlugin plugin;
    private boolean busy;
    public MarkerImporter(PulseGamesPlugin plugin) { this.plugin=plugin; }
    public boolean busy() { return busy; }
    public void scan(CommandSender sender,World world,String game,String id,int[] a,int[] b,boolean check) {
        if(busy)throw new IllegalArgumentException("A Christmas import is already running.");
        if(!Set.of("presentpanic","grinchhunt").contains(game))throw new IllegalArgumentException("Use presentpanic or grinchhunt.");
        if(!WorldService.isValidTemplateId(id))throw new IllegalArgumentException("Invalid arena id.");
        if(plugin.instances().all().stream().anyMatch(g->world.equals(g.world())))throw new IllegalArgumentException("Scan a build world, never a running match.");
        int minX=Math.min(a[0],b[0]),maxX=Math.max(a[0],b[0]),minY=Math.min(a[1],b[1]),maxY=Math.max(a[1],b[1]),minZ=Math.min(a[2],b[2]),maxZ=Math.max(a[2],b[2]);
        if((long)maxX-minX>=200 || (long)maxZ-minZ>=200 || minY<world.getMinHeight() || maxY>=world.getMaxHeight())
            throw new IllegalArgumentException("Scan must fit 200 × 200 and the world's height range.");
        File arena=new File(plugin.getDataFolder(),"arenas/"+game+"-"+id+".yml");
        if(!check && (arena.exists()||plugin.worlds().templateDir(id).exists()))throw new IllegalArgumentException("Choose a new arena id; existing maps are preserved.");
        busy=true;say(sender,"Scanning one chunk per tick. Keep the build world unchanged until capture completes.");
        List<Sign> signs=new ArrayList<>();List<Map<String,Object>> rows=new ArrayList<>();List<String> errors=new ArrayList<>();
        new BukkitRunnable() {
            int cx=minX>>4,cz=minZ>>4;
            public void run() {
                try {
                    for(BlockState state:world.getChunkAt(cx,cz).getTileEntities()) {
                        if(state.getX()<minX||state.getX()>maxX||state.getY()<minY||state.getY()>maxY||state.getZ()<minZ||state.getZ()>maxZ)continue;
                        if(!(state instanceof Sign sign))continue;
                        var front=sign.getSide(Side.FRONT);
                        if(!plain(front.line(0)).equals("[pulse]"))continue;
                        if(state.getType()!=Material.OAK_SIGN) {errors.add("Marker must be a standing oak sign at "+state.getLocation());continue;}
                        String key=plain(front.line(1)),facing=plain(front.line(2));
                        if(!MarkerContract.validKey(game,key)) {errors.add("Unknown marker: "+key);continue;}
                        float yaw;
                        try {yaw=MarkerContract.yaw(facing);}catch(IllegalArgumentException invalid){errors.add(key+": "+invalid.getMessage());continue;}
                        Location at=state.getLocation().add(.5,0,.5);at.setYaw(yaw);
                        signs.add(sign);rows.add(Map.of("key",key,"location",LocUtil.serialize(at),"facing",facing,"notes",plain(front.line(3))));
                    }
                    if(++cz>(maxZ>>4)) {cz=minZ>>4;cx++;}
                    if(cx>(maxX>>4)) {cancel();finish(sender,world,game,id,arena,signs,rows,errors,check);}
                } catch(Exception failure) {cancel();busy=false;say(sender,"Scan failed; signs retained: "+failure.getMessage());}
            }
        }.runTaskTimer(plugin,1,1);
    }
    private void finish(CommandSender sender,World world,String game,String id,File arena,List<Sign> signs,List<Map<String,Object>> rows,List<String> errors,boolean check) throws IOException {
        errors.addAll(MarkerContract.problems(game,rows.stream().map(row->String.valueOf(row.get("key"))).toList()));
        if(!errors.isEmpty()) {busy=false;say(sender,"Marker validation failed; nothing removed.");errors.stream().distinct().limit(40).forEach(e->say(sender,e));return;}
        if(check) {busy=false;say(sender,"Validated "+rows.size()+" markers. Check only; signs retained.");return;}
        for(Sign snapshot:signs) {
            if(!(snapshot.getBlock().getState() instanceof Sign current) || !snapshot.getBlockData().equals(current.getBlockData())) {
                busy=false;say(sender,"A scanned marker changed; nothing removed. Run the scan again.");return;
            }
            for(int line=0;line<4;line++)if(!snapshot.getSide(Side.FRONT).line(line).equals(current.getSide(Side.FRONT).line(line))) {
                busy=false;say(sender,"A scanned marker changed; nothing removed. Run the scan again.");return;
            }
        }
        YamlConfiguration definition=definition(game,id,rows);
        File manifests=new File(plugin.getDataFolder(),"marker-manifests");manifests.mkdirs();
        YamlConfiguration recovery=new YamlConfiguration();recovery.set("world",world.getName());recovery.set("markers",rows);
        recovery.set("status","validated-before-strip");
        File manifest=new File(manifests,game+"-"+id+".yml");YamlFiles.writeAtomically(manifest,recovery.saveToString());
        // All signs and their note/facing data are durably recorded before changing the world.
        try {
            for(Sign sign:signs)sign.getBlock().setType(Material.AIR,false);
            plugin.worlds().saveAsTemplate(world,id,()-> {
                try {
                    recovery.set("status","captured");YamlFiles.writeAtomically(manifest,recovery.saveToString());
                    arena.getParentFile().mkdirs();YamlFiles.writeAtomically(arena,definition.saveToString());
                    plugin.arenas().load();plugin.maps().captured(game,id);
                    say(sender,"Saved "+id+" with "+rows.size()+" markers and removed the signs. Seasonal gate still applies.");
                } catch(IOException|RuntimeException failure) {restore(signs);say(sender,"Arena publication failed; signs restored. Manifest: "+manifest+". "+failure.getMessage());}
                finally {busy=false;}
            },error->{restore(signs);busy=false;say(sender,"Capture failed; signs restored: "+error);});
        } catch(RuntimeException failure) {restore(signs);busy=false;throw failure;}
    }
    static YamlConfiguration definition(String game,String id,List<Map<String,Object>> rows) {
        YamlConfiguration y=new YamlConfiguration();String p=game.equals("presentpanic")?"pp":"gh";
        y.set("id",id);y.set("game",game);y.set("world-template",id);y.set("display-name",id);
        y.set("min-players",2);y.set("max-players",24);y.set("modes",List.of(game.equals("presentpanic")?"teams":"classic"));
        y.set("lobby",location(rows,p+".return"));y.set("spectator",location(rows,p+".spectator"));
        List<String> spawns=new ArrayList<>();
        if(p.equals("pp"))for(String c:List.of("red","blue","green","gold"))spawns.add(location(rows,"pp.spawn."+c));
        else rows.stream().filter(r->r.get("key").equals("gh.villager.spawn")).forEach(r->spawns.add(String.valueOf(r.get("location"))));
        y.set("spawns",spawns);y.set("settings.markers",rows);y.set("settings.marker-contract",1);y.set("settings.team-count",4);
        return y;
    }
    private static String location(List<Map<String,Object>> rows,String key) {return rows.stream().filter(r->key.equals(r.get("key"))).map(r->String.valueOf(r.get("location"))).findFirst().orElseThrow();}
    private static void restore(List<Sign> signs) {for(Sign sign:signs)sign.update(true,false);}
    private static String plain(Component c) {return PlainTextComponentSerializer.plainText().serialize(c);}
    private static void say(CommandSender sender,String text) {sender.sendMessage(Component.text(text));}
}
