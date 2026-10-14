package uk.co.playerready.pulsegames.core.maps;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Explicit review evidence, separate from arena configuration and publication. */
public final class MapReviewStore {
    public enum Check { BUILT, VISUAL, JAVA, BEDROCK, BOTS }
    public enum Outcome { PASS, FAIL, PENDING }
    public record Mark(Outcome outcome, String revision, String configuration, String actor, String at, String note) {
        public boolean current(String build, String config) { return revision.equals(build) && configuration.equals(config); }
    }
    public record Entry(String game, String map, String revision, Map<Check,Mark> marks) {
        public Entry { marks=Map.copyOf(marks); }
    }
    private final Path file;
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    public MapReviewStore(Path file) throws IOException {
        this.file=file;
        if(!Files.exists(file)) return;
        if(Files.size(file)>4_000_000) throw new IOException("Review ledger exceeds size limit");
        var data=new YamlConfiguration();
        try { data.load(file.toFile()); } catch(org.bukkit.configuration.InvalidConfigurationException invalid) { throw new IOException("Invalid review ledger",invalid); }
        var rows=data.getConfigurationSection("maps");
        if(rows==null) { if(data.contains("maps")) throw new IOException("Invalid maps ledger section"); return; }
        for(String game:rows.getKeys(false)) {
            var maps=rows.getConfigurationSection(game); if(maps==null || !safe(game)) throw new IOException("Invalid game review entry");
            for(String map:maps.getKeys(false)) {
                if(!safe(map) || entries.size()>=512) throw new IOException("Invalid or oversized review ledger");
                var row=maps.getConfigurationSection(map); if(row==null) throw new IOException("Invalid map review entry");
                String revision=row.getString("revision","initial");
                Map<Check,Mark> marks=new EnumMap<>(Check.class);
                for(Check check:Check.values()) {
                    var mark=row.getConfigurationSection("checks."+check.name()); if(mark==null) continue;
                    try { marks.put(check,new Mark(Outcome.valueOf(mark.getString("outcome","PENDING")),
                            mark.getString("revision",""),mark.getString("configuration",""),mark.getString("actor",""),
                            mark.getString("at",""),mark.getString("note",""))); }
                    catch(IllegalArgumentException invalid) { throw new IOException("Invalid review outcome",invalid); }
                }
                entries.put(key(game,map),new Entry(game,map,revision,marks));
            }
        }
    }
    public static boolean safe(String value) { return value!=null && value.matches("[a-z0-9_-]{1,64}"); }
    private static String key(String game,String map) { if(!safe(game)||!safe(map)) throw new IllegalArgumentException("Use lower-case map/game IDs containing letters, numbers, underscore or hyphen."); return game+"/"+map; }
    public synchronized Entry get(String game,String map) { return entries.get(key(game,map)); }
    public synchronized List<Entry> all() { return List.copyOf(entries.values()); }
    public synchronized void register(String game,String map) throws IOException {
        String key=key(game,map); if(entries.containsKey(key)) return;
        if(entries.size()>=512) throw new IllegalArgumentException("Review ledger is full (512 maps).");
        replace(key,new Entry(game,map,"initial",Map.of()));
    }
    public synchronized void revision(String game,String map,String revision) throws IOException {
        if(!safe(revision)) throw new IllegalArgumentException("Revision must be a short lower-case ID.");
        register(game,map); var old=get(game,map);
        replace(key(game,map),new Entry(game,map,revision,old.marks()));
    }
    public synchronized void mark(String game,String map,Check check,Outcome outcome,String fingerprint,String actor,String note) throws IOException {
        if(note==null || note.isBlank() || note.length()>400) throw new IllegalArgumentException("Add evidence or a note of 1-400 characters.");
        register(game,map); var old=get(game,map); var marks=new EnumMap<Check,Mark>(Check.class); marks.putAll(old.marks());
        marks.put(check,new Mark(outcome,old.revision(),fingerprint,actor,Instant.now().toString(),note.replaceAll("[\\r\\n\\t]"," ")));
        replace(key(game,map),new Entry(game,map,old.revision(),marks));
    }
    private void replace(String key,Entry entry) throws IOException {
        Entry before=entries.put(key,entry);
        try { save(); } catch(IOException failure) { if(before==null) entries.remove(key);else entries.put(key,before); throw failure; }
    }
    private void save() throws IOException {
        var data=new YamlConfiguration();
        for(var entry:entries.values()) {
            String path="maps."+entry.game()+"."+entry.map(); data.set(path+".revision",entry.revision());
            entry.marks().forEach((check,mark)-> {
                String base=path+".checks."+check.name();
                data.set(base+".outcome",mark.outcome().name());data.set(base+".revision",mark.revision());
                data.set(base+".configuration",mark.configuration());data.set(base+".actor",mark.actor());
                data.set(base+".at",mark.at());data.set(base+".note",mark.note());
            });
        }
        Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"map-review-",".tmp");
        try { Files.writeString(temp,data.saveToString());
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException unsupported) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
