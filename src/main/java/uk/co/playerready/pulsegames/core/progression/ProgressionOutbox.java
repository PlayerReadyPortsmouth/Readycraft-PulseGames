package uk.co.playerready.pulsegames.core.progression;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Durable delivery receipts. SQL is authoritative; a lost acknowledgement safely replays. */
public final class ProgressionOutbox {
    public record Result(UUID player,UUID match,String game,boolean won,int kills,String event) {
        public Result {
            Objects.requireNonNull(player);Objects.requireNonNull(match);
            if(game==null || !game.matches("[a-z0-9_-]{1,48}") || kills<0 || kills>10000
                    || event==null || !event.matches("[a-z0-9_-]{0,48}")) throw new IllegalArgumentException("Invalid progression result");
        }
        public String key(){return match+"-"+player;}
    }
    private final Path directory;
    private final Map<String,Result> pending=new LinkedHashMap<>();
    public ProgressionOutbox(Path directory)throws IOException {
        this.directory=directory;Files.createDirectories(directory);
        try(var paths=Files.list(directory)) {
            for(Path file:paths.filter(p->p.getFileName().toString().endsWith(".yml")).limit(10001).toList()) {
                if(pending.size()>=10000 || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>4096) throw new IOException("Invalid or full progression outbox");
                var y=new YamlConfiguration();
                try { y.load(file.toFile());
                    if(!y.isBoolean("won") || !y.isInt("kills"))throw new IllegalArgumentException("Invalid receipt fields");
                    var row=new Result(UUID.fromString(y.getString("player","")),UUID.fromString(y.getString("match","")),y.getString("game"),y.getBoolean("won"),y.getInt("kills"),y.getString("event",""));
                    if(!file.getFileName().toString().equals(row.key()+".yml")) throw new IllegalArgumentException("Receipt filename mismatch");
                    pending.put(row.key(),row);
                }catch(Exception invalid){throw new IOException("Invalid progression receipt; preserve and repair outbox",invalid);}
            }
        }
    }
    public synchronized void add(Result row)throws IOException {
        var previous=pending.get(row.key());
        if(previous!=null){if(!previous.equals(row))throw new IOException("Conflicting local match receipt");return;}
        if(pending.size()>=10000)throw new IOException("Progression outbox full");
        var y=new YamlConfiguration();y.set("player",row.player().toString());y.set("match",row.match().toString());y.set("game",row.game());y.set("won",row.won());y.set("kills",row.kills());y.set("event",row.event());
        Path temp=Files.createTempFile(directory,"receipt-",".tmp");
        try { Files.writeString(temp,y.saveToString());
            try { Files.move(temp,directory.resolve(row.key()+".yml"),StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,directory.resolve(row.key()+".yml"));}
            pending.put(row.key(),row);
        } finally {Files.deleteIfExists(temp);}
    }
    public synchronized List<Result> pending(){return List.copyOf(pending.values());}
    public synchronized void acknowledge(Result row)throws IOException {
        if(!row.equals(pending.get(row.key())))return;
        Files.deleteIfExists(directory.resolve(row.key()+".yml"));pending.remove(row.key());
    }
}
