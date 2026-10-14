package uk.co.playerready.pulsegames.core.maps;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import uk.co.playerready.pulsegames.PulseGamesPlugin;
import uk.co.playerready.pulsegames.core.arena.Arena;
import uk.co.playerready.pulsegames.core.setup.SavedArenaValidation;
import uk.co.playerready.pulsegames.core.world.WorldService;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only checks plus explicit review records; never publishes maps or grants approval automatically. */
public final class MapReviewService {
    private final PulseGamesPlugin plugin;
    private MapReviewStore store;
    public MapReviewService(PulseGamesPlugin plugin) {
        this.plugin=plugin;
        try { store=new MapReviewStore(plugin.getDataFolder().toPath().resolve("map-reviews.yml")); }
        catch(IOException failure) { plugin.getLogger().warning("Map review records unavailable; preserving existing ledger: "+failure.getMessage()); }
    }
    public void captured(String game,String map) {
        if(store==null) return;
        try { store.revision(game,map,"capture-"+UUID.randomUUID().toString().substring(0,8)); }
        catch(IOException|IllegalArgumentException failure) { plugin.getLogger().warning("Could not invalidate map reviews after capture: "+failure.getMessage()); }
    }
    public void handle(CommandSender sender,String[] args) {
        if(!sender.hasPermission("pulsegames.admin")) { say(sender,"No permission.");return; }
        try {
            if(args.length==0 || args[0].equalsIgnoreCase("list")) { list(sender,args.length>1?args[1]:null);return; }
            if(args.length<3) { usage(sender);return; }
            String action=args[0].toLowerCase(Locale.ROOT),game=args[1],map=args[2];
            if(!MapReviewStore.safe(game) || plugin.registry().get(game)==null || !MapReviewStore.safe(map)) { say(sender,"Unknown game or invalid map ID.");return; }
            if(action.equals("check") || action.equals("report")) {
                var lines=report(game,map); lines.stream().limit(100).forEach(line->say(sender,line));
                if(lines.size()>100) say(sender,"Chat report truncated; use /pulse maps export for the full report.");return;
            }
            if(action.equals("export")) {
                var rows=report(game,map);
                Path directory=plugin.getDataFolder().toPath().resolve("map-reports");Files.createDirectories(directory);
                Path output=directory.resolve(game+"-"+map+".html");
                Files.writeString(output,html(game+" / "+map,rows));
                say(sender,"Exported standalone report: "+output.toAbsolutePath());return;
            }
            if(store==null) { say(sender,"Review ledger could not be loaded. Checks remain available; repair the ledger before recording reviews.");return; }
            if(action.equals("register") && args.length==3) {
                store.register(game,map);say(sender,"Registered for review. No build, visual or playtest approval has been inferred.");return;
            }
            if(action.equals("revision") && args.length==4) {
                if(!MapReviewStore.safe(args[3]) || args[3].length()>48) throw new IllegalArgumentException("Use a revision label of 1-48 lower-case letters, numbers, underscores or hyphens.");
                store.revision(game,map,args[3]+"-"+UUID.randomUUID().toString().substring(0,8));
                say(sender,"New build revision recorded. Previous review evidence is retained but needs review again.");return;
            }
            if(action.equals("mark") && args.length>=6) {
                MapReviewStore.Check check=MapReviewStore.Check.valueOf(args[3].toUpperCase(Locale.ROOT));
                MapReviewStore.Outcome outcome=MapReviewStore.Outcome.valueOf(args[4].toUpperCase(Locale.ROOT));
                if(check==MapReviewStore.Check.VISUAL && !sender.hasPermission("pulsegames.maps.approve")) {
                    say(sender,"Visual review records require pulsegames.maps.approve.");return;
                }
                String note=String.join(" ",Arrays.copyOfRange(args,5,args.length));
                String actor=sender instanceof Player player?player.getUniqueId().toString():sender.getName();
                store.mark(game,map,check,outcome,currentFingerprint(plugin.arenas().byId(game,map)),actor,note);
                say(sender,"Recorded "+check+" = "+outcome+". This records your evidence; it does not publish or enable the map.");return;
            }
            usage(sender);
        } catch(IllegalArgumentException invalid) { say(sender,"Invalid review command: "+invalid.getMessage());usage(sender); }
        catch(IOException failure) { say(sender,"Could not save the review/report. Existing review evidence was kept.");plugin.getLogger().warning("Map review write failed: "+failure.getMessage()); }
    }
    public List<String> report(String game,String map) {
        var type=plugin.registry().get(game);
        if(!MapReviewStore.safe(game) || type==null || !MapReviewStore.safe(map)) throw new IllegalArgumentException("Unknown game or invalid map ID");
        Arena arena=plugin.arenas().byId(game,map);
        var entry=store==null?null:store.get(game,map);
        String revision=entry==null?"unregistered":entry.revision(),fingerprint=currentFingerprint(arena);
        List<String> lines=new ArrayList<>();
        lines.add("Map: "+game+" / "+map+(arena==null?"":" — "+arena.displayName()));
        lines.add("Build revision: "+revision+" | Configuration: "+fingerprint);
        if(store==null) lines.add("Review ledger unavailable: no approval status can be inferred.");
        for(var check:MapReviewStore.Check.values()) {
            var mark=entry==null?null:entry.marks().get(check);
            lines.add(check+": "+(mark==null?"NOT RECORDED":(mark.current(revision,fingerprint)?"RECORDED ":"STALE ")+mark.outcome()
                    +" | "+mark.at()+" | reviewer="+mark.actor()+" | "+mark.note()));
        }
        if(arena==null) lines.add("CONFIGURED: NO — no loaded arena definition. Registering/reviewing a build does not configure it.");
        else {
            var report=SavedArenaValidation.check(type,arena,null);
            boolean template=WorldService.isValidTemplateId(arena.worldTemplate())
                    && Files.isDirectory(plugin.worlds().templateDir(arena.worldTemplate()).toPath(),LinkOption.NOFOLLOW_LINKS);
            lines.add("CONFIGURED: "+(report.configurationValid() && template?"YES (configuration only)":"NO — errors or missing template"));
            if(!template) lines.add("ERROR: Missing or invalid captured template folder: "+arena.worldTemplate());
            report.checklist().stream().limit(120).forEach(check->lines.add(check.status()+": "+check.label()+" — "+check.detail()));
            report.validation().issues().stream().limit(120).forEach(issue->lines.add((issue.error()?"ERROR: ":"CHECK: ")+issue.message()));
            if(report.checklist().size()>120 || report.validation().issues().size()>120) lines.add("Additional checks truncated; fix the reported issues and rerun validation.");
        }
        lines.add("No worlds/chunks were loaded and no gameplay was simulated. Inspect the build, then test Java, Bedrock and bots separately.");
        lines.add("External terrain edits require /pulse maps revision before reusing approval evidence. Captures and configuration changes invalidate current evidence.");
        return List.copyOf(lines);
    }
    private void list(CommandSender sender,String filter) {
        Set<String> keys=new TreeSet<>();
        plugin.arenas().all().forEach(a->keys.add(a.gameId()+"/"+a.id()));
        if(store!=null) store.all().forEach(e->keys.add(e.game()+"/"+e.map()));
        var selected=keys.stream().filter(k->filter==null||k.startsWith(filter+"/")).toList();
        say(sender,"Map review records ("+selected.size()+"): /pulse maps check <game> <map>");
        selected.stream().limit(40).forEach(k->say(sender,k));
        if(selected.size()>40)say(sender,"Showing 40. Filter with /pulse maps list <game>.");
    }
    private String currentFingerprint(Arena arena) {
        String config=fingerprint(arena);
        if(arena==null || !WorldService.isValidTemplateId(arena.worldTemplate())) return config;
        Path template=plugin.worlds().templateDir(arena.worldTemplate()).toPath();
        if(!Files.isDirectory(template,LinkOption.NOFOLLOW_LINKS)) return config+":missing-template";
        Path marker=template.resolve(".pulse-capture-revision");
        try {
            if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS)) return config+":legacy";
            if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)) return config+":unreadable-"+UUID.randomUUID();
            if(Files.size(marker)>100) return config+":unreadable-"+UUID.randomUUID();
            String capture=Files.readString(marker).strip();
            if(!capture.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return config+":unreadable-"+UUID.randomUUID();
            return config+":"+capture;
        } catch(IOException failure) { return config+":unreadable-"+UUID.randomUUID(); }
    }
    public static String fingerprint(Arena arena) {
        if(arena==null)return "unconfigured";
        SortedMap<String,Object> values=new TreeMap<>();
        values.put("game",arena.gameId());values.put("id",arena.id());values.put("name",arena.displayName());values.put("template",arena.worldTemplate());
        values.put("modes",arena.modes());values.put("min",arena.minPlayers());values.put("max",arena.maxPlayers());
        values.put("lobby",Objects.toString(arena.rawLobby(),""));values.put("spectator",Objects.toString(arena.rawSpectator(),""));values.put("spawns",arena.rawSpawns());
        arena.regions().forEach((key,box)->values.put("region:"+key,List.of(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())));
        arena.settings().getValues(true).forEach((key,value)->{if(!(value instanceof org.bukkit.configuration.ConfigurationSection))values.put("setting:"+key,value);});
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(values).getBytes(StandardCharsets.UTF_8))).substring(0,24); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String canonical(Object value) {
        if(value instanceof Map<?,?> map) {
            var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(String.valueOf(k),v));
            return sorted.entrySet().stream().map(e->canonical(e.getKey())+canonical(e.getValue())).collect(java.util.stream.Collectors.joining("","{","}"));
        }
        if(value instanceof List<?> list)return list.stream().map(MapReviewService::canonical).collect(java.util.stream.Collectors.joining("","[","]"));
        String text=String.valueOf(value);return (value==null?"null":value.getClass().getName())+":"+text.length()+":"+text;
    }
    static String html(String title,List<String> lines) {
        return "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Map review</title>"
                +"<style>body{font:16px/1.55 system-ui;max-width:1000px;margin:40px auto;padding:0 24px;color:#193b37}h1{border-bottom:3px solid #27816c}li{margin:12px 0;overflow-wrap:anywhere}@media print{body{margin:0;font-size:11pt}li{break-inside:avoid}}</style>"
                +"<h1>"+escape(title)+"</h1><p>Dated review snapshot: "+java.time.Instant.now()+". Print or save as PDF from your browser.</p><ul>"
                +lines.stream().map(line->"<li>"+escape(line)+"</li>").collect(java.util.stream.Collectors.joining())+"</ul></html>";
    }
    private static String escape(String text) {return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void say(CommandSender sender,String text) {sender.sendMessage(Component.text(text));}
    private static void usage(CommandSender sender) {
        say(sender,"/pulse maps list [game] | check/export/register <game> <map>");
        say(sender,"/pulse maps revision <game> <map> <build-label>");
        say(sender,"/pulse maps mark <game> <map> <built|visual|java|bedrock|bots> <pass|fail|pending> <evidence note>");
    }
}
