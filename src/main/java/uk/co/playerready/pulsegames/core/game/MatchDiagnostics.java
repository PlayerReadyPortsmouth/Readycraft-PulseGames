package uk.co.playerready.pulsegames.core.game;

import java.nio.file.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Bounded match reports without player names, chat, inventories or coordinates. */
public final class MatchDiagnostics implements AutoCloseable {
    private volatile boolean closed;
    private final Path directory;
    private final Logger logger;
    private final Map<String,Deque<String>> reports=new LinkedHashMap<>();
    private final java.util.concurrent.ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"PulseGames-reports");t.setDaemon(true);return t;});
    private final Map<String,String> pending=new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
    public MatchDiagnostics(Path directory,Logger logger){this.directory=directory;this.logger=logger;}
    public synchronized void record(String id,String game,String mode,String map,String event,String detail) {
        if(closed)return;
        if(id==null || !id.matches("[a-zA-Z0-9_-]{1,80}"))return;
        Deque<String> lines=reports.computeIfAbsent(id,k->new ArrayDeque<>());
        lines.addLast(Instant.now()+" | "+clean(game)+"/"+clean(mode)+" | "+clean(map)+" | "+clean(event)+" | "+clean(detail));
        while(lines.size()>128)lines.removeFirst();
        while(reports.size()>100)reports.remove(reports.keySet().iterator().next());
        if (pending.size() >= 100 && !pending.containsKey(id)) pending.remove(pending.keySet().iterator().next());
        pending.put(id,String.join("\n",lines)+"\n");
        if(queued.compareAndSet(false,true))writer.execute(this::drain);
    }
    private void drain(){
        try {
            Files.createDirectories(directory);
            do {
                for(String id:List.copyOf(pending.keySet())){
                    String data=pending.remove(id);if(data==null)continue;
                    Path tmp=Files.createTempFile(directory,"report-",".tmp");
                    try {Files.writeString(tmp,data);Files.move(tmp,directory.resolve(id+".log"),StandardCopyOption.REPLACE_EXISTING);}
                    finally {Files.deleteIfExists(tmp);}
                }
            }while(!pending.isEmpty());
            try(var files=Files.list(directory)){
                var old=files.filter(p->p.getFileName().toString().endsWith(".log"))
                        .sorted(Comparator.comparingLong(MatchDiagnostics::modified).reversed()).skip(100).toList();
                for(Path file:old)Files.deleteIfExists(file);
            }
        }catch(IOException ex){pending.clear();logger.warning("Could not write match diagnostics: "+ex.getClass().getSimpleName());}
        finally {synchronized(this){queued.set(false);if(!pending.isEmpty()&&!closed&&queued.compareAndSet(false,true))writer.execute(this::drain);}}
    }
    private static long modified(Path p){try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException ex){return 0;}}
    static String clean(String text){return String.valueOf(text).replaceAll("[\\r\\n\\t]"," ").replaceAll("[^a-zA-Z0-9 _.,:/=()\\-]","?").substring(0,Math.min(String.valueOf(text).length(),160));}
    public synchronized List<String> recent(){return reports.keySet().stream().toList().reversed().stream().limit(10).toList();}
    public synchronized List<String> report(String id){var rows=reports.get(id);return rows==null?List.of():rows.stream().skip(Math.max(0,rows.size()-20)).toList();}
    @Override public void close(){synchronized(this){if(closed)return;closed=true;if(!pending.isEmpty())writer.execute(this::drain);writer.shutdown();}try{if(!writer.awaitTermination(5,TimeUnit.SECONDS))logger.warning("Match report writer still finishing.");}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
}
